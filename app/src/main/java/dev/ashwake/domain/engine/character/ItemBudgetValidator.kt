package dev.ashwake.domain.engine.character

import dev.ashwake.domain.model.character.EquipItem
import dev.ashwake.domain.model.character.ItemEffect
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

sealed interface ValidationIssue {
    val itemId: String

    data class UnknownEffect(override val itemId: String, val key: String) : ValidationIssue
    data class TooManyEffects(
        override val itemId: String,
        val count: Int,
        val allowed: Int
    ) : ValidationIssue
    data class BudgetExceeded(
        override val itemId: String,
        val spent: Float,
        val budget: Int
    ) : ValidationIssue
    data class MissingSprite(override val itemId: String, val spriteId: String) : ValidationIssue
}

data class ValidationResult(
    val issues: List<ValidationIssue>,
    /** Предметы после обрезки: в release эффекты подгоняются под бюджет. */
    val items: List<EquipItem>
) {
    val isValid: Boolean get() = issues.isEmpty()
}

/**
 * Проверка каталога на старте (п. 16.6).
 *
 * Ошибка — краш в debug, обрезание эффекта до бюджета в release с записью в лог.
 * Смысл в том, что предмет с эффектами сверх бюджета ломает экономику молча,
 * и заметить это без валидатора можно только по жалобам.
 */
@Singleton
class ItemBudgetValidator @Inject constructor() {

    fun validate(
        items: List<EquipItem>,
        knownSprites: Set<String> = emptySet(),
        checkSprites: Boolean = false
    ): ValidationResult {
        val issues = mutableListOf<ValidationIssue>()
        val fixed = items.map { item ->
            var effects = item.effects

            effects.filterNot { EffectKeys.isKnown(it.key) }.forEach {
                issues += ValidationIssue.UnknownEffect(item.id, it.key)
            }
            effects = effects.filter { EffectKeys.isKnown(it.key) }

            if (effects.size > item.rarity.maxEffects) {
                issues += ValidationIssue.TooManyEffects(
                    item.id, effects.size, item.rarity.maxEffects
                )
                effects = effects.take(item.rarity.maxEffects)
            }

            if (checkSprites && item.baseSpriteId !in knownSprites) {
                issues += ValidationIssue.MissingSprite(item.id, item.baseSpriteId)
            }

            val spent = spentBudget(effects)
            if (spent > item.rarity.budget) {
                issues += ValidationIssue.BudgetExceeded(item.id, spent, item.rarity.budget)
                effects = trimToBudget(effects, item.rarity.budget)
            }

            item.copy(effects = effects)
        }
        return ValidationResult(issues, fixed)
    }

    /**
     * Потраченный бюджет.
     *
     * Отрицательные эффекты возвращают очки, а не тратят: проклятый предмет
     * платит минусом за сильный плюс (п. 16.5.2).
     */
    fun spentBudget(effects: List<ItemEffect>): Float =
        effects.sumOf { EffectKeys.cost(it.key, it.value).toDouble() }.toFloat()

    /**
     * Обрезка под бюджет: положительные эффекты ужимаются пропорционально,
     * отрицательные остаются нетронутыми — иначе обрезка превратила бы
     * проклятый предмет в обычный и тем самым его усилила.
     */
    fun trimToBudget(effects: List<ItemEffect>, budget: Int): List<ItemEffect> {
        val positiveCost = effects
            .filter { EffectKeys.cost(it.key, it.value) > 0 }
            .sumOf { EffectKeys.cost(it.key, it.value).toDouble() }
            .toFloat()
        val negativeCost = effects
            .filter { EffectKeys.cost(it.key, it.value) < 0 }
            .sumOf { EffectKeys.cost(it.key, it.value).toDouble() }
            .toFloat()

        val allowedPositive = budget - negativeCost
        if (positiveCost <= allowedPositive || positiveCost <= 0f) return effects

        val scale = allowedPositive / positiveCost
        return effects.map { effect ->
            if (EffectKeys.cost(effect.key, effect.value) > 0) {
                effect.copy(value = roundValue(effect.value * scale))
            } else effect
        }
    }

    /** Цена предмета из бюджета редкости — подсказка при наполнении каталога. */
    fun suggestedPrice(item: EquipItem): Int? {
        if (item.price != null) return item.price
        val spent = abs(spentBudget(item.effects))
        return (spent * PRICE_PER_BUDGET_POINT).toInt().coerceAtLeast(MIN_PRICE)
    }

    private fun roundValue(value: Float): Float = Math.round(value * 10f) / 10f

    private companion object {
        const val PRICE_PER_BUDGET_POINT = 25f
        const val MIN_PRICE = 50
    }
}
