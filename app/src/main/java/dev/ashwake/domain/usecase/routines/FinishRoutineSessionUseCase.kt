package dev.ashwake.domain.usecase.routines

import dev.ashwake.core.time.AppClock
import dev.ashwake.domain.engine.character.StatSource
import dev.ashwake.domain.engine.reward.RewardContext
import dev.ashwake.domain.engine.reward.RewardSource
import dev.ashwake.domain.repository.character.CharacterRepository
import dev.ashwake.domain.repository.routines.RoutineRepository
import dev.ashwake.domain.usecase.character.RefreshAchievementsUseCase
import javax.inject.Inject

/**
 * Завершение сессии рутины: факт в базу и награда за доведённую до конца.
 *
 * Отдельный use case, потому что закончить можно кнопкой, по последнему
 * шагу и (в будущем) из уведомления — везде должна сработать ровно одна
 * награда с одинаковыми условиями.
 */
class FinishRoutineSessionUseCase @Inject constructor(
    private val routines: RoutineRepository,
    private val character: CharacterRepository,
    private val achievements: RefreshAchievementsUseCase,
    private val clock: AppClock
) {
    suspend operator fun invoke(
        sessionId: Long,
        completed: Boolean,
        routineId: Long? = null
    ) {
        routines.finishSession(sessionId, completed, clock.now())
        if (!completed) return

        val session = routines.getSession(sessionId)
        character.grantReward(
            RewardContext(
                source = RewardSource.ROUTINE_DONE,
                flawless = session?.flawless == true,
                time = clock.now().atZone(clock.zone()).toLocalTime()
            ),
            refId = routineId?.toString()
        )
        character.grantStatPoints(StatSource.ROUTINE_DONE, refId = routineId?.toString())
        achievements()
    }
}
