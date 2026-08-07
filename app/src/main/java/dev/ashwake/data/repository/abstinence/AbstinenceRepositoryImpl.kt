package dev.ashwake.data.repository.abstinence

import androidx.room.withTransaction
import dev.ashwake.core.time.AppClock
import dev.ashwake.data.db.AshwakeDatabase
import dev.ashwake.data.db.dao.abstinence.AbstinenceDao
import dev.ashwake.data.db.entity.abstinence.AbstinenceSubstituteEntity
import dev.ashwake.data.db.entity.abstinence.CravingTriggerEntity
import dev.ashwake.data.db.entity.abstinence.RelapseReasonEntity
import dev.ashwake.data.db.mapper.abstinence.toDomain
import dev.ashwake.data.db.mapper.abstinence.toEntity
import dev.ashwake.domain.engine.abstinence.AbstinenceCalculator
import dev.ashwake.domain.engine.abstinence.CravingAnalyzer
import dev.ashwake.domain.model.abstinence.Abstinence
import dev.ashwake.domain.model.abstinence.AbstinenceMode
import dev.ashwake.domain.model.abstinence.Attempt
import dev.ashwake.domain.model.abstinence.CravingEvent
import dev.ashwake.domain.model.abstinence.Milestone
import dev.ashwake.domain.repository.abstinence.AbstinenceDetail
import dev.ashwake.domain.repository.abstinence.AbstinenceRepository
import dev.ashwake.domain.repository.abstinence.AbstinenceWithStats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Окно, в течение которого срыв можно отменить (п. 4). */
private val UNDO_WINDOW: Duration = Duration.ofHours(24)

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class AbstinenceRepositoryImpl @Inject constructor(
    private val db: AshwakeDatabase,
    private val dao: AbstinenceDao,
    private val clock: AppClock,
    private val calculator: AbstinenceCalculator,
    private val analyzer: CravingAnalyzer
) : AbstinenceRepository {

    override fun observeAll(now: Instant): Flow<List<AbstinenceWithStats>> = combine(
        dao.observeAll(0),
        dao.observeAttempts(),
        dao.observeMilestones(),
        dao.observeSubstitutes()
    ) { items, attempts, milestones, substitutes ->
        val attemptsById = attempts.map { it.toDomain() }.groupBy { it.abstinenceId }
        val milestonesById = milestones.map { it.toDomain() }.groupBy { it.abstinenceId }
        val substitutesById = substitutes.map { it.toDomain() }.groupBy { it.abstinenceId }

        items.map { entity ->
            val abstinence = entity.toDomain(
                attempts = attemptsById[entity.id].orEmpty(),
                milestones = milestonesById[entity.id].orEmpty(),
                substitutes = substitutesById[entity.id].orEmpty()
            )
            AbstinenceWithStats(abstinence, calculator.stats(abstinence, now))
        }
    }

    override fun observeDetail(id: Long, now: Instant): Flow<AbstinenceDetail?> =
        dao.observeOne(id).flatMapLatest { entity ->
            if (entity == null) return@flatMapLatest flowOf(null)
            combine(
                dao.observeAttemptsOf(id),
                dao.observeMilestones(),
                dao.observeSubstitutes(),
                dao.observeCravings(id),
                combine(dao.observeTriggers(), dao.observeRelapseReasons()) { t, r -> t to r }
            ) { attempts, milestones, substitutes, cravings, dictionaries ->
                val (triggerEntities, reasonEntities) = dictionaries
                val abstinence = entity.toDomain(
                    attempts = attempts.map { it.toDomain() },
                    milestones = milestones.map { it.toDomain() }.filter { it.abstinenceId == id },
                    substitutes = substitutes.map { it.toDomain() }.filter { it.abstinenceId == id }
                )
                val triggers = triggerEntities.map { it.toDomain() }
                val cravingEvents = cravings.map { it.toDomain() }

                AbstinenceDetail(
                    abstinence = abstinence,
                    stats = calculator.stats(abstinence, now),
                    cravings = cravingEvents,
                    analytics = analyzer.analyze(cravingEvents, triggers),
                    triggers = triggers,
                    relapseReasons = reasonEntities.map { it.toDomain() },
                    undoAvailableUntil = abstinence.attempts
                        .maxByOrNull { it.startedAt }
                        ?.undoDeadline
                        ?.takeIf { it.isAfter(now) }
                )
            }
        }

    override suspend fun get(id: Long): Abstinence? = db.withTransaction {
        val entity = dao.get(id) ?: return@withTransaction null
        entity.toDomain(
            attempts = dao.attemptsOf(id).map { it.toDomain() },
            milestones = dao.milestonesOf(id).map { it.toDomain() }
        )
    }

    override suspend fun create(abstinence: Abstinence): Long = db.withTransaction {
        val now = clock.now()
        val prepared = abstinence.copy(
            createdAt = if (abstinence.createdAt.toEpochMilli() == 0L) now else abstinence.createdAt,
            position = dao.maxPosition() + 1
        )
        val id = dao.upsert(prepared.toEntity())

        dao.insertAttempt(
            Attempt(
                abstinenceId = id,
                ordinal = 1,
                // Старт можно указать задним числом: «бросил три дня назад»
                startedAt = abstinence.attempts.firstOrNull()?.startedAt ?: now
            ).toEntity()
        )
        seedMilestones(id, abstinence.milestones)
        if (abstinence.substitutes.isNotEmpty()) {
            dao.insertSubstitutes(
                abstinence.substitutes.mapIndexed { index, substitute ->
                    AbstinenceSubstituteEntity(
                        abstinenceId = id, text = substitute.text, position = index
                    )
                }
            )
        }
        id
    }

    /** Стандартные вехи заводятся при создании: тексты к ним пишет пользователь. */
    private suspend fun seedMilestones(id: Long, custom: List<Milestone>) {
        val customDays = custom.map { it.days }.toSet()
        val defaults = calculator.defaultMilestoneDays()
            .filterNot { it in customDays }
            .map { days ->
                Milestone(abstinenceId = id, days = days, title = calculator.titleOf(days))
                    .toEntity()
            }
        dao.insertMilestones(defaults + custom.map { it.copy(abstinenceId = id).toEntity() })
    }

    override suspend fun update(abstinence: Abstinence) {
        dao.upsert(abstinence.toEntity())
    }

    override suspend fun archive(id: Long) = dao.archive(id)

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override suspend fun acknowledgeSubstanceWarning(id: Long) = dao.acknowledgeWarning(id)

    /**
     * Срыв.
     *
     * В GENTLE попытка остаётся открытой и получает штрафные дни; отменить можно
     * только последний срыв — предыдущие штрафы уже слились в общую сумму.
     * В STRICT попытка закрывается, следующая начинается сразу же, а вехи
     * сбрасываются: отсчёт до них идёт от начала текущей попытки.
     */
    override suspend fun registerRelapse(
        abstinenceId: Long,
        reasonId: Long?,
        note: String?,
        at: Instant
    ) {
        db.withTransaction {
            val entity = dao.get(abstinenceId) ?: return@withTransaction
            val current = dao.currentAttempt(abstinenceId) ?: return@withTransaction
            val undoDeadline = clock.now().plus(UNDO_WINDOW)

            if (AbstinenceMode.valueOf(entity.mode) == AbstinenceMode.GENTLE) {
                dao.updateAttempt(
                    current.copy(
                        penaltyDays = current.penaltyDays + entity.gentlePenaltyDays,
                        relapseReasonId = reasonId,
                        note = note,
                        undoDeadline = undoDeadline.toEpochMilli()
                    )
                )
            } else {
                dao.updateAttempt(
                    current.copy(
                        endedAt = at.toEpochMilli(),
                        relapseReasonId = reasonId,
                        note = note,
                        undoDeadline = undoDeadline.toEpochMilli()
                    )
                )
                dao.insertAttempt(
                    Attempt(
                        abstinenceId = abstinenceId,
                        ordinal = dao.maxOrdinal(abstinenceId) + 1,
                        startedAt = at,
                        undoDeadline = undoDeadline
                    ).toEntity()
                )
                dao.resetMilestones(abstinenceId)
            }
        }
    }

    override suspend fun undoRelapse(abstinenceId: Long): Boolean = db.withTransaction {
        val entity = dao.get(abstinenceId) ?: return@withTransaction false
        val last = dao.lastAttempt(abstinenceId) ?: return@withTransaction false
        val deadline = last.undoDeadline ?: return@withTransaction false
        if (clock.now().toEpochMilli() > deadline) return@withTransaction false

        if (AbstinenceMode.valueOf(entity.mode) == AbstinenceMode.GENTLE) {
            dao.updateAttempt(
                last.copy(
                    penaltyDays = (last.penaltyDays - entity.gentlePenaltyDays).coerceAtLeast(0),
                    relapseReasonId = null,
                    note = null,
                    undoDeadline = null
                )
            )
        } else {
            // Удаляем открытую заново попытку и воскрешаем предыдущую
            dao.deleteAttempt(last.id)
            val previous = dao.lastAttempt(abstinenceId) ?: return@withTransaction false
            dao.updateAttempt(
                previous.copy(
                    endedAt = null, relapseReasonId = null, note = null, undoDeadline = null
                )
            )
        }
        true
    }

    override suspend fun recordCraving(event: CravingEvent): Long =
        dao.insertCraving(event.toEntity())

    override suspend fun updateCravingOutcome(
        eventId: Long,
        resisted: Boolean,
        durationSeconds: Int?,
        note: String?
    ) {
        dao.updateCravingOutcome(eventId, resisted, durationSeconds, note)
    }

    override suspend fun setSubstitutes(abstinenceId: Long, texts: List<String>) {
        db.withTransaction {
            dao.clearSubstitutes(abstinenceId)
            dao.insertSubstitutes(
                texts.filter { it.isNotBlank() }.mapIndexed { index, text ->
                    AbstinenceSubstituteEntity(
                        abstinenceId = abstinenceId, text = text.trim(), position = index
                    )
                }
            )
        }
    }

    override suspend fun addCustomMilestone(
        abstinenceId: Long,
        days: Int,
        title: String,
        userText: String?
    ) {
        dao.upsertMilestone(
            Milestone(
                abstinenceId = abstinenceId,
                days = days,
                title = title,
                userText = userText,
                custom = true
            ).toEntity()
        )
    }

    override suspend fun deleteMilestone(milestoneId: Long) = dao.deleteMilestone(milestoneId)

    override suspend fun collectReachedMilestones(now: Instant): List<Pair<Abstinence, Milestone>> =
        db.withTransaction {
            val result = mutableListOf<Pair<Abstinence, Milestone>>()
            dao.allActive().forEach { entity ->
                val attempts = dao.attemptsOf(entity.id).map { it.toDomain() }
                val abstinence = entity.toDomain(
                    attempts = attempts,
                    milestones = dao.milestonesOf(entity.id).map { it.toDomain() }
                )
                calculator.newlyReached(abstinence, now).forEach { milestone ->
                    dao.markMilestoneReached(milestone.id, now.toEpochMilli())
                    result += abstinence to milestone
                }
            }
            result
        }

    override suspend fun ensureBuiltinData() {
        if (dao.relapseReasonCount() == 0) {
            dao.insertRelapseReasons(
                listOf(
                    RelapseReasonEntity(code = "STRESS", label = "Стресс"),
                    RelapseReasonEntity(code = "COMPANY", label = "Компания"),
                    RelapseReasonEntity(code = "INERTIA", label = "Привычка по инерции"),
                    RelapseReasonEntity(code = "CELEBRATION", label = "Праздник"),
                    RelapseReasonEntity(code = "BOREDOM", label = "Скука"),
                    RelapseReasonEntity(code = "COULD_NOT_REFUSE", label = "Не смог отказать")
                )
            )
        }
        if (dao.triggerCount() == 0) {
            dao.insertTriggers(
                listOf(
                    CravingTriggerEntity(code = "STRESS", label = "Стресс"),
                    CravingTriggerEntity(code = "BOREDOM", label = "Скука"),
                    CravingTriggerEntity(code = "COMPANY", label = "Компания"),
                    CravingTriggerEntity(code = "ALCOHOL", label = "Алкоголь рядом"),
                    CravingTriggerEntity(code = "AFTER_MEAL", label = "После еды"),
                    CravingTriggerEntity(code = "COFFEE", label = "Кофе"),
                    CravingTriggerEntity(code = "TIRED", label = "Усталость"),
                    CravingTriggerEntity(code = "CONFLICT", label = "Ссора")
                )
            )
        }
    }
}
