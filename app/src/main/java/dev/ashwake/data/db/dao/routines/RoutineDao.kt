package dev.ashwake.data.db.dao.routines

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import dev.ashwake.data.db.entity.routines.FocusSessionEntity
import dev.ashwake.data.db.entity.routines.RoutineEntity
import dev.ashwake.data.db.entity.routines.RoutineSessionEntity
import dev.ashwake.data.db.entity.routines.RoutineSessionStepEntity
import dev.ashwake.data.db.entity.routines.RoutineStepEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {

    @Query("SELECT * FROM routines WHERE (:includeArchived = 1 OR archived = 0) ORDER BY position, id")
    fun observeRoutines(includeArchived: Int): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun routine(id: Long): RoutineEntity?

    @Query("SELECT * FROM routines WHERE archived = 0 AND startTime IS NOT NULL AND alarmEnabled = 1")
    suspend fun routinesWithAlarm(): List<RoutineEntity>

    @Upsert
    suspend fun upsertRoutine(routine: RoutineEntity): Long

    @Query("UPDATE routines SET archived = 1 WHERE id = :id")
    suspend fun archiveRoutine(id: Long)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteRoutine(id: Long)

    @Query("SELECT COALESCE(MAX(position), 0) FROM routines")
    suspend fun maxPosition(): Int

    // --- шаги --------------------------------------------------------------

    @Query("SELECT * FROM routine_steps ORDER BY position")
    fun observeSteps(): Flow<List<RoutineStepEntity>>

    @Query("SELECT * FROM routine_steps WHERE routineId = :routineId ORDER BY position")
    suspend fun stepsOf(routineId: Long): List<RoutineStepEntity>

    @Query("DELETE FROM routine_steps WHERE routineId = :routineId")
    suspend fun clearSteps(routineId: Long)

    @Insert
    suspend fun insertSteps(steps: List<RoutineStepEntity>)

    // --- сессии ------------------------------------------------------------

    @Insert
    suspend fun insertSession(session: RoutineSessionEntity): Long

    @Update
    suspend fun updateSession(session: RoutineSessionEntity)

    @Query("SELECT * FROM routine_sessions WHERE id = :id")
    suspend fun session(id: Long): RoutineSessionEntity?

    @Query("SELECT * FROM routine_sessions WHERE routineId = :routineId ORDER BY startedAt DESC LIMIT :limit")
    fun observeSessions(routineId: Long, limit: Int = 20): Flow<List<RoutineSessionEntity>>

    @Query("SELECT * FROM routine_sessions ORDER BY startedAt DESC LIMIT 1")
    suspend fun lastSession(): RoutineSessionEntity?

    @Insert
    suspend fun insertSessionSteps(steps: List<RoutineSessionStepEntity>): List<Long>

    @Update
    suspend fun updateSessionStep(step: RoutineSessionStepEntity)

    @Insert
    suspend fun insertSessionStep(step: RoutineSessionStepEntity): Long

    @Query("SELECT * FROM routine_session_steps WHERE sessionId = :sessionId ORDER BY position")
    suspend fun sessionSteps(sessionId: Long): List<RoutineSessionStepEntity>

    @Query("SELECT * FROM routine_session_steps WHERE sessionId = :sessionId ORDER BY position")
    fun observeSessionSteps(sessionId: Long): Flow<List<RoutineSessionStepEntity>>
}

@Dao
interface FocusDao {

    @Insert
    suspend fun insert(session: FocusSessionEntity): Long

    @Update
    suspend fun update(session: FocusSessionEntity)

    @Query("SELECT * FROM focus_sessions WHERE id = :id")
    suspend fun session(id: Long): FocusSessionEntity?

    @Query("SELECT * FROM focus_sessions WHERE startedAt >= :from ORDER BY startedAt DESC")
    fun observeSince(from: Long): Flow<List<FocusSessionEntity>>

    /** Суммарное время фокуса по задаче — показывается в карточке задачи. */
    @Query(
        """
        SELECT COALESCE(SUM(actualSeconds), 0) FROM focus_sessions
        WHERE taskId = :taskId AND phase = 'WORK'
        """
    )
    suspend fun secondsOnTask(taskId: Long): Long

    @Query("SELECT * FROM focus_sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun unfinished(): FocusSessionEntity?
}
