package dev.ashwake.domain.usecase.habits

import dev.ashwake.core.time.AppClock
import dev.ashwake.domain.engine.character.StatSource
import dev.ashwake.domain.engine.reward.RewardContext
import dev.ashwake.domain.engine.reward.RewardSource
import dev.ashwake.domain.model.habits.EntrySource
import dev.ashwake.domain.model.habits.EntryStatus
import dev.ashwake.domain.model.habits.HabitWithProgress
import dev.ashwake.domain.repository.character.CharacterRepository
import dev.ashwake.domain.repository.habits.HabitRepository
import java.time.LocalDate
import javax.inject.Inject

/**
 * Отметка привычки вместе с начислением.
 *
 * Награда выдаётся только при переходе в выполненное состояние: повторное
 * нажатие по уже отмеченной привычке не должно быть источником монет.
 * Проверка живёт здесь, потому что отметить привычку можно из списка,
 * из уведомления и из вечернего ритуала — трёх разных мест.
 */
class MarkHabitUseCase @Inject constructor(
    private val habits: HabitRepository,
    private val character: CharacterRepository,
    private val clock: AppClock
) {
    suspend operator fun invoke(
        progress: HabitWithProgress,
        status: EntryStatus,
        date: LocalDate = clock.today(),
        value: Float? = null,
        note: String? = null,
        skipReasonId: Long? = null,
        source: EntrySource = EntrySource.MANUAL
    ) {
        val wasCounted = progress.todayEntry?.countsForStreak == true
        habits.mark(progress.habit.id, date, status, value, note, skipReasonId, source)

        val nowCounted = status == EntryStatus.DONE || status == EntryStatus.MINIMUM
        if (!nowCounted || wasCounted) return

        val habit = progress.habit
        character.grantReward(
            RewardContext(
                source = if (status == EntryStatus.DONE) RewardSource.HABIT_DONE
                else RewardSource.HABIT_MINIMUM,
                sphere = habit.sphere,
                habitScore = progress.score,
                time = clock.now().atZone(clock.zone()).toLocalTime()
            ),
            refId = habit.id.toString()
        )
        character.grantStatPoints(
            source = if (status == EntryStatus.DONE) StatSource.HABIT_DONE
            else StatSource.HABIT_MINIMUM,
            sphere = habit.sphere,
            refId = habit.id.toString()
        )
        // Длинная серия качает выносливость отдельно от самой привычки
        if (progress.currentStreak > 0) {
            character.grantStatPoints(StatSource.STREAK_DAY, refId = habit.id.toString())
        }
    }
}
