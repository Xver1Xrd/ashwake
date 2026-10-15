package dev.ashwake.data.db.entity.online

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Удалённые локально записи нельзя терять до подтверждения сервера. */
@Entity(tableName = "sync_tombstones", indices = [Index("spaceId")])
data class SyncTombstoneEntity(
    @PrimaryKey val globalId: String,
    val spaceId: String,
    val recordType: String,
    val baseRevision: Long,
    val createdAt: Long
)
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val accountId: String,
    val cursor: Long = 0,
    val lastSuccessAt: Long? = null
)

/** Шифротекст конфликта сохраняется до явного решения, ничего не затирается молча. */
@Entity(tableName = "sync_conflicts", indices = [Index("spaceId")])
data class SyncConflictEntity(
    @PrimaryKey val globalId: String,
    val spaceId: String,
    val localRevision: Long,
    val remoteRevision: Long,
    val remoteCiphertext: String,
    val createdAt: Long
)
