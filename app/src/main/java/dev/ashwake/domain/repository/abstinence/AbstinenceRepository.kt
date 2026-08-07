package dev.ashwake.domain.repository.abstinence

import dev.ashwake.domain.engine.abstinence.AbstinenceStats
import dev.ashwake.domain.engine.abstinence.CravingAnalytics
import dev.ashwake.domain.model.abstinence.Abstinence
import dev.ashwake.domain.model.abstinence.CravingEvent
import dev.ashwake.domain.model.abstinence.CravingTrigger
import dev.ashwake.domain.model.abstinence.Milestone
import dev.ashwake.domain.model.abstinence.RelapseReason
import kotlinx.coroutines.flow.Flow
import java.time.Instant

data class AbstinenceWithStats(
    val abstinence: Abstinence,
    val stats: AbstinenceStats
)

data class AbstinenceDetail(
    val abstinence: Abstinence,
    val stats: AbstinenceStats,
    val cravings: List<CravingEvent>,
    val analytics: CravingAnalytics,
    val triggers: List<CravingTrigger>,
    val relapseReasons: List<RelapseReason>,
    /** Можно ли ещё отменить последний срыв (24 часа, п. 4). */
    val undoAvailableUntil: Instant?
)

interface AbstinenceRepository {

    fun observeAll(now: Instant): Flow<List<AbstinenceWithStats>>

    fun observeDetail(id: Long, now: Instant): Flow<AbstinenceDetail?>

    suspend fun get(id: Long): Abstinence?

    /** Создаёт отказ, первую попытку и набор стандартных вех. */
    suspend fun create(abstinence: Abstinence): Long

    suspend fun update(abstinence: Abstinence)

    suspend fun archive(id: Long)

    suspend fun delete(id: Long)

    suspend fun acknowledgeSubstanceWarning(id: Long)

    /**
     * Срыв.
     *
     * STRICT закрывает попытку и открывает следующую. GENTLE оставляет попытку
     * открытой и добавляет ей штрафные дни. В обоих случаях история сохраняется.
     *
     * @param at время срыва — можно указать задним числом.
     */
    suspend fun registerRelapse(
        abstinenceId: Long,
        reasonId: Long?,
        note: String?,
        at: Instant
    )

    /** Отмена ошибочно нажатого срыва в течение суток. */
    suspend fun undoRelapse(abstinenceId: Long): Boolean

    suspend fun recordCraving(event: CravingEvent): Long

    suspend fun updateCravingOutcome(
        eventId: Long,
        resisted: Boolean,
        durationSeconds: Int?,
        note: String?
    )

    suspend fun setSubstitutes(abstinenceId: Long, texts: List<String>)

    suspend fun addCustomMilestone(abstinenceId: Long, days: Int, title: String, userText: String?)

    suspend fun deleteMilestone(milestoneId: Long)

    /** Отмечает достигнутые вехи. Возвращает те, о которых надо уведомить. */
    suspend fun collectReachedMilestones(now: Instant): List<Pair<Abstinence, Milestone>>

    suspend fun ensureBuiltinData()
}
