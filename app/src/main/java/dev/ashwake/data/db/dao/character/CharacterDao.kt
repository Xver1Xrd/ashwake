package dev.ashwake.data.db.dao.character

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import dev.ashwake.data.db.entity.character.AppearancePresetEntity
import dev.ashwake.data.db.entity.character.AppearancePresetItemEntity
import dev.ashwake.data.db.entity.character.CharacterProfileEntity
import dev.ashwake.data.db.entity.character.CharacterStatEntity
import dev.ashwake.data.db.entity.character.EquippedItemEntity
import dev.ashwake.data.db.entity.character.LedgerTransactionEntity
import dev.ashwake.data.db.entity.character.OwnedItemEntity
import dev.ashwake.data.db.entity.character.StatEventEntity
import dev.ashwake.data.db.entity.character.UserRewardEntity
import dev.ashwake.data.db.entity.character.UserRewardRedemptionEntity
import dev.ashwake.data.db.entity.character.WalletEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterDao {

    // --- профиль -----------------------------------------------------------

    @Query("SELECT * FROM character_profile WHERE id = 1")
    fun observeProfile(): Flow<CharacterProfileEntity?>

    @Query("SELECT * FROM character_profile WHERE id = 1")
    suspend fun profile(): CharacterProfileEntity?

    @Upsert
    suspend fun upsertProfile(profile: CharacterProfileEntity)

    // --- владение и экипировка --------------------------------------------

    @Query("SELECT * FROM owned_items ORDER BY acquiredAt DESC")
    fun observeOwned(): Flow<List<OwnedItemEntity>>

    @Query("SELECT * FROM owned_items WHERE itemId = :itemId")
    suspend fun owned(itemId: String): OwnedItemEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOwned(item: OwnedItemEntity): Long

    @Query("UPDATE owned_items SET upgradeLevel = :level WHERE itemId = :itemId")
    suspend fun setUpgradeLevel(itemId: String, level: Int)

    @Query("SELECT * FROM equipped_items")
    fun observeEquipped(): Flow<List<EquippedItemEntity>>

    @Query("SELECT * FROM equipped_items")
    suspend fun equipped(): List<EquippedItemEntity>

    @Upsert
    suspend fun equip(item: EquippedItemEntity)

    @Query("DELETE FROM equipped_items WHERE slot = :slot")
    suspend fun unequip(slot: String)

    @Query("DELETE FROM equipped_items")
    suspend fun unequipAll()

    // --- пресеты внешнего вида --------------------------------------------

    @Query("SELECT * FROM appearance_presets ORDER BY position")
    fun observePresets(): Flow<List<AppearancePresetEntity>>

    @Upsert
    suspend fun upsertPreset(preset: AppearancePresetEntity): Long

    @Query("SELECT * FROM appearance_preset_items WHERE presetId = :presetId")
    suspend fun presetItems(presetId: Long): List<AppearancePresetItemEntity>

    @Query("DELETE FROM appearance_preset_items WHERE presetId = :presetId")
    suspend fun clearPresetItems(presetId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPresetItems(items: List<AppearancePresetItemEntity>)

    // --- характеристики ----------------------------------------------------

    @Query("SELECT * FROM character_stats")
    fun observeStats(): Flow<List<CharacterStatEntity>>

    @Query("SELECT * FROM character_stats WHERE stat = :stat")
    suspend fun stat(stat: String): CharacterStatEntity?

    @Upsert
    suspend fun upsertStat(stat: CharacterStatEntity)

    @Insert
    suspend fun insertStatEvent(event: StatEventEntity)

    // --- кошелёк и журнал --------------------------------------------------

    @Query("SELECT * FROM wallet WHERE id = 1")
    fun observeWallet(): Flow<WalletEntity?>

    @Query("SELECT * FROM wallet WHERE id = 1")
    suspend fun wallet(): WalletEntity?

    @Upsert
    suspend fun upsertWallet(wallet: WalletEntity)

    @Insert
    suspend fun insertTransaction(transaction: LedgerTransactionEntity)

    @Query("SELECT * FROM ledger_transactions ORDER BY at DESC LIMIT :limit")
    fun observeTransactions(limit: Int = 100): Flow<List<LedgerTransactionEntity>>

    /**
     * Сколько по событию начислено на самом деле: сумма начислений и уже
     * сделанных отмен. Журнал только дописывается, поэтому отмена — это не
     * удаление строки, а встречная запись, и «сколько сейчас висит на
     * событии» считается суммой, а не последним значением.
     */
    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM ledger_transactions
        WHERE refId = :refId AND currency = :currency AND source IN (:sources)
        """
    )
    suspend fun netLedgerAmount(refId: String, currency: String, sources: List<String>): Long

    /** То же для очков характеристик, по одной строке на характеристику. */
    @Query(
        """
        SELECT stat AS stat, COALESCE(SUM(points), 0) AS points FROM stat_events
        WHERE refId = :refId AND source IN (:sources)
        GROUP BY stat
        """
    )
    suspend fun netStatPoints(refId: String, sources: List<String>): List<StatPointsSum>

    // --- пользовательские награды -----------------------------------------

    @Query("SELECT * FROM user_rewards WHERE archived = 0 ORDER BY cost")
    fun observeUserRewards(): Flow<List<UserRewardEntity>>

    @Upsert
    suspend fun upsertUserReward(reward: UserRewardEntity): Long

    @Query("UPDATE user_rewards SET archived = 1 WHERE id = :id")
    suspend fun archiveUserReward(id: Long)

    @Insert
    suspend fun insertRedemption(redemption: UserRewardRedemptionEntity)
}

/** Строка ответа [CharacterDao.netStatPoints]: характеристика и сумма очков по ней. */
data class StatPointsSum(
    val stat: String,
    val points: Int
)
