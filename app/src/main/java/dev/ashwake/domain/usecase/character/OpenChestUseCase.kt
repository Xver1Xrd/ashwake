package dev.ashwake.domain.usecase.character

import dev.ashwake.core.time.AppClock
import dev.ashwake.data.assets.CatalogLoader
import dev.ashwake.domain.engine.character.ChestEngine
import dev.ashwake.domain.engine.character.ChestResult
import dev.ashwake.domain.repository.character.CharacterRepository
import javax.inject.Inject

/**
 * Открытие ежедневного сундука.
 *
 * Единственная точка, где сундук открывается: кнопка на экране персонажа —
 * и, возможно, позже виджет. Проверка «уже открыт сегодня» живёт здесь,
 * чтобы ни одна кнопка не могла задваивать раздачу.
 */
class OpenChestUseCase @Inject constructor(
    private val character: CharacterRepository,
    private val catalogLoader: CatalogLoader,
    private val chestEngine: ChestEngine,
    private val clock: AppClock
) {
    suspend operator fun invoke(): ChestResult {
        val today = clock.today().toEpochDay().toInt()
        if (character.chestState(today).opened) return ChestResult.AlreadyOpened

        val state = character.state()
        val reward = chestEngine.roll(
            lootLuckPercent = state.effect("lootLuck"),
            ownedItemIds = state.owned.map { it.itemId }.toSet(),
            shopItems = catalogLoader.load().items
        )
        if (reward.isEmpty) return ChestResult.NoReward

        character.applyChest(reward, today)
        return ChestResult.Opened(reward)
    }
}
