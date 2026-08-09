package dev.ashwake.data.db.dao.backup

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.ashwake.data.db.entity.abstinence.AbstinenceAttemptEntity
import dev.ashwake.data.db.entity.abstinence.AbstinenceEntity
import dev.ashwake.data.db.entity.character.CharacterStatEntity
import dev.ashwake.data.db.entity.character.EquippedItemEntity
import dev.ashwake.data.db.entity.character.OwnedItemEntity
import dev.ashwake.data.db.entity.character.WalletEntity
import dev.ashwake.data.db.entity.habits.HabitEntity
import dev.ashwake.data.db.entity.habits.HabitEntryEntity
import dev.ashwake.data.db.entity.ritual.DailyReviewEntity
import dev.ashwake.data.db.entity.tasks.TaskEntity

/**
 * Операции восстановления из архива.
 *
 * Вынесены в отдельный DAO намеренно: `DELETE FROM` без условия — опасный
 * запрос, и ему не место среди обычных методов работы с задачами или
 * привычками, где его можно вызвать по ошибке. Здесь он окружён
 * единственным сценарием, в котором допустим.
 */
@Dao
interface BackupDao {

    // --- очистка ------------------------------------------------------------
    //
    // Порядок важен: сначала то, что ссылается, потом то, на что ссылаются.
    // Внешние ключи с CASCADE сняли бы часть работы, но полагаться на них
    // при полной замене данных — значит зависеть от того, какой ON DELETE
    // окажется у следующей добавленной таблицы.

    @Query("DELETE FROM habit_entries") suspend fun clearHabitEntries()
    @Query("DELETE FROM habit_freezes") suspend fun clearHabitFreezes()
    @Query("DELETE FROM habit_pauses") suspend fun clearHabitPauses()
    @Query("DELETE FROM habit_anchors") suspend fun clearHabitAnchors()
    @Query("DELETE FROM habits") suspend fun clearHabits()

    @Query("DELETE FROM task_tag_cross_ref") suspend fun clearTaskTags()
    @Query("DELETE FROM task_postponements") suspend fun clearPostponements()
    @Query("DELETE FROM tasks") suspend fun clearTasks()

    @Query("DELETE FROM abstinence_attempts") suspend fun clearAttempts()
    @Query("DELETE FROM craving_events") suspend fun clearCravings()
    @Query("DELETE FROM abstinence_milestones") suspend fun clearMilestones()
    @Query("DELETE FROM abstinences") suspend fun clearAbstinences()

    @Query("DELETE FROM daily_review_top_tasks") suspend fun clearReviewTopTasks()
    @Query("DELETE FROM daily_reviews") suspend fun clearReviews()

    @Query("DELETE FROM equipped_items") suspend fun clearEquipped()
    @Query("DELETE FROM owned_items") suspend fun clearOwned()
    @Query("DELETE FROM character_stats") suspend fun clearStats()
    @Query("DELETE FROM ledger_transactions") suspend fun clearLedger()

    // --- вставка ------------------------------------------------------------
    //
    // REPLACE, а не IGNORE: архив — источник истины, и если запись с таким
    // id уже есть, побеждает архив, иначе восстановление отдало бы смесь.

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(items: List<TaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabits(items: List<HabitEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabitEntries(items: List<HabitEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAbstinences(items: List<AbstinenceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttempts(items: List<AbstinenceAttemptEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReviews(items: List<DailyReviewEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOwned(items: List<OwnedItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEquipped(items: List<EquippedItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStats(items: List<CharacterStatEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWallet(wallet: WalletEntity)
}
