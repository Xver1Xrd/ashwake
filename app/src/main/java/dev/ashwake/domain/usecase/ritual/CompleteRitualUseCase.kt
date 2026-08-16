package dev.ashwake.domain.usecase.ritual

import dev.ashwake.core.time.AppClock
import dev.ashwake.domain.engine.character.StatSource
import dev.ashwake.domain.engine.reward.RewardContext
import dev.ashwake.domain.engine.reward.RewardSource
import dev.ashwake.domain.model.ritual.ReviewCompletion
import dev.ashwake.domain.repository.character.CharacterRepository
import dev.ashwake.domain.repository.ritual.RitualRepository
import dev.ashwake.domain.repository.timebox.TimeboxRepository
import dev.ashwake.domain.usecase.character.RefreshAchievementsUseCase
import java.time.LocalDate
import javax.inject.Inject

/**
 * Прохождение вечернего ритуала: сохранение отчёта и награда.
 *
 * Награда выдаётся только за первое прохождение дня — переоткрыть и «пройти»
 * ритуал второй раз не должно приносить монет. Проверка живёт здесь, потому
 * что доснять ритуал можно и за прошлый день, и за сегодня.
 */
class CompleteRitualUseCase @Inject constructor(
    private val ritual: RitualRepository,
    private val character: CharacterRepository,
    private val timebox: TimeboxRepository,
    private val achievements: RefreshAchievementsUseCase,
    private val clock: AppClock
) {
    suspend operator fun invoke(
        date: LocalDate,
        dayRating: Int?,
        mood: Int?,
        energy: Int?,
        note: String?,
        topTaskIds: List<Long>,
        alreadyReviewed: Boolean,
        planTomorrow: Boolean
    ) {
        ritual.saveReview(
            date = date,
            dayRating = dayRating,
            mood = mood,
            energy = energy,
            note = note,
            topTaskIds = topTaskIds,
            completedAs = if (date < clock.today()) ReviewCompletion.NEXT_MORNING
            else ReviewCompletion.EVENING
        )

        if (!alreadyReviewed) {
            character.grantReward(
                RewardContext(
                    source = RewardSource.RITUAL_DONE,
                    time = clock.now().atZone(clock.zone()).toLocalTime()
                ),
                refId = date.toString()
            )
            character.grantStatPoints(StatSource.RITUAL_DONE, refId = date.toString())
        }

        if (planTomorrow) timebox.planDay(date.plusDays(1))
        achievements()
    }
}
