package dev.ashwake.domain.engine.character

import dev.ashwake.core.model.Stat
import dev.ashwake.domain.model.character.Affix
import dev.ashwake.domain.model.character.EquipItem
import dev.ashwake.domain.model.character.EquipSlot
import dev.ashwake.domain.model.character.ItemEffect
import dev.ashwake.domain.model.character.ItemSet
import dev.ashwake.domain.model.character.Rarity
import dev.ashwake.domain.model.character.Style
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EquipmentEngineTest {

    private val engine = EquipmentEngine()

    private fun item(
        id: String,
        slot: EquipSlot = EquipSlot.CHEST,
        effects: List<ItemEffect> = emptyList(),
        setTag: String? = null,
        requires: Map<Stat, Int> = emptyMap(),
        upgradeLevel: Int = 0,
        hides: List<EquipSlot> = emptyList(),
        prefixId: String? = null
    ) = EquipItem(
        id = id,
        baseSpriteId = id,
        slot = slot,
        effects = effects,
        setTag = setTag,
        requires = requires,
        upgradeLevel = upgradeLevel,
        hides = hides,
        prefixId = prefixId,
        name = id,
        rarity = Rarity.RARE,
        style = Style.ARMOR
    )

    @Test
    fun `одинаковые эффекты складываются аддитивно, а не перемножаются`() {
        val equipped = listOf(
            item("a", EquipSlot.CHEST, listOf(ItemEffect(EffectKeys.COIN_MULT, 20f))),
            item("b", EquipSlot.LEGS, listOf(ItemEffect(EffectKeys.COIN_MULT, 20f)))
        )
        // Два по +20% дают +40%, а не +44%
        assertEquals(40f, engine.compute(equipped).effect(EffectKeys.COIN_MULT), 0.001f)
    }

    @Test
    fun `апгрейд усиливает эффект по формуле восьми процентов за уровень`() {
        val equipped = listOf(
            item("a", effects = listOf(ItemEffect(EffectKeys.COIN_MULT, 10f)), upgradeLevel = 5)
        )
        // 10 * (1 + 0.08 * 5) = 14
        assertEquals(14f, engine.compute(equipped).effect(EffectKeys.COIN_MULT), 0.001f)
    }

    @Test
    fun `апгрейд выше десятого не усиливает`() {
        assertEquals(engine.upgradeMultiplier(10), engine.upgradeMultiplier(15), 0.0001f)
    }

    @Test
    fun `потолок обрезает итог, а не каждый источник по отдельности`() {
        val equipped = (1..10).map { index ->
            item("item$index", effects = listOf(ItemEffect(EffectKeys.COIN_MULT, 50f)))
        }
        // Сумма 500%, потолок 200%
        assertEquals(200f, engine.compute(equipped).effect(EffectKeys.COIN_MULT), 0.001f)
    }

    @Test
    fun `отрицательные эффекты потолком не режутся`() {
        val equipped = listOf(
            item("cursed", effects = listOf(ItemEffect(EffectKeys.FREEZE_CAP, -2f)))
        )
        assertEquals(-2f, engine.compute(equipped).effect(EffectKeys.FREEZE_CAP), 0.001f)
    }

    @Test
    fun `сетовые бонусы кумулятивны и дают все пройденные ступени`() {
        val set = ItemSet(
            tag = "knight", title = "Рыцарь", style = Style.ARMOR,
            bonus2 = listOf(ItemEffect(EffectKeys.SCORE_DECAY_SLOW, 10f)),
            bonus4 = listOf(ItemEffect(EffectKeys.STREAK_SHIELD, 1f)),
            bonus6 = listOf(ItemEffect(EffectKeys.COIN_MULT, 15f))
        )
        val equipped = EquipSlot.entries.take(6).mapIndexed { index, slot ->
            item("knight$index", slot = slot, setTag = "knight")
        }
        val result = engine.compute(equipped, sets = mapOf("knight" to set))

        assertEquals(1, result.activeSets.size)
        assertEquals(6, result.activeSets.first().pieces)
        assertEquals(listOf(2, 4, 6), result.activeSets.first().tiers)
        assertEquals(10f, result.effect(EffectKeys.SCORE_DECAY_SLOW), 0.001f)
        assertEquals(1f, result.effect(EffectKeys.STREAK_SHIELD), 0.001f)
        assertEquals(15f, result.effect(EffectKeys.COIN_MULT), 0.001f)
    }

    @Test
    fun `трёх частей хватает только на бонус за две`() {
        val set = ItemSet(
            tag = "runner", title = "Бегун", style = Style.SPORT,
            bonus2 = listOf(ItemEffect(EffectKeys.COIN_MULT, 10f)),
            bonus4 = listOf(ItemEffect(EffectKeys.COIN_MULT, 15f))
        )
        val equipped = EquipSlot.entries.take(3).mapIndexed { index, slot ->
            item("runner$index", slot = slot, setTag = "runner")
        }
        val result = engine.compute(equipped, sets = mapOf("runner" to set))

        assertEquals(listOf(2), result.activeSets.first().tiers)
        assertEquals(10f, result.effect(EffectKeys.COIN_MULT), 0.001f)
    }

    @Test
    fun `предмет без выполненных требований не даёт эффектов`() {
        val equipped = listOf(
            item(
                "heavy",
                effects = listOf(ItemEffect(EffectKeys.COIN_MULT, 50f)),
                requires = mapOf(Stat.STRENGTH to 12)
            )
        )
        val result = engine.compute(equipped, stats = mapOf(Stat.STRENGTH to 5))

        assertEquals(0f, result.effect(EffectKeys.COIN_MULT), 0.001f)
        assertEquals(1, result.blockedItems.size)
    }

    @Test
    fun `подсказка показывает, чего именно не хватает`() {
        val heavy = item("heavy", requires = mapOf(Stat.STRENGTH to 12, Stat.WILL to 5))
        val missing = engine.missingRequirements(heavy, mapOf(Stat.STRENGTH to 9, Stat.WILL to 5))

        assertEquals(mapOf(Stat.STRENGTH to 3), missing)
    }

    @Test
    fun `аффикс добавляет эффекты и бонус к характеристике`() {
        val affix = Affix(
            id = "sturdy", isPrefix = true, label = "Крепкий",
            effects = listOf(ItemEffect(EffectKeys.SCORE_DECAY_SLOW, 5f)),
            statBonus = mapOf(Stat.ENDURANCE to 2)
        )
        val equipped = listOf(item("jacket", prefixId = "sturdy"))
        val result = engine.compute(equipped, affixes = mapOf("sturdy" to affix))

        assertEquals(5f, result.effect(EffectKeys.SCORE_DECAY_SLOW), 0.001f)
        assertEquals(2, result.statBonuses[Stat.ENDURANCE])
    }

    @Test
    fun `эффекты аффикса не масштабируются апгрейдом предмета`() {
        val affix = Affix(
            id = "sturdy", isPrefix = true, label = "Крепкий",
            effects = listOf(ItemEffect(EffectKeys.COIN_MULT, 10f))
        )
        val equipped = listOf(item("jacket", prefixId = "sturdy", upgradeLevel = 10))
        val result = engine.compute(equipped, affixes = mapOf("sturdy" to affix))

        assertEquals(10f, result.effect(EffectKeys.COIN_MULT), 0.001f)
    }

    @Test
    fun `закрытый шлем скрывает волосы и лицо`() {
        val equipped = listOf(
            item("helm", EquipSlot.HELMET, hides = listOf(EquipSlot.HEAD_HAIR, EquipSlot.FACE)),
            item("hair", EquipSlot.HEAD_HAIR),
            item("face", EquipSlot.FACE),
            item("chest", EquipSlot.CHEST)
        )
        val visible = engine.resolveLayers(equipped).map { it.slot }

        assertFalse(EquipSlot.HEAD_HAIR in visible)
        assertFalse(EquipSlot.FACE in visible)
        assertTrue(EquipSlot.HELMET in visible)
    }

    @Test
    fun `порядок отрисовки идёт по слоям`() {
        val equipped = listOf(
            item("shoulders", EquipSlot.SHOULDERS),
            item("chest", EquipSlot.CHEST),
            item("body", EquipSlot.BODY)
        )
        val order = engine.resolveLayers(equipped).map { it.slot }

        assertEquals(listOf(EquipSlot.BODY, EquipSlot.CHEST, EquipSlot.SHOULDERS), order)
    }

    @Test
    fun `капюшон затемняет лицо, но не скрывает его`() {
        val hood = item("hood", EquipSlot.HELMET, hides = listOf(EquipSlot.HEAD_HAIR))
            .copy(dimsFace = true)
        val equipped = listOf(hood, item("face", EquipSlot.FACE))

        assertTrue(engine.shouldDimFace(equipped))
        assertTrue(engine.resolveLayers(equipped).any { it.slot == EquipSlot.FACE })
    }

    @Test
    fun `пустая экипировка не падает`() {
        val result = engine.compute(emptyList())
        assertTrue(result.effects.isEmpty())
        assertTrue(result.activeSets.isEmpty())
    }
}
