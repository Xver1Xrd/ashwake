package dev.ashwake.domain.engine.character

import dev.ashwake.domain.model.character.MaterialType
import dev.ashwake.domain.model.character.Rarity

/**
 * Стоимость апгрейда в материалах (п. 16.9).
 *
 * Чем реже предмет, тем дороже материал: обычную вещь качает любой,
 * легендарную — только тот, кто копил руны неделями.
 */
object MaterialCost {

    /** Сколько и какого материала нужно на апгрейд предмета данной редкости. */
    fun forRarity(rarity: Rarity): Map<MaterialType, Int> = when (rarity) {
        Rarity.COMMON -> mapOf(MaterialType.COMMON to 1)
        Rarity.UNCOMMON -> mapOf(MaterialType.COMMON to 2)
        Rarity.RARE -> mapOf(MaterialType.COMMON to 4)
        Rarity.EPIC -> mapOf(MaterialType.DOUBLE to 2)
        Rarity.LEGENDARY -> mapOf(MaterialType.DOUBLE to 4)
        Rarity.RELIC -> mapOf(MaterialType.RARE to 3)
    }

    /**
     * Сверка наличия. @return отсутствующие материалы; пустая карта —
     * можно списывать.
     */
    fun missing(
        required: Map<MaterialType, Int>,
        owned: Map<MaterialType, Int>
    ): Map<MaterialType, Int> =
        required.filter { (type, amount) -> (owned[type] ?: 0) < amount }
}
