package dev.ashwake.domain.repository.character

import dev.ashwake.core.model.Stat
import dev.ashwake.domain.engine.achievement.AchievementSnapshot
import dev.ashwake.domain.engine.character.ChestReward
import dev.ashwake.domain.engine.character.EquipmentResult
import dev.ashwake.domain.engine.character.StatSource
import dev.ashwake.domain.engine.reward.RewardContext
import dev.ashwake.domain.model.character.AchievementState
import dev.ashwake.domain.model.character.CharacterProfile
import dev.ashwake.domain.model.character.EquipItem
import dev.ashwake.domain.model.character.EquipSlot
import dev.ashwake.domain.model.character.MaterialCount
import dev.ashwake.domain.model.character.MaterialType
import dev.ashwake.domain.model.character.OwnedItem
import dev.ashwake.domain.model.character.StatValue
import dev.ashwake.domain.model.character.Wallet
import kotlinx.coroutines.flow.Flow

/** Всё состояние персонажа одним снимком — то, что рисует главный экран. */
data class CharacterState(
    val profile: CharacterProfile = CharacterProfile(),
    val wallet: Wallet = Wallet(),
    val level: Int = 1,
    val levelProgress: Float = 0f,
    val stats: List<StatValue> = emptyList(),
    val equipped: Map<EquipSlot, EquipItem> = emptyMap(),
    val owned: List<OwnedItem> = emptyList(),
    val equipment: EquipmentResult? = null,
    val materials: List<MaterialCount> = emptyList(),
    val achievements: List<AchievementState> = emptyList()
) {
    val statMap: Map<Stat, Int> get() = stats.associate { it.stat to it.value }
    fun effect(key: String): Float = equipment?.effect(key) ?: 0f
    val materialMap: Map<MaterialType, Int> get() = materials.associate { it.type to it.amount }
}

sealed interface PurchaseResult {
    data object Success : PurchaseResult
    data object NotEnoughCoins : PurchaseResult
    data object AlreadyOwned : PurchaseResult
    data class RequirementsNotMet(val missing: Map<Stat, Int>) : PurchaseResult
    data object NotForSale : PurchaseResult
}

sealed interface UpgradeResult {
    data object Success : UpgradeResult
    data object NotEnoughCoins : UpgradeResult
    data class NotEnoughMaterials(val missing: Map<MaterialType, Int>) : UpgradeResult
    data object NotForSale : UpgradeResult
}

/** Потолок прокачки предмета — один источник истины для репозитория и UI. */
const val MAX_UPGRADE_LEVEL = 10

/** Состояние ежедневного сундука: открыт ли сегодня и что выпало. */
data class ChestState(
    val opened: Boolean,
    val reward: ChestReward? = null
)

interface CharacterRepository {

    fun observeState(): Flow<CharacterState>

    suspend fun state(): CharacterState

    suspend fun updateProfile(profile: CharacterProfile)

    suspend fun equip(itemId: String): Boolean

    suspend fun unequip(slot: EquipSlot)

    suspend fun unequipAll()

    suspend fun buy(itemId: String): PurchaseResult

    /** Апгрейд: монеты плюс материалы по редкости предмета (п. 16.9). */
    suspend fun upgrade(itemId: String, coinCost: Int): UpgradeResult

    /** Три сохранённых образа с мгновенным переключением (п. 16.5.6). */
    suspend fun savePreset(index: Int, name: String)

    suspend fun applyPreset(presetId: Long)

    /**
     * Начисление за событие. Единственный путь, которым монеты попадают в кошелёк:
     * множители экипировки подставляются здесь, а не в вызывающем коде.
     */
    suspend fun grantReward(context: RewardContext, refId: String? = null)

    /** Очки характеристик за поведение. Купить их нельзя (п. 16.5.1). */
    suspend fun grantStatPoints(
        source: StatSource,
        sphere: dev.ashwake.core.model.Sphere? = null,
        refId: String? = null
    )

    suspend fun ensureBuiltinData()

    // --- материалы улучшений (п. 16.9) --------------------------------------

    fun observeMaterials(): Flow<List<MaterialCount>>

    suspend fun grantMaterial(type: MaterialType, amount: Int)

    /** Списание материалов за апгрейд. @return false, если не хватает. */
    suspend fun spendMaterials(required: Map<MaterialType, Int>): Boolean

    // --- достижения --------------------------------------------------------

    fun observeAchievements(): Flow<List<AchievementState>>

    /** Снимок всех счётчиков достижений — строится из базы по требованию. */
    suspend fun achievementSnapshot(): AchievementSnapshot

    /** Отметить достижение открытым. @return false, если уже было открыто. */
    suspend fun unlockAchievement(id: String, at: Long): Boolean

    // --- ежедневный сундук -------------------------------------------------

    /** Открыт ли сундук за день и что в нём лежало. */
    fun observeChest(epochDay: Int): Flow<ChestState>

    suspend fun chestState(epochDay: Int): ChestState

    /** Применить раздачу сундука: монеты, материалы, предмет. */
    suspend fun applyChest(reward: dev.ashwake.domain.engine.character.ChestReward, epochDay: Int)
}
