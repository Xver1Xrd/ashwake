package dev.ashwake.domain.usecase.abstinence

import dev.ashwake.core.time.AppClock
import dev.ashwake.domain.engine.character.StatSource
import dev.ashwake.domain.engine.reward.RewardContext
import dev.ashwake.domain.engine.reward.RewardSource
import dev.ashwake.domain.repository.abstinence.AbstinenceRepository
import dev.ashwake.domain.repository.character.CharacterRepository
import dev.ashwake.domain.usecase.character.RefreshAchievementsUseCase
import javax.inject.Inject

/**
 * Завершение эпизода тяги: запись исхода и награда за переждённую.
 *
 * Одна точка на награду — тягу можно закрыть из экрана отказа и из
 * уведомления, и задваивать монеты нельзя.
 */
class FinishCravingUseCase @Inject constructor(
    private val abstinences: AbstinenceRepository,
    private val character: CharacterRepository,
    private val achievements: RefreshAchievementsUseCase,
    private val clock: AppClock
) {
    suspend operator fun invoke(
        abstinenceId: Long,
        eventId: Long,
        resisted: Boolean,
        durationSeconds: Int?,
        note: String?
    ) {
        abstinences.updateCravingOutcome(eventId, resisted, durationSeconds, note)
        // Награда только за переждённую тягу: платить за срыв бессмысленно
        if (!resisted) return

        character.grantReward(
            RewardContext(
                source = RewardSource.CRAVING_RESISTED,
                time = clock.now().atZone(clock.zone()).toLocalTime()
            ),
            refId = abstinenceId.toString()
        )
        character.grantStatPoints(StatSource.CRAVING_RESISTED, refId = abstinenceId.toString())
        achievements()
    }
}
