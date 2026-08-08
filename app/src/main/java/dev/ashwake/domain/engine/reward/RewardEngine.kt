package dev.ashwake.domain.engine.reward

import dev.ashwake.core.model.Priority
import dev.ashwake.core.model.Sphere
import dev.ashwake.domain.engine.character.EffectKeys
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
import kotlin.math.roundToInt

/** Источник начисления. Пишется в журнал — по нему потом отлаживается баланс. */
enum class RewardSource {
    TASK_DONE, HABIT_DONE, HABIT_MINIMUM, ROUTINE_DONE, ABSTINENCE_DAY,
    MILESTONE, CRAVING_RESISTED, FOCUS_DONE, RITUAL_DONE,
    PURCHASE, UPGRADE, USER_REWARD, CHEST, ADJUST
}

/** Контекст события: из него движок берёт всё, что влияет на множители. */
data class RewardContext(
    val source: RewardSource,
    val priority: Priority? = null,
    val sphere: Sphere? = null,
    /** score привычки на момент отметки: 0..1. */
    val habitScore: Float? = null,
    val time: LocalTime? = null,
    val postponeCount: Int = 0,
    val flawless: Boolean = false,
    /** Итоговые эффекты экипировки из EquipmentEngine. */
    val equipmentEffects: Map<String, Float> = emptyMap()
)

data class Reward(
    val coins: Int,
    val xp: Int,
    /** Итоговый множитель монет — сохраняется в транзакции для отладки баланса. */
    val multiplier: Float
) {
    val isEmpty: Boolean get() = coins == 0 && xp == 0
}

/**
 * Единственная точка начисления и списания (п. 14).
 *
 * Ни один экран не считает монеты сам: иначе коэффициенты расползаются
 * по коду и баланс становится неправимым.
 *
 * Списывать движок умеет с самого начала — механики риска появятся позже,
 * и переписывать под них ничего не придётся.
 */
@Singleton
class RewardEngine @Inject constructor(
    // Значения по умолчанию у параметра быть не может: Kotlin сгенерировал бы
    // второй конструктор, а Hilt требует ровно один помеченный @Inject
    private val config: RewardConfig
) {

    fun reward(context: RewardContext): Reward {
        val base = baseCoins(context)
        val baseXp = baseXp(context)
        val multiplier = coinMultiplier(context)

        return Reward(
            coins = (base * multiplier).roundToInt(),
            xp = (baseXp * xpMultiplier(context)).roundToInt(),
            multiplier = multiplier
        )
    }

    /** Списание: покупка снаряжения, апгрейд, пользовательская награда. */
    fun charge(amount: Int, source: RewardSource): Reward =
        Reward(coins = -amount.coerceAtLeast(0), xp = 0, multiplier = 1f)

    private fun baseCoins(context: RewardContext): Int = when (context.source) {
        RewardSource.TASK_DONE -> {
            val byPriority = config.taskCoinsByPriority[context.priority ?: Priority.P4] ?: 0
            // Закрытие залежавшейся задачи ценнее: именно они больше всего мешают
            val stale = if (context.postponeCount >= STALE_THRESHOLD) config.staleTaskBonus else 0
            byPriority + stale
        }

        RewardSource.HABIT_DONE -> {
            val scoreMultiplier = habitScoreMultiplier(context.habitScore)
            (config.habitCoins * scoreMultiplier).roundToInt()
        }

        RewardSource.HABIT_MINIMUM -> config.habitMinimumCoins

        RewardSource.ROUTINE_DONE ->
            config.routineCoins + if (context.flawless) config.routineFlawlessBonus else 0

        RewardSource.ABSTINENCE_DAY -> config.abstinenceDayCoins
        RewardSource.MILESTONE -> config.abstinenceMilestoneCoins
        RewardSource.CRAVING_RESISTED -> config.cravingResistedCoins
        RewardSource.FOCUS_DONE -> config.focusCoins
        RewardSource.RITUAL_DONE -> config.ritualCoins

        RewardSource.PURCHASE, RewardSource.UPGRADE,
        RewardSource.USER_REWARD, RewardSource.CHEST, RewardSource.ADJUST -> 0
    }

    private fun baseXp(context: RewardContext): Int = when (context.source) {
        RewardSource.TASK_DONE -> config.taskXp
        RewardSource.HABIT_DONE, RewardSource.HABIT_MINIMUM -> config.habitXp
        RewardSource.ROUTINE_DONE -> config.routineXp
        RewardSource.FOCUS_DONE -> config.focusXp
        RewardSource.ABSTINENCE_DAY, RewardSource.MILESTONE,
        RewardSource.CRAVING_RESISTED -> config.abstinenceXp
        RewardSource.RITUAL_DONE -> config.ritualXp
        else -> 0
    }

    /** Привычка со score 0.8 приносит больше, чем едва начатая, но разрыв ограничен. */
    private fun habitScoreMultiplier(score: Float?): Float {
        if (score == null) return 1f
        val range = config.habitScoreMultiplierRange
        return range.start + (range.endInclusive - range.start) * score.coerceIn(0f, 1f)
    }

    /**
     * Множитель монет от экипировки.
     *
     * Все проценты складываются аддитивно и применяются одним множителем:
     * перемножение отдельных бонусов давало бы неожиданные скачки
     * и делало бы потолки бессмысленными.
     */
    fun coinMultiplier(context: RewardContext): Float {
        val effects = context.equipmentEffects
        var percent = effects[EffectKeys.COIN_MULT] ?: 0f

        context.sphere?.let { sphere ->
            percent += effects["${EffectKeys.COIN_MULT_SPHERE}:${sphere.name}"] ?: 0f
        }

        context.time?.let { time ->
            if (time.hour < MORNING_UNTIL_HOUR) {
                percent += effects[EffectKeys.COIN_MULT_MORNING] ?: 0f
            }
            if (time.hour >= NIGHT_FROM_HOUR) {
                percent += effects[EffectKeys.COIN_MULT_NIGHT] ?: 0f
            }
        }

        when (context.source) {
            RewardSource.ABSTINENCE_DAY ->
                percent += flatToPercent(effects[EffectKeys.ABSTINENCE_COIN], config.abstinenceDayCoins)
            RewardSource.CRAVING_RESISTED ->
                percent += flatToPercent(effects[EffectKeys.CRAVING_WARD], config.cravingResistedCoins)
            RewardSource.FOCUS_DONE ->
                percent += flatToPercent(effects[EffectKeys.FOCUS_COIN], config.focusCoins)
            RewardSource.TASK_DONE -> if (context.priority == Priority.P1) {
                percent += effects[EffectKeys.TASK_PRIORITY_BONUS] ?: 0f
            }
            RewardSource.ROUTINE_DONE -> if (context.flawless) {
                percent += effects[EffectKeys.ROUTINE_BONUS] ?: 0f
            }
            else -> Unit
        }

        // Потолок применяется к сумме, а не к каждому источнику
        val capped = percent.coerceAtMost(EffectKeys.CAPS[EffectKeys.COIN_MULT] ?: percent)
        return (1f + capped / 100f).coerceAtLeast(0f)
    }

    private fun xpMultiplier(context: RewardContext): Float =
        1f + (context.equipmentEffects[EffectKeys.XP_MULT] ?: 0f) / 100f

    /**
     * Плоские бонусы вида «+N монет за день отказа» переводятся в проценты,
     * чтобы всё начисление шло одним множителем и одна формула отвечала за итог.
     */
    private fun flatToPercent(flat: Float?, base: Int): Float {
        if (flat == null || base <= 0) return 0f
        return flat / base * 100f
    }

    // --- уровни ------------------------------------------------------------

    /** Порог XP для уровня. Рост геометрический: каждый следующий дороже. */
    fun xpForLevel(level: Int): Long {
        if (level <= 1) return 0
        return (config.baseXpPerLevel * (config.xpGrowthPerLevel.toDouble()
            .pow((level - 2).toDouble())) * (level - 1)).toLong()
    }

    fun levelForXp(xp: Long): Int {
        var level = 1
        while (level < MAX_LEVEL && xp >= xpForLevel(level + 1)) level++
        return level
    }

    fun progressToNextLevel(xp: Long): Float {
        val level = levelForXp(xp)
        val from = xpForLevel(level)
        val to = xpForLevel(level + 1)
        if (to <= from) return 1f
        return ((xp - from).toFloat() / (to - from)).coerceIn(0f, 1f)
    }

    private companion object {
        const val STALE_THRESHOLD = 3
        const val MORNING_UNTIL_HOUR = 10
        const val NIGHT_FROM_HOUR = 22
        const val MAX_LEVEL = 999
    }
}
