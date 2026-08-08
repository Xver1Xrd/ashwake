package dev.ashwake.domain.repository.routines

import dev.ashwake.domain.model.focus.FocusMode
import dev.ashwake.domain.model.focus.FocusPhase
import dev.ashwake.domain.model.focus.FocusSession
import dev.ashwake.domain.model.focus.FocusStats
import dev.ashwake.domain.model.routines.Routine
import dev.ashwake.domain.model.routines.RoutineSession
import dev.ashwake.domain.model.routines.RoutineSessionStep
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface RoutineRepository {

    fun observeRoutines(includeArchived: Boolean = false): Flow<List<Routine>>

    suspend fun getRoutine(id: Long): Routine?

    suspend fun upsertRoutine(routine: Routine): Long

    suspend fun archiveRoutine(id: Long)

    suspend fun deleteRoutine(id: Long)

    suspend fun routinesWithAlarm(): List<Routine>

    /** Начинает сессию и фиксирует снимок шагов. @return id сессии. */
    suspend fun startSession(routineId: Long): Long

    suspend fun updateSessionStep(step: RoutineSessionStep)

    suspend fun addSessionStep(sessionId: Long, title: String, durationSeconds: Int): Long

    suspend fun finishSession(sessionId: Long, completed: Boolean, endedAt: Instant)

    suspend fun getSession(sessionId: Long): RoutineSession?

    fun observeSessions(routineId: Long): Flow<List<RoutineSession>>
}

interface FocusRepository {

    suspend fun start(
        mode: FocusMode,
        phase: FocusPhase,
        plannedSeconds: Int,
        taskId: Long?,
        projectId: Long?,
        whiteNoiseId: String?
    ): Long

    suspend fun finish(sessionId: Long, actualSeconds: Int, completed: Boolean)

    suspend fun addInterruption(sessionId: Long)

    suspend fun secondsOnTask(taskId: Long): Long

    /** Статистика по дням недели и проектам (п. 8). */
    fun observeStats(sinceDays: Long = 30): Flow<FocusStats>

    fun observeRecent(sinceDays: Long = 7): Flow<List<FocusSession>>
}
