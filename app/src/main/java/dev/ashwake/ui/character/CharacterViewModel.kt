package dev.ashwake.ui.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ashwake.data.assets.Catalog
import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ashwake.data.assets.CatalogLoader
import dev.ashwake.data.export.ExportResult
import dev.ashwake.data.export.ImageExporter
import dev.ashwake.ui.character.render.CharacterBitmapRenderer
import dev.ashwake.ui.character.render.CharacterLayer
import dev.ashwake.ui.character.render.buildCharacterLayers
import dev.ashwake.domain.engine.character.EquipmentEngine
import dev.ashwake.domain.model.character.EquipItem
import dev.ashwake.domain.model.character.EquipSlot
import dev.ashwake.domain.model.character.Rarity
import dev.ashwake.domain.model.character.Style
import dev.ashwake.domain.repository.character.CharacterRepository
import dev.ashwake.domain.repository.character.CharacterState
import dev.ashwake.domain.repository.character.PurchaseResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShopFilter(
    val slot: EquipSlot? = null,
    val style: Style? = null,
    val rarity: Rarity? = null,
    val onlyAffordable: Boolean = false,
    val onlyOwned: Boolean = false
)

@HiltViewModel
class CharacterViewModel @Inject constructor(
    private val character: CharacterRepository,
    private val catalogLoader: CatalogLoader,
    private val equipmentEngine: EquipmentEngine,
    private val bitmapRenderer: CharacterBitmapRenderer,
    private val imageExporter: ImageExporter,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val state: StateFlow<CharacterState> = character.observeState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CharacterState())

    private val _catalog = MutableStateFlow(Catalog.EMPTY)
    val catalog: StateFlow<Catalog> = _catalog.asStateFlow()

    private val _filter = MutableStateFlow(ShopFilter())
    val filter: StateFlow<ShopFilter> = _filter.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Предмет, выделенный в списке: показывается на персонаже до покупки. */
    private val _preview = MutableStateFlow<EquipItem?>(null)
    val preview: StateFlow<EquipItem?> = _preview.asStateFlow()

    init {
        viewModelScope.launch {
            character.ensureBuiltinData()
            _catalog.value = catalogLoader.load()
        }
    }

    // --- магазин и экипировка ----------------------------------------------

    fun setSlotFilter(slot: EquipSlot?) {
        _filter.value = _filter.value.copy(slot = slot)
        _preview.value = null
    }

    fun setStyleFilter(style: Style?) { _filter.value = _filter.value.copy(style = style) }
    fun setRarityFilter(rarity: Rarity?) { _filter.value = _filter.value.copy(rarity = rarity) }
    fun toggleAffordable() {
        _filter.value = _filter.value.copy(onlyAffordable = !_filter.value.onlyAffordable)
    }
    fun toggleOwnedOnly() {
        _filter.value = _filter.value.copy(onlyOwned = !_filter.value.onlyOwned)
    }

    fun preview(item: EquipItem?) { _preview.value = item }

    /** Отфильтрованный список для магазина. */
    fun visibleItems(): List<EquipItem> {
        val current = state.value
        val ownedIds = current.owned.map { it.itemId }.toSet()
        val filter = _filter.value

        return _catalog.value.items.filter { item ->
            (filter.slot == null || item.slot == filter.slot) &&
                (filter.style == null || item.style == filter.style) &&
                (filter.rarity == null || item.rarity == filter.rarity) &&
                (!filter.onlyOwned || item.id in ownedIds) &&
                (!filter.onlyAffordable || (item.price ?: Int.MAX_VALUE) <= current.wallet.coins)
        }.sortedWith(compareBy({ it.rarity.ordinal }, { it.price ?: Int.MAX_VALUE }))
    }

    fun isOwned(item: EquipItem): Boolean =
        item.price == 0 || state.value.owned.any { it.itemId == item.id }

    fun missingRequirements(item: EquipItem) =
        equipmentEngine.missingRequirements(item, state.value.statMap)

    fun equip(item: EquipItem) {
        viewModelScope.launch {
            if (!character.equip(item.id)) _message.value = "Предмет ещё не куплен"
            else _preview.value = null
        }
    }

    fun unequip(slot: EquipSlot) {
        viewModelScope.launch { character.unequip(slot) }
    }

    fun unequipAll() {
        viewModelScope.launch { character.unequipAll() }
    }

    fun buy(item: EquipItem) {
        viewModelScope.launch {
            _message.value = when (val result = character.buy(item.id)) {
                PurchaseResult.Success -> {
                    character.equip(item.id)
                    "Куплено: ${item.name}"
                }
                PurchaseResult.NotEnoughCoins -> "Не хватает монет"
                PurchaseResult.AlreadyOwned -> "Уже есть"
                PurchaseResult.NotForSale -> "Не продаётся: только за достижение"
                is PurchaseResult.RequirementsNotMet ->
                    "Не хватает: " + result.missing.entries.joinToString {
                        "${statTitle(it.key)} +${it.value}"
                    }
            }
        }
    }

    /**
     * Цена улучшения растёт с уровнем: иначе десятый апгрейд стоил бы столько же,
     * сколько первый, и качать было бы нечего.
     */
    fun upgradeCost(item: EquipItem): Int {
        val level = state.value.owned.firstOrNull { it.itemId == item.id }?.upgradeLevel ?: 0
        val base = item.price ?: BASE_UPGRADE_COST
        return (base * UPGRADE_COST_SHARE * (level + 1)).toInt().coerceAtLeast(MIN_UPGRADE_COST)
    }

    fun upgrade(item: EquipItem) {
        viewModelScope.launch {
            _message.value = when (character.upgrade(item.id, upgradeCost(item))) {
                PurchaseResult.Success -> "Улучшено"
                PurchaseResult.NotEnoughCoins -> "Не хватает монет"
                else -> "Улучшить нельзя"
            }
        }
    }

    // --- пресеты -----------------------------------------------------------

    fun savePreset(index: Int) {
        viewModelScope.launch {
            character.savePreset(index, "Образ ${index + 1}")
            _message.value = "Образ ${index + 1} сохранён"
        }
    }

    fun applyPreset(index: Int) {
        viewModelScope.launch { character.applyPreset(index.toLong()) }
    }

    fun consumeMessage() { _message.value = null }

    /**
     * «Сохранить портрет» (п. 15.9): рендер на масштабе x8 с фоном и рамкой.
     * Восьмикратный масштаб выбран потому, что 128×128 в галерее выглядит
     * иконкой, а не картинкой.
     */
    fun savePortrait() {
        viewModelScope.launch {
            val bitmap = bitmapRenderer.render(
                layers = currentLayers(),
                scale = PORTRAIT_SCALE,
                background = PORTRAIT_BACKGROUND,
                withFloor = true,
                frame = true
            )
            _message.value = when (val result = imageExporter.saveToGallery(bitmap, portraitName())) {
                is ExportResult.Saved -> "Портрет сохранён в галерею"
                is ExportResult.Failed -> result.reason
            }
        }
    }

    fun sharePortrait() {
        viewModelScope.launch {
            val bitmap = bitmapRenderer.render(
                layers = currentLayers(),
                scale = PORTRAIT_SCALE,
                background = PORTRAIT_BACKGROUND,
                withFloor = true,
                frame = true
            )
            val intent = imageExporter.shareIntent(bitmap, portraitName())
            if (intent == null) {
                _message.value = "Не удалось подготовить картинку"
                return@launch
            }
            context.startActivity(
                Intent.createChooser(intent, "Поделиться портретом")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun currentLayers(): List<CharacterLayer> = buildCharacterLayers(
        items = state.value.equipped.values.toList(),
        tints = _catalog.value.paletteTints
    )

    private fun portraitName(): String =
        "ashwake-" + state.value.profile.name.lowercase().replace(' ', '-') +
            "-" + System.currentTimeMillis()

    private companion object {
        const val UPGRADE_COST_SHARE = 0.3f
        const val BASE_UPGRADE_COST = 200
        const val MIN_UPGRADE_COST = 50

        const val PORTRAIT_SCALE = 8
        const val PORTRAIT_BACKGROUND = 0xFF1A1622.toInt()
        const val DEFAULT_TINT = 0xFF6E7BA6.toInt()
    }
}

internal fun statTitle(stat: dev.ashwake.core.model.Stat): String = when (stat) {
    dev.ashwake.core.model.Stat.STRENGTH -> "Сила"
    dev.ashwake.core.model.Stat.AGILITY -> "Ловкость"
    dev.ashwake.core.model.Stat.ENDURANCE -> "Выносливость"
    dev.ashwake.core.model.Stat.INTELLECT -> "Интеллект"
    dev.ashwake.core.model.Stat.WILL -> "Воля"
    dev.ashwake.core.model.Stat.LUCK -> "Удача"
}
