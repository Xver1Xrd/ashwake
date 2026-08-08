package dev.ashwake.domain.engine.character

/**
 * Допустимые ключи эффектов (п. 16.5.2) и их цена в очках бюджета.
 *
 * Список закрытый намеренно: неизвестный ключ означает, что предмет обещает
 * механику, которой в приложении нет. Валидатор такое роняет в debug.
 *
 * Ни один эффект не обходит механики честности: автоотметок, закрытия привычки
 * без выполнения и обхода блокировки приложений здесь нет и быть не может.
 */
object EffectKeys {

    const val COIN_MULT = "coinMult"
    const val COIN_MULT_SPHERE = "coinMultSphere"
    const val COIN_MULT_MORNING = "coinMultMorning"
    const val COIN_MULT_NIGHT = "coinMultNight"
    const val XP_MULT = "xpMult"
    const val SCORE_DECAY_SLOW = "scoreDecaySlow"
    const val FREEZE_CAP = "freezeCap"
    const val STREAK_SHIELD = "streakShield"
    const val PUNCTUAL_BONUS = "punctualBonus"
    const val EARLY_BONUS = "earlyBonus"
    const val COMBO_BONUS = "comboBonus"
    const val ABSTINENCE_COIN = "abstinenceCoin"
    const val CRAVING_WARD = "cravingWard"
    const val FOCUS_COIN = "focusCoin"
    const val ROUTINE_BONUS = "routineBonus"
    const val TASK_PRIORITY_BONUS = "taskPriorityBonus"
    const val LOOT_LUCK = "lootLuck"
    const val REROLL_CHEST = "rerollChest"
    const val OVERDUE_RELIEF = "overdueRelief"

    /** Цена единицы эффекта в очках бюджета (п. 16.5.3). */
    private val UNIT_COST: Map<String, Float> = mapOf(
        COIN_MULT to 1f,
        COIN_MULT_SPHERE to 0.6f,
        COIN_MULT_MORNING to 0.8f,
        COIN_MULT_NIGHT to 0.8f,
        XP_MULT to 1f,
        SCORE_DECAY_SLOW to 1.2f,
        FREEZE_CAP to 12f,
        STREAK_SHIELD to 25f,
        PUNCTUAL_BONUS to 0.8f,
        EARLY_BONUS to 0.8f,
        COMBO_BONUS to 0.8f,
        ABSTINENCE_COIN to 3f,
        CRAVING_WARD to 2f,
        FOCUS_COIN to 2f,
        ROUTINE_BONUS to 1f,
        TASK_PRIORITY_BONUS to 1f,
        LOOT_LUCK to 1.5f,
        REROLL_CHEST to 10f,
        OVERDUE_RELIEF to 1f
    )

    /**
     * Жёсткие потолки итоговых значений (п. 16.6).
     * Без них полный сет плюс апгрейды ломают экономику.
     */
    val CAPS: Map<String, Float> = mapOf(
        COIN_MULT to 200f,
        SCORE_DECAY_SLOW to 50f,
        FREEZE_CAP to 5f,
        LOOT_LUCK to 25f
    )

    /** Базовое имя ключа без параметра: `coinMultSphere:SPORT` → `coinMultSphere`. */
    fun baseKey(key: String): String = key.substringBefore(':')

    /** Параметр ключа, если он есть: сфера у `coinMultSphere:SPORT`. */
    fun parameter(key: String): String? = key.substringAfter(':', "").takeIf { it.isNotEmpty() }

    fun isKnown(key: String): Boolean = baseKey(key) in UNIT_COST

    fun unitCost(key: String): Float = UNIT_COST[baseKey(key)] ?: 0f

    /**
     * Стоимость эффекта в очках бюджета.
     *
     * Отрицательные эффекты возвращают бюджет, а не тратят (п. 16.5.2):
     * проклятый предмет может дать сильный плюс ценой заметного минуса.
     */
    fun cost(key: String, value: Float): Float = unitCost(key) * value

    fun capFor(key: String): Float? = CAPS[baseKey(key)]

    val all: Set<String> get() = UNIT_COST.keys
}
