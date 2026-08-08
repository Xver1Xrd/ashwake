package dev.ashwake.domain.repository.ritual

import dev.ashwake.domain.engine.analytics.CorrelationReport
import dev.ashwake.domain.engine.analytics.WeeklyReport
import dev.ashwake.domain.engine.analytics.YearSummary
import dev.ashwake.domain.model.habits.HabitWithProgress
import dev.ashwake.domain.model.ritual.DailyReview
import dev.ashwake.domain.model.ritual.ReviewCompletion
import dev.ashwake.domain.model.tasks.Task
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Всё, что нужно экрану ритуала за один заход (п. 9). */
data class RitualState(
    val date: LocalDate,
    val review: DailyReview?,
    /** Незакрытые задачи дня — их разбирают свайпом. */
    val openTasks: List<Task> = emptyList(),
    /** Привычки без отметки за день. */
    val unmarkedHabits: List<HabitWithProgress> = emptyList(),
    val tomorrowCandidates: List<Task> = emptyList(),
    /** Ритуал за вчера ещё можно пройти утром. */
    val isCatchUp: Boolean = false
) {
    val alreadyDone: Boolean get() = review != null
}

interface RitualRepository {

    fun observeRitual(date: LocalDate): Flow<RitualState>

    /**
     * Дата, за которую сейчас проходится ритуал.
     * Утром это может быть вчера: пропущенный ритуал не теряется (п. 9).
     */
    suspend fun currentRitualDate(): LocalDate

    suspend fun saveReview(
        date: LocalDate,
        dayRating: Int?,
        mood: Int?,
        energy: Int?,
        note: String?,
        topTaskIds: List<Long>,
        completedAs: ReviewCompletion
    )

    fun observeReviews(from: LocalDate, to: LocalDate): Flow<List<DailyReview>>

    // --- аналитика ---------------------------------------------------------

    suspend fun correlations(windowDays: Long = 90): CorrelationReport

    suspend fun weeklyReport(weekStart: LocalDate): WeeklyReport

    suspend fun yearSummary(year: Int): YearSummary
}
