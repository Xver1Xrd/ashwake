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

/** Потолок улучшения предмета: уровни считаются с нуля, то есть 0..9. */
const val MAX_UPGRADE_LEVEL = 10

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
    /** Инвентарь материалов улучшений (п. 16.9). */
    val materials: List<MaterialCount> = emptyList(),
    val achievements: List<AchievementState> = emptyList()
) {
    val statMap: Map<Stat, Int> get() = stats.associate { it.stat to it.value }
    fun effect(key: String): Float = equipment?.effect(key) ?: 0f
    val materialMap: Map<MaterialType, Int>
        get() = materials.associate { it.type to it.amount }
}

sealed interface PurchaseResult {
    data object Success : PurchaseResult
    data object NotEnoughCoins : PurchaseResult
    data object AlreadyOwned : PurchaseResult
    data class RequirementsNotMet(val missing: Map<Stat, Int>) : PurchaseResult
    data object NotForSale : PurchaseResult
}

/**
 * Результат улучшения. Отдельный от покупки тип: апгрейд стоит не только
 * монет, но и материалов, и это должно быть видно в ответе, а не в тосте.
 */
sealed interface UpgradeResult {
    data object Success : UpgradeResult
    data object NotEnoughCoins : UpgradeResult
    /** Не хватает материалов; карта — сколько какого не хватает. */
    data class NotEnoughMaterials(val missing: Map<MaterialType, Int>) : UpgradeResult
    data object NotForSale : UpgradeResult
}

/** Состояние ежедневного сундука за конкретный день (epochDay). */
data class ChestState(
    val opened: Boolean = false,
    val reward: ChestReward? = null
)

/**
 * Что именно отменяется.
 *
 * Одно событие начисляет сразу несколькими источниками: задача даёт монеты
 * за закрытие и очки за срок и за разбор завала. Перечислять их в вызывающем
 * коде значило бы держать знание о внутренностях начисления в трёх местах,
 * поэтому наружу выходит только вид события.
 */
enum class RewardScope { TASK, HABIT }

interface CharacterRepository {

    fun observeState(): Flow<CharacterState>

    suspend fun state(): CharacterState

    suspend fun updateProfile(profile: CharacterProfile)

    suspend fun equip(itemId: String): Boolean

    suspend fun unequip(slot: EquipSlot)

    suspend fun unequipAll()

    suspend fun buy(itemId: String): PurchaseResult

    suspend fun upgrade(itemId: String, coinCost: Int): UpgradeResult

    /** Три сохранённых образа с мгновенным переключением (п. 16.5.6). */
    suspend fun savePreset(index: Int, name: String)

    suspend fun applyPreset(presetId: Long)

    /**
     * Начисление за событие. Единственный путь, которым монеты попадают в кошелёк:
     * множители экипировки подставляются здесь, а не в вызывающем коде.
     */
    suspend fun grantReward(context: RewardContext, refId: String? = null)

    /**
     * Отмена начисления за отменённое событие.
     *
     * Нужна там, где действие обратимо: задачу можно вернуть в работу,
     * отметку привычки — снять. Без отмены обратимое действие превращается
     * в бесконечный источник монет — достаточно нажимать чекбокс.
     *
     * Отменяется ровно то, что по этому событию сейчас начислено: журнал
     * только дописывается, поэтому повторный вызов уже ничего не снимет.
     *
     * @param refId тот же идентификатор события, с которым шло начисление
     */
    suspend fun revokeReward(scope: RewardScope, refId: String)

    /** Очки характеристик за поведение. Купить их нельзя (п. 16.5.1). */
    suspend fun grantStatPoints(
        source: StatSource,
        sphere: dev.ashwake.core.model.Sphere? = null,
        refId: String? = null
    )

    suspend fun ensureBuiltinData()

    // --- материалы улучшений (п. 16.9) -------------------------------------

    fun observeMaterials(): Flow<List<MaterialCount>>

    suspend fun grantMaterial(type: MaterialType, amount: Int)

    /** Списывает материалы, если хватает всех сразу. @return удалось ли. */
    suspend fun spendMaterials(required: Map<MaterialType, Int>): Boolean

    // --- достижения (п. 16.9) ----------------------------------------------

    fun observeAchievements(): Flow<List<AchievementState>>

    /** Текущие счётчики условий: то, что сравнивает движок достижений. */
    suspend fun achievementSnapshot(): AchievementSnapshot

    /** @return false, если уже открыто (повторная раздача исключена). */
    suspend fun unlockAchievement(id: String, at: Long): Boolean

    // --- ежедневный сундук (п. 16.9) ---------------------------------------

    fun observeChest(epochDay: Int): Flow<ChestState>

    suspend fun chestState(epochDay: Int): ChestState

    /** Открытие сундука: начисляет награду и помечает день. */
    suspend fun applyChest(reward: ChestReward, epochDay: Int)
}
