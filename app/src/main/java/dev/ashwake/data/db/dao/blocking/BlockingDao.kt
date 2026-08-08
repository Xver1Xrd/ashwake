package dev.ashwake.data.db.dao.blocking

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import dev.ashwake.data.db.entity.blocking.BlockedAppEntity
import dev.ashwake.data.db.entity.blocking.BlockingRuleEntity
import dev.ashwake.data.db.entity.blocking.BypassLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockingDao {

    @Query("SELECT * FROM blocking_rules ORDER BY id")
    fun observeRules(): Flow<List<BlockingRuleEntity>>

    @Query("SELECT * FROM blocking_rules WHERE enabled = 1")
    suspend fun enabledRules(): List<BlockingRuleEntity>

    @Upsert
    suspend fun upsertRule(rule: BlockingRuleEntity): Long

    @Query("DELETE FROM blocking_rules WHERE id = :id")
    suspend fun deleteRule(id: Long)

    @Query("UPDATE blocking_rules SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("SELECT COUNT(*) FROM blocking_rules WHERE enabled = 1")
    suspend fun enabledCount(): Int

    @Query("SELECT * FROM blocked_apps")
    fun observeApps(): Flow<List<BlockedAppEntity>>

    @Query("SELECT * FROM blocked_apps WHERE ruleId = :ruleId")
    suspend fun appsOf(ruleId: Long): List<BlockedAppEntity>

    @Query("DELETE FROM blocked_apps WHERE ruleId = :ruleId")
    suspend fun clearApps(ruleId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertApps(apps: List<BlockedAppEntity>)

    @Insert
    suspend fun logBypass(entry: BypassLogEntity)

    @Query("SELECT * FROM bypass_log ORDER BY at DESC LIMIT :limit")
    fun observeBypasses(limit: Int = 50): Flow<List<BypassLogEntity>>

    @Query("SELECT COUNT(*) FROM bypass_log WHERE at >= :since")
    suspend fun bypassCountSince(since: Long): Int
}
