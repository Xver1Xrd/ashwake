package dev.ashwake.domain.engine.character

import dev.ashwake.domain.model.character.EquipItem
import dev.ashwake.domain.model.character.EquipSlot
import dev.ashwake.domain.model.character.ItemEffect
import dev.ashwake.domain.model.character.Rarity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemBudgetValidatorTest {

    private val validator = ItemBudgetValidator()

    private fun item(
        rarity: Rarity = Rarity.COMMON,
        effects: List<ItemEffect> = emptyList()
    ) = EquipItem(
        id = "test",
        baseSpriteId = "sprite",
        slot = EquipSlot.CHEST,
        rarity = rarity,
        effects = effects,
        name = "тест"
    )

    @Test
    fun `предмет в рамках бюджета проходит проверку`() {
        // COMMON: бюджет 10. coinMult +8% стоит 8 очков
        val result = validator.validate(listOf(item(effects = listOf(ItemEffect(EffectKeys.COIN_MULT, 8f)))))
        assertTrue(result.isValid)
    }

    @Test
    fun `превышение бюджета фиксируется и обрезается`() {
        val result = validator.validate(
            listOf(item(effects = listOf(ItemEffect(EffectKeys.COIN_MULT, 30f))))
        )
        assertFalse(result.isValid)
        assertTrue(result.issues.any { it is ValidationIssue.BudgetExceeded })
        // После обрезки укладывается в бюджет COMMON
        val trimmed = result.items.first()
        assertTrue(validator.spentBudget(trimmed.effects) <= Rarity.COMMON.budget + 0.01f)
    }

    @Test
    fun `неизвестный ключ эффекта отбраковывается`() {
        val result = validator.validate(
            listOf(item(effects = listOf(ItemEffect("autoCompleteHabit", 100f))))
        )
        assertTrue(result.issues.any { it is ValidationIssue.UnknownEffect })
        assertTrue(result.items.first().effects.isEmpty())
    }

    @Test
    fun `эффектов больше трёх не бывает у обычных предметов`() {
        val effects = listOf(
            ItemEffect(EffectKeys.COIN_MULT, 1f),
            ItemEffect(EffectKeys.XP_MULT, 1f),
            ItemEffect(EffectKeys.LOOT_LUCK, 1f),
            ItemEffect(EffectKeys.FOCUS_COIN, 1f)
        )
        val result = validator.validate(listOf(item(Rarity.EPIC, effects)))

        assertTrue(result.issues.any { it is ValidationIssue.TooManyEffects })
        assertEquals(3, result.items.first().effects.size)
    }

    @Test
    fun `у реликвии допустимы четыре эффекта`() {
        val effects = listOf(
            ItemEffect(EffectKeys.COIN_MULT, 1f),
            ItemEffect(EffectKeys.XP_MULT, 1f),
            ItemEffect(EffectKeys.LOOT_LUCK, 1f),
            ItemEffect(EffectKeys.FOCUS_COIN, 1f)
        )
        val result = validator.validate(listOf(item(Rarity.RELIC, effects)))
        assertFalse(result.issues.any { it is ValidationIssue.TooManyEffects })
    }

    @Test
    fun `отрицательный эффект возвращает бюджет, а не тратит`() {
        // freezeCap -2 стоит -24 очка, значит на плюсы остаётся больше
        val cursed = listOf(
            ItemEffect(EffectKeys.COIN_MULT, 30f),
            ItemEffect(EffectKeys.FREEZE_CAP, -2f)
        )
        assertEquals(30f - 24f, validator.spentBudget(cursed), 0.01f)

        val result = validator.validate(listOf(item(Rarity.COMMON, cursed)))
        assertTrue("проклятый предмет должен укладываться в бюджет", result.isValid)
    }

    @Test
    fun `обрезка не трогает отрицательные эффекты`() {
        val cursed = listOf(
            ItemEffect(EffectKeys.COIN_MULT, 100f),
            ItemEffect(EffectKeys.FREEZE_CAP, -1f)
        )
        val trimmed = validator.trimToBudget(cursed, Rarity.COMMON.budget)
        val negative = trimmed.first { it.key == EffectKeys.FREEZE_CAP }

        assertEquals(-1f, negative.value, 0.001f)
        assertTrue(trimmed.first { it.key == EffectKeys.COIN_MULT }.value < 100f)
    }

    @Test
    fun `отсутствующий спрайт замечается при включённой проверке`() {
        val result = validator.validate(
            listOf(item()),
            knownSprites = setOf("other"),
            checkSprites = true
        )
        assertTrue(result.issues.any { it is ValidationIssue.MissingSprite })
    }

    @Test
    fun `без проверки спрайтов каталог валиден на плейсхолдерах`() {
        val result = validator.validate(listOf(item()), checkSprites = false)
        assertTrue(result.isValid)
    }
}
