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
import dev.ashwake.domain.engine.character.EquipmentEngine
import dev.ashwake.domain.engine.character.ChestResult
import dev.ashwake.domain.engine.character.MaterialCost
import dev.ashwake.domain.model.character.EquipItem
import dev.ashwake.domain.model.character.EquipSlot
import dev.ashwake.domain.model.character.MaterialType
import dev.ashwake.domain.model.character.Rarity
import dev.ashwake.domain.model.character.Style
import dev.ashwake.domain.repository.character.CharacterRepository
import dev.ashwake.domain.repository.character.CharacterState
import dev.ashwake.domain.repository.character.ChestState
import dev.ashwake.domain.repository.character.PurchaseResult
import dev.ashwake.domain.repository.character.UpgradeResult
import dev.ashwake.domain.usecase.character.OpenChestUseCase
import dev.ashwake.domain.usecase.character.RefreshAchievementsUseCase
import dev.ashwake.data.assets.AchievementLoader
import dev.ashwake.core.time.AppClock
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
    private val openChestUseCase: OpenChestUseCase,
    private val refreshAchievementsUseCase: RefreshAchievementsUseCase,
    private val achievementLoader: AchievementLoader,
    private val clock: AppClock,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val state: StateFlow<CharacterState> = character.observeState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CharacterState())

    private val _catalog = MutableStateFlow(Catalog.EMPTY)
    val catalog: StateFlow<Catalog> = _catalog.asStateFlow()

    /** Каталог достижений из assets — по нему строится список на экране. */
    private val _achievements = MutableStateFlow(emptyList<dev.ashwake.domain.engine.achievement.AchievementDefinition>())
    val achievements: StateFlow<List<dev.ashwake.domain.engine.achievement.AchievementDefinition>> =
        _achievements.asStateFlow()

    private val _filter = MutableStateFlow(ShopFilter())
    val filter: StateFlow<ShopFilter> = _filter.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Предмет, выделенный в списке: показывается на персонаже до покупки. */
    private val _preview = MutableStateFlow<EquipItem?>(null)
    val preview: StateFlow<EquipItem?> = _preview.asStateFlow()

    /** Состояние сегодняшнего сундука: обновляется после каждого открытия. */
    private val _chest = MutableStateFlow(ChestState(opened = false))
    val chest: StateFlow<ChestState> = _chest.asStateFlow()

    /** Подтверждение апгрейда, когда материалов не хватает. */
    private val _pendingUpgrade = MutableStateFlow<EquipItem?>(null)
    val pendingUpgrade: StateFlow<EquipItem?> = _pendingUpgrade.asStateFlow()

    init {
        viewModelScope.launch {
            character.ensureBuiltinData()
            _catalog.value = catalogLoader.load()
            _achievements.value = achievementLoader.load()
            val today = clock.today().toEpochDay().toInt()
            character.observeChest(today).collect { _chest.value = it }
            checkAchievements()
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
            _message.value = when (val result = character.upgrade(item.id, upgradeCost(item))) {
                UpgradeResult.Success -> "Улучшено до +${upgradeLevel(item) + 1}"
                UpgradeResult.NotEnoughCoins -> "Не хватает монет"
                is UpgradeResult.NotEnoughMaterials ->
                    "Не хватает материалов: " + result.missing.entries.joinToString { "${it.key.title} ×${it.value}" }
                UpgradeResult.NotForSale -> "Улучшать нечего"
            }
        }
    }

    /** Текущий уровень прокачки предмета (для кнопки и бейджа). */
    fun upgradeLevel(item: EquipItem): Int =
        state.value.owned.firstOrNull { it.itemId == item.id }?.upgradeLevel ?: 0

    /** Сколько материалов нужно на следующий апгрейд: для подписи кнопки. */
    fun nextUpgradeCost(item: EquipItem): Map<MaterialType, Int> {
        val owned = state.value.owned.firstOrNull { it.itemId == item.id } ?: return emptyMap()
        if (owned.upgradeLevel >= MAX_UPGRADE_LEVEL) return emptyMap()
        return MaterialCost.forRarity(item.rarity)
    }

    // --- ежедневный сундук -------------------------------------------------

    fun openChest() {
        viewModelScope.launch {
            _message.value = when (val result = openChestUseCase()) {
                is ChestResult.Opened -> {
                    val reward = result.reward
                    val parts = buildList {
                        add("+${reward.coins} монет")
                        reward.materials.forEach { add("${it.type.title} ×${it.amount}") }
                        reward.itemId?.let { add("предмет!") }
                    }
                    "Сундук: " + parts.joinToString(", ")
                }
                ChestResult.AlreadyOpened -> "Сундук уже открыт сегодня"
                ChestResult.NoReward -> "Сундук пуст"
            }
        }
    }

    // --- достижения ---------------------------------------------------------

    /** Полная сверка условий. Вызывается после каждого начисления из других экранов. */
    fun checkAchievements() {
        viewModelScope.launch {
            val unlocked = refreshAchievementsUseCase()
            if (unlocked.isNotEmpty()) {
                _message.value = "Достижение: " + unlocked.joinToString { it.definition.title }
            }
        }
    }

    fun consumeMessage() { _message.value = null }

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

    private fun currentLayers(): List<CharacterLayer> {
        val items = state.value.equipped.values.toList()
        val hidden = items.flatMap { it.hides }.toSet()
        val tints = _catalog.value.paletteTints

        return items
            .filterNot { it.slot in hidden }
            .sortedBy { it.layer }
            .map { item ->
                CharacterLayer(
                    slot = item.slot,
                    color = Color(tints[item.paletteId] ?: DEFAULT_TINT),
                    label = item.slot.title,
                    frames = item.frames
                )
            }
    }

    private fun portraitName(): String =
        "ashwake-" + state.value.profile.name.lowercase().replace(' ', '-') +
            "-" + System.currentTimeMillis()

    private companion object {
        const val UPGRADE_COST_SHARE = 0.3f
        const val BASE_UPGRADE_COST = 200
        const val MIN_UPGRADE_COST = 50
        const val MAX_UPGRADE_LEVEL = 5

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
