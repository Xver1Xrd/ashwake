package dev.ashwake.domain.usecase.habits

import dev.ashwake.core.time.AppClock
import dev.ashwake.domain.engine.character.StatSource
import dev.ashwake.domain.engine.reward.RewardContext
import dev.ashwake.domain.engine.reward.RewardSource
import dev.ashwake.domain.model.habits.EntrySource
import dev.ashwake.domain.model.habits.EntryStatus
import dev.ashwake.domain.model.habits.HabitWithProgress
import dev.ashwake.domain.repository.character.CharacterRepository
import dev.ashwake.domain.repository.character.RewardScope
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
    private val fireAnchors: FireAnchorsUseCase,
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

        // Привычки, привязанные к этой, ждут именно отметки — и ждут её
        // независимо от того, откуда она пришла: из списка, из шторки
        // или из виджета. Поэтому будим их здесь, а не в UI
        fireAnchors.onHabitDone(progress.habit.id)

        val habit = progress.habit
        val refId = habitRewardRef(habit.id, date)
        character.grantReward(
            RewardContext(
                source = if (status == EntryStatus.DONE) RewardSource.HABIT_DONE
                else RewardSource.HABIT_MINIMUM,
                sphere = habit.sphere,
                habitScore = progress.score,
                time = clock.now().atZone(clock.zone()).toLocalTime()
            ),
            refId = refId
        )
        character.grantStatPoints(
            source = if (status == EntryStatus.DONE) StatSource.HABIT_DONE
            else StatSource.HABIT_MINIMUM,
            sphere = habit.sphere,
            refId = refId
        )
        // Длинная серия качает выносливость отдельно от самой привычки
        if (progress.currentStreak > 0) {
            character.grantStatPoints(StatSource.STREAK_DAY, refId = refId)
        }
    }
}

/**
 * Идентификатор начисления за отметку привычки.
 *
 * В нём обязана быть дата: отметка снимается за конкретный день, и отмена
 * по одному лишь номеру привычки сняла бы заодно всё, что начислено за
 * прошлые дни.
 */
fun habitRewardRef(habitId: Long, date: LocalDate): String = "$habitId@$date"

/**
 * Снятие отметки за день.
 *
 * Отметить и снять — обратимая пара, поэтому снятие обязано забирать
 * начисленное. Иначе привычка становится тем же бесконечным источником
 * монет, что и задача: нажал, снял, нажал.
 */
class ClearHabitMarkUseCase @Inject constructor(
    private val habits: HabitRepository,
    private val character: CharacterRepository,
    private val clock: AppClock
) {
    suspend operator fun invoke(habitId: Long, date: LocalDate = clock.today()) {
        habits.clearMark(habitId, date)
        character.revokeReward(RewardScope.HABIT, habitRewardRef(habitId, date))
    }
}
