package dev.ashwake.domain.usecase.character

import dev.ashwake.core.time.AppClock
import dev.ashwake.data.assets.AchievementLoader
import dev.ashwake.domain.engine.achievement.AchievementDefinition
import dev.ashwake.domain.engine.achievement.AchievementEngine
import dev.ashwake.domain.engine.reward.RewardContext
import dev.ashwake.domain.engine.reward.RewardSource
import dev.ashwake.domain.repository.character.CharacterRepository
import javax.inject.Inject

/** Открытое сейчас достижение — то, что показывается всплывашкой. */
data class UnlockedAchievement(
    val definition: AchievementDefinition,
    val at: Long
)

/**
 * Проверка условий достижений и раздача наград.
 *
 * Вызывается после каждого начисления (задача, привычка, рутина, фокус,
 * тяга, ритуал, сундук) — поэтому достижения открываются сразу, а не
 * при следующем заходе на экран. Раздача идемпотентна: уже открытые
 * не открываются повторно.
 */
class RefreshAchievementsUseCase @Inject constructor(
    private val character: CharacterRepository,
    private val loader: AchievementLoader,
    private val engine: AchievementEngine,
    private val clock: AppClock
) {
    suspend operator fun invoke(): List<UnlockedAchievement> {
        val definitions = loader.load()
        if (definitions.isEmpty()) return emptyList()

        val unlockedIds = character.state().achievements
            .filter { it.unlockedAt != null }
            .map { it.id }
            .toSet()

        val snapshot = character.achievementSnapshot()
        val now = clock.now().toEpochMilli()

        return engine.newlyUnlocked(definitions, snapshot, unlockedIds).mapNotNull { def ->
            if (!character.unlockAchievement(def.id, now)) return@mapNotNull null
            grantRewards(def)
            UnlockedAchievement(def, now)
        }
    }

    private suspend fun grantRewards(def: AchievementDefinition) {
        if (def.rewardCoins > 0 || def.rewardXp > 0) {
            character.grantReward(
                RewardContext(
                    source = RewardSource.ACHIEVEMENT,
                    time = clock.now().atZone(clock.zone()).toLocalTime(),
                    flatCoins = def.rewardCoins,
                    flatXp = def.rewardXp
                ),
                refId = def.id
            )
        }
        def.rewardMaterial?.let { material ->
            character.grantMaterial(material, def.rewardMaterialAmount)
        }
    }
}
