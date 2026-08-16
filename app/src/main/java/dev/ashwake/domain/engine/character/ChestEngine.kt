package dev.ashwake.domain.engine.character

import dev.ashwake.domain.engine.reward.RewardConfig
import dev.ashwake.domain.model.character.EquipItem
import dev.ashwake.domain.model.character.MaterialCount
import dev.ashwake.domain.model.character.MaterialType
import dev.ashwake.domain.model.character.Rarity
import kotlin.random.Random
import javax.inject.Inject

/** Раздача сундука: монеты, материалы и редкий предмет. */
data class ChestReward(
    val coins: Int,
    val materials: List<MaterialCount>,
    val itemId: String? = null
) {
    val isEmpty: Boolean get() = coins == 0 && materials.isEmpty() && itemId == null
}

sealed interface ChestResult {
    data class Opened(val reward: ChestReward) : ChestResult
    data object AlreadyOpened : ChestResult
    data object NoReward : ChestResult
}

/**
 * Ежедневный сундук (п. 16.9).
 *
 * Чистый движок: решает, что упало, и не трогает базу. Раздача честная
 * (не зависит от времени и попыток), множитель от экипировки применяется
 * к монетам, а не к шансам — иначе «удача» ломала бы сам баланс сундука.
 */
class ChestEngine @Inject constructor(
    private val config: RewardConfig
) {

    fun roll(
        lootLuckPercent: Float,
        ownedItemIds: Set<String>,
        shopItems: List<EquipItem>,
        random: Random = Random.Default
    ): ChestReward {
        val coins = (config.chestCoins * (1f + lootLuckPercent / 100f))
            .toInt()
            .coerceAtLeast(config.chestCoins)

        val materials = buildList {
            add(MaterialCount(MaterialType.COMMON, 1 + random.nextInt(3)))
            if (random.nextFloat() < config.chestDoubleChance) {
                add(MaterialCount(MaterialType.DOUBLE, 1))
            }
            if (random.nextFloat() < config.chestRareChance) {
                add(MaterialCount(MaterialType.RARE, 1))
            }
        }

        return ChestReward(
            coins = coins,
            materials = materials,
            itemId = rollItem(ownedItemIds, shopItems, random)
        )
    }

    /**
     * Редкий предмет с шансом, растущим с редкостью дропа: легендарка
     * выпадает реже эпика, но и её можно вытащить.
     */
    private fun rollItem(
        ownedItemIds: Set<String>,
        shopItems: List<EquipItem>,
        random: Random
    ): String? {
        if (random.nextFloat() >= config.chestItemChance) return null

        val candidates = shopItems.filter { it.id !in ownedItemIds }
        if (candidates.isEmpty()) return null

        // Вес по редкости: чем реже, тем меньше шанс именно её
        val weights = candidates.map { item ->
            item to when (item.rarity) {
                Rarity.COMMON -> 50f
                Rarity.UNCOMMON -> 25f
                Rarity.RARE -> 12f
                Rarity.EPIC -> 6f
                Rarity.LEGENDARY -> 4f
                Rarity.RELIC -> 2f
            }
        }
        val total = weights.sumOf { it.second.toDouble() }
        var pick = random.nextDouble() * total
        return weights.firstOrNull {
            pick -= it.second.toDouble()
            pick < 0.0
        }?.first?.id
    }
}
