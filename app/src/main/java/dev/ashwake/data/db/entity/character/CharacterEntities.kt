package dev.ashwake.data.db.entity.character

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Сущности персонажа и экономики (п. 14, 15, 16). */

@Entity(tableName = "character_profile")
data class CharacterProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String = "Безымянный",
    val body: String = "NORMAL",
    val skinToneId: String = "skin_02",
    val hairStyleId: String = "hair_short_void",
    val hairColorId: String = "void",
    val faceId: String? = null,
    val reduceMotion: Boolean = false,
    val decayEnabled: Boolean = true,
    val parallaxEnabled: Boolean = true
)

/**
 * Владение предметом. `itemId` — строковый id из каталога в assets,
 * поэтому пополнение каталога не требует миграции базы (п. 16.6).
 */
@Entity(tableName = "owned_items", indices = [Index(value = ["itemId"], unique = true)])
data class OwnedItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: String,
    val acquiredAt: Long,
    val source: String,
    val upgradeLevel: Int = 0,
    val favorite: Boolean = false
)

/** Слот — первичный ключ: надет максимум один предмет. */
@Entity(tableName = "equipped_items")
data class EquippedItemEntity(
    @PrimaryKey val slot: String,
    val itemId: String
)

@Entity(tableName = "appearance_presets")
data class AppearancePresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int = 0
)

@Entity(tableName = "appearance_preset_items", primaryKeys = ["presetId", "slot"])
data class AppearancePresetItemEntity(
    val presetId: Long,
    val slot: String,
    val itemId: String
)

/** Характеристики. `value` денормализовано: пересчитывать корень на каждый кадр незачем. */
@Entity(tableName = "character_stats")
data class CharacterStatEntity(
    @PrimaryKey val stat: String,
    val points: Long = 0,
    val value: Int = 0
)

/** Журнал начисления очков — из него характеристики можно пересчитать с нуля. */
@Entity(tableName = "stat_events", indices = [Index("at"), Index("stat")])
data class StatEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val stat: String,
    val points: Int,
    val source: String,
    val refId: String? = null
)

@Entity(tableName = "wallet")
data class WalletEntity(
    @PrimaryKey val id: Int = 1,
    val coins: Long = 0,
    val xp: Long = 0,
    val level: Int = 1
)

/**
 * Единый журнал монет и опыта. Пишет только RewardEngine.
 * `multiplierApplied` хранится ради отладки баланса: без него видно начисление,
 * но не видно, почему оно такое.
 */
@Entity(tableName = "ledger_transactions", indices = [Index("at"), Index("source")])
data class LedgerTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val currency: String,
    val amount: Long,
    val source: String,
    val refId: String? = null,
    val multiplierApplied: Float = 1f,
    val balanceAfter: Long,
    val note: String? = null
)

@Entity(tableName = "user_rewards")
data class UserRewardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String? = null,
    val cost: Long,
    val repeatable: Boolean = true,
    val archived: Boolean = false,
    val createdAt: Long
)

@Entity(tableName = "user_reward_redemptions", indices = [Index("rewardId")])
data class UserRewardRedemptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rewardId: Long,
    val at: Long,
    val cost: Long
)
