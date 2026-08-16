package dev.ashwake.domain.usecase.focus

import dev.ashwake.core.time.AppClock
import dev.ashwake.domain.engine.character.StatSource
import dev.ashwake.domain.engine.reward.RewardContext
import dev.ashwake.domain.engine.reward.RewardSource
import dev.ashwake.domain.model.focus.FocusPhase
import dev.ashwake.domain.repository.character.CharacterRepository
import dev.ashwake.domain.repository.routines.FocusRepository
import dev.ashwake.domain.usecase.character.RefreshAchievementsUseCase
import javax.inject.Inject

/**
 * Завершение фазы фокуса: фиксация факта и награда за рабочую фазу.
 *
 * Награда — только за рабочую фазу и только если она реально шла:
 * пять минут и меньше — это не сессия фокуса, а случайное нажатие.
 */
class FinishFocusSessionUseCase @Inject constructor(
    private val focus: FocusRepository,
    private val character: CharacterRepository,
    private val achievements: RefreshAchievementsUseCase,
    private val clock: AppClock
) {
    suspend operator fun invoke(
        sessionId: Long,
        phase: FocusPhase,
        actualSeconds: Int,
        completed: Boolean
    ) {
        focus.finish(sessionId, actualSeconds, completed)
        if (phase != FocusPhase.WORK) return
        if (!completed && actualSeconds < MIN_REWARDABLE_SECONDS) return

        character.grantReward(
            RewardContext(
                source = RewardSource.FOCUS_DONE,
                time = clock.now().atZone(clock.zone()).toLocalTime()
            )
        )
        character.grantStatPoints(StatSource.FOCUS_SESSION)
        achievements()
    }

    private companion object {
        /** Пять минут: короче — это не сессия фокуса, а случайное нажатие. */
        const val MIN_REWARDABLE_SECONDS = 300
    }
}
