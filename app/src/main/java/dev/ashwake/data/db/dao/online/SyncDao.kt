package dev.ashwake.data.db.dao.online

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import dev.ashwake.data.db.entity.online.SyncConflictEntity
import dev.ashwake.data.db.entity.online.SyncStateEntity
import dev.ashwake.data.db.entity.online.SyncTombstoneEntity
import dev.ashwake.data.db.entity.tasks.TaskEntity

@Dao
interface SyncDao {
    @Query("UPDATE tasks SET spaceId = :spaceId, globalId = COALESCE(globalId, lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab', abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6)))), syncState = 'PENDING' WHERE spaceId IS NULL")
    suspend fun moveLocalTasksToPersonalSpace(spaceId: String)

    @Query("SELECT * FROM tasks WHERE syncState = 'PENDING' AND globalId IS NOT NULL AND spaceId IS NOT NULL ORDER BY updatedAt LIMIT :limit")
    suspend fun pendingTasks(limit: Int = 100): List<TaskEntity>

    @Query("UPDATE tasks SET syncRevision = :revision, syncState = 'SYNCED' WHERE globalId = :globalId")
    suspend fun markTaskSynced(globalId: String, revision: Long)

    @Query("UPDATE tasks SET syncState = 'CONFLICT' WHERE globalId = :globalId")
    suspend fun markTaskConflict(globalId: String)

    @Query("SELECT * FROM sync_tombstones ORDER BY createdAt LIMIT :limit")
    suspend fun tombstones(limit: Int = 100): List<SyncTombstoneEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTombstone(item: SyncTombstoneEntity)

    @Query("DELETE FROM sync_tombstones WHERE globalId = :globalId")
    suspend fun removeTombstone(globalId: String)

    @Query("SELECT * FROM sync_state WHERE accountId = :accountId")
    suspend fun state(accountId: String): SyncStateEntity?

    @Upsert
    suspend fun saveState(state: SyncStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConflict(conflict: SyncConflictEntity)

    @Query("SELECT * FROM sync_conflicts WHERE globalId = :globalId")
    suspend fun conflict(globalId: String): SyncConflictEntity?

    @Query("DELETE FROM sync_conflicts WHERE globalId = :globalId")
    suspend fun removeConflict(globalId: String)
}
