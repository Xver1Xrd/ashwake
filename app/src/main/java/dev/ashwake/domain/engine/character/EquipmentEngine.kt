package dev.ashwake.domain.engine.character

import dev.ashwake.core.model.Stat
import dev.ashwake.domain.model.character.Affix
import dev.ashwake.domain.model.character.EquipItem
import dev.ashwake.domain.model.character.EquipSlot
import dev.ashwake.domain.model.character.ItemEffect
import dev.ashwake.domain.model.character.ItemSet
import javax.inject.Inject
import javax.inject.Singleton

data class ActiveSetBonus(
    val set: ItemSet,
    val pieces: Int,
    val tiers: List<Int>
)

data class EquipmentResult(
    /** Итоговые эффекты: ключ → значение с учётом апгрейдов, аффиксов и сетов. */
    val effects: Map<String, Float>,
    val activeSets: List<ActiveSetBonus>,
    val statBonuses: Map<Stat, Int>,
    /** Предметы, надетые без выполнения требований — их эффекты не учтены. */
    val blockedItems: List<EquipItem>
) {
    fun effect(key: String): Float = effects[key] ?: 0f
}

/**
 * Сборка итоговых характеристик экипировки (п. 16.6).
 *
 * Чистый класс: принимает набор надетых предметов, возвращает карту эффектов.
 *
 * Три правила, ради которых он и существует:
 * - одинаковые эффекты из разных источников **складываются аддитивно**,
 *   а не перемножаются: два предмета по +20% дают +40%, а не +44%;
 * - апгрейд усиливает эффект по формуле `значение * (1 + 0.08 * N)`;
 * - каждый эффект обрезается своим потолком уже после всех сложений.
 */
@Singleton
class EquipmentEngine @Inject constructor() {

    fun compute(
        equipped: List<EquipItem>,
        affixes: Map<String, Affix> = emptyMap(),
        sets: Map<String, ItemSet> = emptyMap(),
        stats: Map<Stat, Int> = emptyMap()
    ): EquipmentResult {
        // Требования проверяются до всего остального: предмет, который нельзя
        // надеть, не должен просачиваться в расчёт ни одним эффектом
        val (allowed, blocked) = equipped.partition { meetsRequirements(it, stats) }

        val accumulator = mutableMapOf<String, Float>()
        val statBonuses = mutableMapOf<Stat, Int>()

        allowed.forEach { item ->
            effectsOf(item, affixes).forEach { effect ->
                accumulator.merge(effect.key, effect.value, Float::plus)
            }
            listOfNotNull(item.prefixId, item.suffixId)
                .mapNotNull { affixes[it] }
                .forEach { affix ->
                    affix.statBonus.forEach { (stat, bonus) ->
                        statBonuses.merge(stat, bonus, Int::plus)
                    }
                }
        }

        val activeSets = activeSets(allowed, sets)
        activeSets.forEach { active ->
            active.tiers.forEach { tier ->
                bonusFor(active.set, tier).forEach { effect ->
                    accumulator.merge(effect.key, effect.value, Float::plus)
                }
            }
        }

        return EquipmentResult(
            effects = accumulator.mapValues { (key, value) -> applyCap(key, value) },
            activeSets = activeSets,
            statBonuses = statBonuses,
            blockedItems = blocked
        )
    }

    /** Эффекты предмета с учётом апгрейда и аффиксов. */
    fun effectsOf(item: EquipItem, affixes: Map<String, Affix> = emptyMap()): List<ItemEffect> {
        val multiplier = upgradeMultiplier(item.upgradeLevel)
        val base = item.effects.map { ItemEffect(it.key, it.value * multiplier) }
        val fromAffixes = listOfNotNull(item.prefixId, item.suffixId)
            .mapNotNull { affixes[it] }
            .flatMap { it.effects }
        return base + fromAffixes
    }

    /** `эффект(+N) = эффект(+0) * (1 + 0.08 * N)`, N от 0 до 10 (п. 16.5.3). */
    fun upgradeMultiplier(level: Int): Float =
        1f + UPGRADE_STEP * level.coerceIn(0, MAX_UPGRADE)

    fun meetsRequirements(item: EquipItem, stats: Map<Stat, Int>): Boolean =
        item.requires.all { (stat, required) -> (stats[stat] ?: 0) >= required }

    /** Чего именно не хватает — подпись под предметом в магазине. */
    fun missingRequirements(item: EquipItem, stats: Map<Stat, Int>): Map<Stat, Int> =
        item.requires.filter { (stat, required) -> (stats[stat] ?: 0) < required }
            .mapValues { (stat, required) -> required - (stats[stat] ?: 0) }

    /**
     * Активные сеты.
     *
     * Сет — не одинаковые вещи, а предметы с одним тегом. Бонусы кумулятивны:
     * шесть частей дают и двойку, и четвёрку, и шестёрку.
     */
    fun activeSets(equipped: List<EquipItem>, sets: Map<String, ItemSet>): List<ActiveSetBonus> =
        equipped.mapNotNull { it.setTag }
            .groupingBy { it }
            .eachCount()
            .mapNotNull { (tag, pieces) ->
                val set = sets[tag] ?: return@mapNotNull null
                val tiers = SET_TIERS.filter { it <= pieces }
                if (tiers.isEmpty()) null else ActiveSetBonus(set, pieces, tiers)
            }
            .sortedByDescending { it.pieces }

    private fun bonusFor(set: ItemSet, tier: Int): List<ItemEffect> = when (tier) {
        2 -> set.bonus2
        4 -> set.bonus4
        6 -> set.bonus6
        else -> emptyList()
    }

    private fun applyCap(key: String, value: Float): Float {
        val cap = EffectKeys.capFor(key) ?: return value
        // Потолок только сверху: отрицательные эффекты проклятых предметов
        // должны работать в полную силу, иначе минусы обесцениваются
        return if (value > cap) cap else value
    }

    /**
     * Порядок отрисовки с учётом перекрытий (п. 15.3):
     * собрать активные слоты → применить все `hides` → отсортировать по слою.
     */
    fun resolveLayers(equipped: List<EquipItem>): List<EquipItem> {
        val hidden = equipped.flatMap { it.hides }.toSet()
        return equipped
            .filterNot { it.slot in hidden }
            .sortedBy { it.layer }
    }

    /** Затемнять ли лицо: капюшон оставляет его видимым, но приглушённым. */
    fun shouldDimFace(equipped: List<EquipItem>): Boolean =
        equipped.any { it.dimsFace && EquipSlot.FACE !in equipped.flatMap { other -> other.hides } }

    private companion object {
        const val UPGRADE_STEP = 0.08f
        const val MAX_UPGRADE = 10
        val SET_TIERS = listOf(2, 4, 6)
    }
}
