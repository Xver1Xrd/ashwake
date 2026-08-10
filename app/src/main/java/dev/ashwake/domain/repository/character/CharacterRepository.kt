package dev.ashwake.domain.repository.character

import dev.ashwake.core.model.Stat
import dev.ashwake.domain.engine.character.EquipmentResult
import dev.ashwake.domain.engine.character.StatSource
import dev.ashwake.domain.engine.reward.RewardContext
import dev.ashwake.domain.model.character.CharacterProfile
import dev.ashwake.domain.model.character.EquipItem
import dev.ashwake.domain.model.character.EquipSlot
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
    val equipment: EquipmentResult? = null
) {
    val statMap: Map<Stat, Int> get() = stats.associate { it.stat to it.value }
    fun effect(key: String): Float = equipment?.effect(key) ?: 0f
}

sealed interface PurchaseResult {
    data object Success : PurchaseResult
    data object NotEnoughCoins : PurchaseResult
    data object AlreadyOwned : PurchaseResult
    data class RequirementsNotMet(val missing: Map<Stat, Int>) : PurchaseResult
    data object NotForSale : PurchaseResult
}

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

    suspend fun upgrade(itemId: String, cost: Int): PurchaseResult

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
}
