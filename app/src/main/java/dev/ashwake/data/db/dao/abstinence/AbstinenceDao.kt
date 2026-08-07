package dev.ashwake.data.db.dao.abstinence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import dev.ashwake.data.db.entity.abstinence.AbstinenceAttemptEntity
import dev.ashwake.data.db.entity.abstinence.AbstinenceEntity
import dev.ashwake.data.db.entity.abstinence.AbstinenceMilestoneEntity
import dev.ashwake.data.db.entity.abstinence.AbstinenceSubstituteEntity
import dev.ashwake.data.db.entity.abstinence.CravingEventEntity
import dev.ashwake.data.db.entity.abstinence.CravingTriggerEntity
import dev.ashwake.data.db.entity.abstinence.RelapseReasonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AbstinenceDao {

    @Query("SELECT * FROM abstinences WHERE (:includeArchived = 1 OR archived = 0) ORDER BY position, id")
    fun observeAll(includeArchived: Int): Flow<List<AbstinenceEntity>>

    @Query("SELECT * FROM abstinences WHERE id = :id")
    fun observeOne(id: Long): Flow<AbstinenceEntity?>

    @Query("SELECT * FROM abstinences WHERE id = :id")
    suspend fun get(id: Long): AbstinenceEntity?

    @Query("SELECT * FROM abstinences WHERE archived = 0")
    suspend fun allActive(): List<AbstinenceEntity>

    @Upsert
    suspend fun upsert(entity: AbstinenceEntity): Long

    @Query("UPDATE abstinences SET archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)

    @Query("DELETE FROM abstinences WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE abstinences SET substanceWarningAck = 1 WHERE id = :id")
    suspend fun acknowledgeWarning(id: Long)

    @Query("SELECT COALESCE(MAX(position), 0) FROM abstinences")
    suspend fun maxPosition(): Int

    // --- попытки -----------------------------------------------------------

    @Query("SELECT * FROM abstinence_attempts ORDER BY startedAt")
    fun observeAttempts(): Flow<List<AbstinenceAttemptEntity>>

    @Query("SELECT * FROM abstinence_attempts WHERE abstinenceId = :id ORDER BY startedAt")
    fun observeAttemptsOf(id: Long): Flow<List<AbstinenceAttemptEntity>>

    @Query("SELECT * FROM abstinence_attempts WHERE abstinenceId = :id ORDER BY startedAt")
    suspend fun attemptsOf(id: Long): List<AbstinenceAttemptEntity>

    @Query("SELECT * FROM abstinence_attempts WHERE abstinenceId = :id AND endedAt IS NULL LIMIT 1")
    suspend fun currentAttempt(id: Long): AbstinenceAttemptEntity?

    @Query("SELECT * FROM abstinence_attempts WHERE abstinenceId = :id ORDER BY startedAt DESC LIMIT 1")
    suspend fun lastAttempt(id: Long): AbstinenceAttemptEntity?

    @Query("SELECT COALESCE(MAX(ordinal), 0) FROM abstinence_attempts WHERE abstinenceId = :id")
    suspend fun maxOrdinal(id: Long): Int

    @Insert
    suspend fun insertAttempt(attempt: AbstinenceAttemptEntity): Long

    @Update
    suspend fun updateAttempt(attempt: AbstinenceAttemptEntity)

    @Query("DELETE FROM abstinence_attempts WHERE id = :id")
    suspend fun deleteAttempt(id: Long)

    // --- вехи --------------------------------------------------------------

    @Query("SELECT * FROM abstinence_milestones ORDER BY days")
    fun observeMilestones(): Flow<List<AbstinenceMilestoneEntity>>

    @Query("SELECT * FROM abstinence_milestones WHERE abstinenceId = :id ORDER BY days")
    suspend fun milestonesOf(id: Long): List<AbstinenceMilestoneEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMilestones(milestones: List<AbstinenceMilestoneEntity>)

    @Upsert
    suspend fun upsertMilestone(milestone: AbstinenceMilestoneEntity): Long

    @Query("DELETE FROM abstinence_milestones WHERE id = :id")
    suspend fun deleteMilestone(id: Long)

    @Query("UPDATE abstinence_milestones SET reachedAt = :at, notified = 1 WHERE id = :id")
    suspend fun markMilestoneReached(id: Long, at: Long)

    /** После срыва вехи текущей попытки начинают отсчёт заново. */
    @Query("UPDATE abstinence_milestones SET reachedAt = NULL, notified = 0 WHERE abstinenceId = :id")
    suspend fun resetMilestones(id: Long)

    // --- тяга --------------------------------------------------------------

    @Query("SELECT * FROM craving_events WHERE abstinenceId = :id ORDER BY at DESC")
    fun observeCravings(id: Long): Flow<List<CravingEventEntity>>

    @Insert
    suspend fun insertCraving(event: CravingEventEntity): Long

    @Update
    suspend fun updateCraving(event: CravingEventEntity)

    /** Точечное обновление исхода: заметка не стирается, если новую не передали. */
    @Query(
        """
        UPDATE craving_events
        SET resisted = :resisted,
            durationSeconds = :durationSeconds,
            note = COALESCE(:note, note)
        WHERE id = :id
        """
    )
    suspend fun updateCravingOutcome(
        id: Long,
        resisted: Boolean,
        durationSeconds: Int?,
        note: String?
    )

    @Query("SELECT * FROM craving_triggers ORDER BY builtin DESC, id")
    fun observeTriggers(): Flow<List<CravingTriggerEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTriggers(triggers: List<CravingTriggerEntity>)

    @Query("SELECT COUNT(*) FROM craving_triggers")
    suspend fun triggerCount(): Int

    // --- причины срыва и заместители --------------------------------------

    @Query("SELECT * FROM abstinence_relapse_reasons ORDER BY builtin DESC, id")
    fun observeRelapseReasons(): Flow<List<RelapseReasonEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRelapseReasons(reasons: List<RelapseReasonEntity>)

    @Query("SELECT COUNT(*) FROM abstinence_relapse_reasons")
    suspend fun relapseReasonCount(): Int

    @Query("SELECT * FROM abstinence_substitutes ORDER BY position")
    fun observeSubstitutes(): Flow<List<AbstinenceSubstituteEntity>>

    @Query("DELETE FROM abstinence_substitutes WHERE abstinenceId = :id")
    suspend fun clearSubstitutes(id: Long)

    @Insert
    suspend fun insertSubstitutes(items: List<AbstinenceSubstituteEntity>)
}
