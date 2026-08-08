package dev.ashwake.data.db.dao.timebox

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import dev.ashwake.data.db.entity.timebox.TimeboxBlockEntity
import dev.ashwake.data.db.entity.timebox.TimeboxDayEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeboxDao {

    @Query("SELECT * FROM timebox_days WHERE date = :date")
    fun observeDay(date: Int): Flow<TimeboxDayEntity?>

    @Query("SELECT * FROM timebox_days WHERE date = :date")
    suspend fun day(date: Int): TimeboxDayEntity?

    @Upsert
    suspend fun upsertDay(day: TimeboxDayEntity)

    @Query("SELECT * FROM timebox_blocks WHERE date = :date ORDER BY startMinute")
    fun observeBlocks(date: Int): Flow<List<TimeboxBlockEntity>>

    @Query("SELECT * FROM timebox_blocks WHERE date = :date ORDER BY startMinute")
    suspend fun blocks(date: Int): List<TimeboxBlockEntity>

    @Insert
    suspend fun insertBlocks(blocks: List<TimeboxBlockEntity>)

    @Update
    suspend fun updateBlock(block: TimeboxBlockEntity)

    @Query("DELETE FROM timebox_blocks WHERE id = :id")
    suspend fun deleteBlock(id: Long)

    /**
     * Стереть только автоматически расставленное.
     * Ручные и закреплённые блоки переживают пересборку дня — иначе
     * кнопка «разложить» стирала бы то, что человек поправил руками.
     */
    @Query("DELETE FROM timebox_blocks WHERE date = :date AND pinned = 0 AND createdBy = 'AUTO'")
    suspend fun clearAutoBlocks(date: Int)

    @Query("DELETE FROM timebox_blocks WHERE date = :date")
    suspend fun clearAll(date: Int)
}
