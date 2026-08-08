package dev.ashwake.data.repository.blocking

import androidx.room.withTransaction
import dev.ashwake.core.time.AppClock
import dev.ashwake.data.db.AshwakeDatabase
import dev.ashwake.data.db.dao.blocking.BlockingDao
import dev.ashwake.data.db.entity.blocking.BlockedAppEntity
import dev.ashwake.data.db.entity.blocking.BlockingRuleEntity
import dev.ashwake.data.db.entity.blocking.BypassLogEntity
import dev.ashwake.data.db.mapper.tasks.toLocalTime
import dev.ashwake.data.db.mapper.tasks.toMinuteOfDay
import dev.ashwake.domain.engine.blocking.BlockingContext
import dev.ashwake.domain.engine.blocking.BlockingEvaluator
import dev.ashwake.domain.model.blocking.BlockingRule
import dev.ashwake.domain.model.blocking.BlockingVerdict
import dev.ashwake.domain.model.blocking.BypassRecord
import dev.ashwake.domain.model.blocking.UnlockCondition
import dev.ashwake.domain.repository.blocking.BlockingRepository
import dev.ashwake.domain.repository.habits.HabitRepository
import dev.ashwake.domain.repository.routines.RoutineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** До какого часа привычка считается «утренней». */
private const val MORNING_UNTIL_HOUR = 12

@Singleton
class BlockingRepositoryImpl @Inject constructor(
    private val db: AshwakeDatabase,
    private val dao: BlockingDao,
    private val habits: HabitRepository,
    private val routines: RoutineRepository,
    private val evaluator: BlockingEvaluator,
    private val clock: AppClock
) : BlockingRepository {

    /** Обход держится в памяти: он короткий и переживать перезапуск не должен. */
    @Volatile
    private var bypassUntil: Instant? = null

    override fun observeRules(): Flow<List<BlockingRule>> =
        combine(dao.observeRules(), dao.observeApps()) { rules, apps ->
            val byRule = apps.groupBy { it.ruleId }
            rules.map { rule ->
                rule.toDomain(byRule[rule.id].orEmpty().map { it.packageName })
            }
        }

    override suspend fun upsertRule(rule: BlockingRule): Long = db.withTransaction {
        val prepared =
            if (rule.createdAt.toEpochMilli() == 0L) rule.copy(createdAt = clock.now()) else rule
        val id = dao.upsertRule(prepared.toEntity()).let { if (it == -1L) rule.id else it }

        dao.clearApps(id)
        dao.insertApps(rule.packages.distinct().map { BlockedAppEntity(id, it) })
        id
    }

    override suspend fun deleteRule(id: Long) = dao.deleteRule(id)

    override suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)

    override suspend fun hasEnabledRules(): Boolean = dao.enabledCount() > 0

    /**
     * Контекст на сейчас.
     *
     * Утренними считаются привычки с напоминанием до полудня либо ежедневные
     * без времени: точного признака «утренняя» в модели нет, и вводить его
     * ради одной фичи значило бы усложнить редактор привычки всем остальным.
     */
    override suspend fun currentContext(): BlockingContext {
        val today = clock.today()
        val zoned = clock.now().atZone(clock.zone() ?: ZoneId.systemDefault())

        val unfinished = habits.observeHabitsWithProgress(today).first()
            .filter { it.dueToday && !it.paused && it.todayEntry == null }
            .filter { progress ->
                val reminder = progress.habit.reminderTime
                reminder == null || reminder.hour < MORNING_UNTIL_HOUR
            }
            .map { it.habit.name }

        val routineList = routines.observeRoutines().first()
        val completedToday = routineList.mapNotNull { routine ->
            val sessions = routines.observeSessions(routine.id).first()
            routine.id.takeIf { _ ->
                sessions.any { session ->
                    session.completed &&
                        session.startedAt.atZone(clock.zone() ?: ZoneId.systemDefault())
                            .toLocalDate() == today
                }
            }
        }.toSet()

        return BlockingContext(
            nowMinute = zoned.hour * 60 + zoned.minute,
            now = clock.now(),
            unfinishedMorningHabits = unfinished,
            completedRoutineIds = completedToday,
            completedRoutineNames = routineList.associate { it.id to it.name },
            bypassUntil = bypassUntil
        )
    }

    override suspend fun verdictFor(packageName: String): BlockingVerdict {
        val rules = dao.enabledRules().map { rule ->
            rule.toDomain(dao.appsOf(rule.id).map { it.packageName })
        }
        if (rules.isEmpty()) return BlockingVerdict(blocked = false)
        return evaluator.evaluate(packageName, rules, currentContext())
    }

    override suspend fun registerBypass(packageName: String, ruleId: Long?, delaySeconds: Int) {
        bypassUntil = evaluator.bypassUntil(clock.now())
        dao.logBypass(
            BypassLogEntity(
                at = clock.now().toEpochMilli(),
                packageName = packageName,
                ruleId = ruleId,
                delaySeconds = delaySeconds
            )
        )
    }

    override fun observeBypasses(): Flow<List<BypassRecord>> =
        dao.observeBypasses().map { list ->
            list.map {
                BypassRecord(
                    id = it.id,
                    at = Instant.ofEpochMilli(it.at),
                    packageName = it.packageName,
                    ruleId = it.ruleId,
                    delaySeconds = it.delaySeconds
                )
            }
        }

    private fun BlockingRuleEntity.toDomain(packages: List<String>) = BlockingRule(
        id = id,
        name = name,
        enabled = enabled,
        condition = UnlockCondition.valueOf(conditionType),
        routineId = refRoutineId,
        unlockTime = timeMinute?.toLocalTime(),
        packages = packages,
        createdAt = Instant.ofEpochMilli(createdAt)
    )

    private fun BlockingRule.toEntity() = BlockingRuleEntity(
        id = id,
        name = name,
        enabled = enabled,
        conditionType = condition.name,
        refRoutineId = routineId,
        timeMinute = unlockTime?.toMinuteOfDay(),
        createdAt = createdAt.toEpochMilli()
    )
}
