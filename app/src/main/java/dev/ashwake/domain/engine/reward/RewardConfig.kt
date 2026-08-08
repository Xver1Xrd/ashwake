package dev.ashwake.domain.engine.reward

import dev.ashwake.core.model.Priority

/**
 * Все коэффициенты баланса в одном месте (п. 14).
 *
 * Именно ради этого файла существует [RewardEngine]: баланс должен править
 * человек, а не программист — правка чисел здесь не требует трогать логику.
 */
data class RewardConfig(
    // задачи
    val taskCoinsByPriority: Map<Priority, Int> = mapOf(
        Priority.P1 to 20,
        Priority.P2 to 12,
        Priority.P3 to 7,
        Priority.P4 to 4
    ),
    val taskXp: Int = 5,
    /** Бонус за закрытие задачи, которую переносили три и более раз. */
    val staleTaskBonus: Int = 15,

    // привычки
    val habitCoins: Int = 10,
    val habitMinimumCoins: Int = 5,
    val habitXp: Int = 4,
    /** Множитель от score: привычка со score 0.8 даёт больше, чем едва начатая. */
    val habitScoreMultiplierRange: ClosedFloatingPointRange<Float> = 0.5f..1.5f,

    // рутины и фокус
    val routineCoins: Int = 15,
    val routineFlawlessBonus: Int = 10,
    val routineXp: Int = 8,
    val focusCoins: Int = 12,
    val focusXp: Int = 6,

    // отказы
    val abstinenceDayCoins: Int = 8,
    val abstinenceMilestoneCoins: Int = 100,
    val cravingResistedCoins: Int = 10,
    val abstinenceXp: Int = 5,

    // ритуал
    val ritualCoins: Int = 20,
    val ritualXp: Int = 10,

    // уровни
    val baseXpPerLevel: Int = 100,
    val xpGrowthPerLevel: Float = 1.25f
) {
    companion object {
        val DEFAULT = RewardConfig()
    }
}
