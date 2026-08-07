package dev.ashwake.data.db.dao.habits

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import dev.ashwake.data.db.entity.habits.HabitAnchorEntity
import dev.ashwake.data.db.entity.habits.HabitEntity
import dev.ashwake.data.db.entity.habits.HabitEntryEntity
import dev.ashwake.data.db.entity.habits.HabitFreezeEntity
import dev.ashwake.data.db.entity.habits.HabitPauseEntity
import dev.ashwake.data.db.entity.habits.HabitSkipReasonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {

    @Query("SELECT * FROM habits WHERE (:includeArchived = 1 OR archived = 0) ORDER BY position, id")
    fun observeHabits(includeArchived: Int): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE id = :id")
    fun observeHabit(id: Long): Flow<HabitEntity?>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getHabit(id: Long): HabitEntity?

    @Query("SELECT * FROM habits WHERE archived = 0 AND reminderTime IS NOT NULL")
    suspend fun habitsWithReminders(): List<HabitEntity>

    @Upsert
    suspend fun upsert(habit: HabitEntity): Long

    @Query("UPDATE habits SET archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COALESCE(MAX(position), 0) FROM habits")
    suspend fun maxPosition(): Int

    // --- отметки -----------------------------------------------------------

    /**
     * Все отметки всех привычек за окно. Одним запросом на весь экран:
     * запрос на привычку превращал бы список из десяти карточек в десять
     * подписок и десять пересчётов на каждое изменение.
     */
    @Query("SELECT * FROM habit_entries WHERE date BETWEEN :from AND :to")
    fun observeEntriesInRange(from: Int, to: Int): Flow<List<HabitEntryEntity>>

    @Query("SELECT * FROM habit_entries WHERE habitId = :habitId AND date BETWEEN :from AND :to")
    fun observeEntriesOf(habitId: Long, from: Int, to: Int): Flow<List<HabitEntryEntity>>

    @Query("SELECT * FROM habit_entries WHERE habitId = :habitId AND date = :date")
    suspend fun entryAt(habitId: Long, date: Int): HabitEntryEntity?

    @Upsert
    suspend fun upsertEntry(entry: HabitEntryEntity): Long

    @Update
    suspend fun updateEntry(entry: HabitEntryEntity)

    @Query("DELETE FROM habit_entries WHERE habitId = :habitId AND date = :date")
    suspend fun deleteEntry(habitId: Long, date: Int)

    // --- заморозки ---------------------------------------------------------

    @Query("SELECT * FROM habit_freezes")
    fun observeFreezes(): Flow<List<HabitFreezeEntity>>

    @Query("SELECT COUNT(*) FROM habit_freezes WHERE habitId = :habitId AND monthKey = :monthKey")
    suspend fun freezesSpent(habitId: Long, monthKey: Int): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFreeze(freeze: HabitFreezeEntity): Long

    @Query("DELETE FROM habit_freezes WHERE habitId = :habitId AND date = :date")
    suspend fun deleteFreeze(habitId: Long, date: Int)

    // --- паузы -------------------------------------------------------------

    @Query("SELECT * FROM habit_pauses")
    fun observePauses(): Flow<List<HabitPauseEntity>>

    @Upsert
    suspend fun upsertPause(pause: HabitPauseEntity): Long

    @Query("DELETE FROM habit_pauses WHERE id = :id")
    suspend fun deletePause(id: Long)

    @Query("UPDATE habit_pauses SET endDate = :endDate WHERE endDate IS NULL")
    suspend fun closeOpenPauses(endDate: Int)

    // --- якоря и причины пропуска -----------------------------------------

    @Query("SELECT * FROM habit_anchors")
    fun observeAnchors(): Flow<List<HabitAnchorEntity>>

    @Query("DELETE FROM habit_anchors WHERE habitId = :habitId")
    suspend fun clearAnchors(habitId: Long)

    @Insert
    suspend fun insertAnchors(anchors: List<HabitAnchorEntity>)

    @Query("SELECT * FROM habit_skip_reasons ORDER BY builtin DESC, id")
    fun observeSkipReasons(): Flow<List<HabitSkipReasonEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSkipReasons(reasons: List<HabitSkipReasonEntity>)

    @Query("SELECT COUNT(*) FROM habit_skip_reasons")
    suspend fun skipReasonCount(): Int
}
