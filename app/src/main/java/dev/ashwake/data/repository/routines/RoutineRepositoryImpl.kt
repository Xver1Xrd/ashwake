package dev.ashwake.data.repository.routines

import androidx.room.withTransaction
import dev.ashwake.core.time.AppClock
import dev.ashwake.data.db.AshwakeDatabase
import dev.ashwake.data.db.dao.routines.FocusDao
import dev.ashwake.data.db.dao.routines.RoutineDao
import dev.ashwake.data.db.entity.routines.FocusSessionEntity
import dev.ashwake.data.db.entity.routines.RoutineEntity
import dev.ashwake.data.db.entity.routines.RoutineSessionEntity
import dev.ashwake.data.db.entity.routines.RoutineSessionStepEntity
import dev.ashwake.data.db.entity.routines.RoutineStepEntity
import dev.ashwake.data.db.mapper.tasks.toLocalTime
import dev.ashwake.data.db.mapper.tasks.toMinuteOfDay
import dev.ashwake.domain.engine.routines.RoutineProgressCalculator
import dev.ashwake.domain.model.focus.FocusMode
import dev.ashwake.domain.model.focus.FocusPhase
import dev.ashwake.domain.model.focus.FocusSession
import dev.ashwake.domain.model.focus.FocusStats
import dev.ashwake.domain.model.routines.Routine
import dev.ashwake.domain.model.routines.RoutineSession
import dev.ashwake.domain.model.routines.RoutineSessionStep
import dev.ashwake.domain.model.routines.RoutineStep
import dev.ashwake.domain.repository.routines.FocusRepository
import dev.ashwake.domain.repository.routines.RoutineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoutineRepositoryImpl @Inject constructor(
    private val db: AshwakeDatabase,
    private val dao: RoutineDao,
    private val calculator: RoutineProgressCalculator,
    private val clock: AppClock
) : RoutineRepository {

    override fun observeRoutines(includeArchived: Boolean): Flow<List<Routine>> =
        combine(
            dao.observeRoutines(if (includeArchived) 1 else 0),
            dao.observeSteps()
        ) { routines, steps ->
            val byRoutine = steps.groupBy { it.routineId }
            routines.map { entity ->
                entity.toDomain(byRoutine[entity.id].orEmpty().map { it.toDomain() })
            }
        }

    override suspend fun getRoutine(id: Long): Routine? {
        val entity = dao.routine(id) ?: return null
        return entity.toDomain(dao.stepsOf(id).map { it.toDomain() })
    }

    override suspend fun upsertRoutine(routine: Routine): Long = db.withTransaction {
        val positioned =
            if (routine.id == 0L) routine.copy(position = dao.maxPosition() + 1) else routine
        val id = dao.upsertRoutine(positioned.toEntity())
            .let { if (it == -1L) routine.id else it }

        // Шаги переписываются целиком: сравнивать по одному дороже, чем вставить заново
        dao.clearSteps(id)
        dao.insertSteps(
            routine.steps.mapIndexed { index, step ->
                step.copy(routineId = id, position = index).toEntity()
            }
        )
        id
    }

    override suspend fun archiveRoutine(id: Long) = dao.archiveRoutine(id)

    override suspend fun deleteRoutine(id: Long) = dao.deleteRoutine(id)

    override suspend fun routinesWithAlarm(): List<Routine> =
        dao.routinesWithAlarm().map { it.toDomain(dao.stepsOf(it.id).map { step -> step.toDomain() }) }

    override suspend fun startSession(routineId: Long): Long = db.withTransaction {
        val routine = getRoutine(routineId) ?: return@withTransaction 0L
        val snapshot = calculator.snapshotSteps(routine)

        val sessionId = dao.insertSession(
            RoutineSessionEntity(
                routineId = routineId,
                startedAt = clock.now().toEpochMilli(),
                plannedSeconds = routine.plannedSeconds
            )
        )
        dao.insertSessionSteps(snapshot.map { it.copy(sessionId = sessionId).toEntity() })
        sessionId
    }

    override suspend fun updateSessionStep(step: RoutineSessionStep) {
        dao.updateSessionStep(step.toEntity())
    }

    override suspend fun addSessionStep(
        sessionId: Long,
        title: String,
        durationSeconds: Int
    ): Long {
        val existing = dao.sessionSteps(sessionId)
        return dao.insertSessionStep(
            RoutineSessionStepEntity(
                sessionId = sessionId,
                title = title,
                position = existing.size,
                plannedSeconds = durationSeconds,
                addedOnTheFly = true
            )
        )
    }

    override suspend fun finishSession(sessionId: Long, completed: Boolean, endedAt: Instant) {
        db.withTransaction {
            val session = dao.session(sessionId) ?: return@withTransaction
            val steps = dao.sessionSteps(sessionId)
            dao.updateSession(
                session.copy(
                    endedAt = endedAt.toEpochMilli(),
                    completed = completed,
                    actualSeconds = steps.sumOf { it.actualSeconds },
                    skippedSteps = steps.count { it.skipped }
                )
            )
        }
    }

    override suspend fun getSession(sessionId: Long): RoutineSession? {
        val session = dao.session(sessionId) ?: return null
        return session.toDomain(dao.sessionSteps(sessionId).map { it.toDomain() })
    }

    override fun observeSessions(routineId: Long): Flow<List<RoutineSession>> =
        dao.observeSessions(routineId).map { list -> list.map { it.toDomain(emptyList()) } }
}

@Singleton
class FocusRepositoryImpl @Inject constructor(
    private val dao: FocusDao,
    private val clock: AppClock
) : FocusRepository {

    override suspend fun start(
        mode: FocusMode,
        phase: FocusPhase,
        plannedSeconds: Int,
        taskId: Long?,
        projectId: Long?,
        whiteNoiseId: String?
    ): Long = dao.insert(
        FocusSessionEntity(
            taskId = taskId,
            projectId = projectId,
            mode = mode.name,
            phase = phase.name,
            startedAt = clock.now().toEpochMilli(),
            plannedSeconds = plannedSeconds,
            whiteNoiseId = whiteNoiseId
        )
    )

    override suspend fun finish(sessionId: Long, actualSeconds: Int, completed: Boolean) {
        val session = dao.session(sessionId) ?: return
        dao.update(
            session.copy(
                endedAt = clock.now().toEpochMilli(),
                actualSeconds = actualSeconds,
                completed = completed
            )
        )
    }

    override suspend fun addInterruption(sessionId: Long) {
        val session = dao.session(sessionId) ?: return
        dao.update(session.copy(interruptions = session.interruptions + 1))
    }

    override suspend fun secondsOnTask(taskId: Long): Long = dao.secondsOnTask(taskId)

    /**
     * Статистика считается на месте из сессий за окно.
     * Отдельной таблицы-агрегата нет: месяц фокуса — это сотни строк,
     * их группировка дешевле, чем поддержание кэша в согласии с историей.
     */
    override fun observeStats(sinceDays: Long): Flow<FocusStats> =
        dao.observeSince(sinceMillis(sinceDays)).map { sessions ->
            val work = sessions.filter { it.phase == FocusPhase.WORK.name }
            FocusStats(
                totalSeconds = work.sumOf { it.actualSeconds.toLong() },
                sessions = work.size,
                byWeekday = work.groupBy { weekdayOf(it.startedAt) }
                    .mapValues { (_, list) -> list.sumOf { it.actualSeconds.toLong() } },
                byProject = work.groupBy { it.projectId }
                    .mapValues { (_, list) -> list.sumOf { it.actualSeconds.toLong() } },
                byTask = work.groupBy { it.taskId }
                    .mapValues { (_, list) -> list.sumOf { it.actualSeconds.toLong() } }
            )
        }

    override fun observeRecent(sinceDays: Long): Flow<List<FocusSession>> =
        dao.observeSince(sinceMillis(sinceDays)).map { list -> list.map { it.toDomain() } }

    private fun sinceMillis(days: Long): Long =
        clock.now().minusSeconds(days * 24 * 60 * 60).toEpochMilli()

    private fun weekdayOf(millis: Long): Int =
        Instant.ofEpochMilli(millis).atZone(clock.zone() ?: ZoneId.systemDefault())
            .dayOfWeek.value
}

// --- мапперы -------------------------------------------------------------

private fun RoutineEntity.toDomain(steps: List<RoutineStep>) = Routine(
    id = id,
    name = name,
    icon = icon,
    startTime = startTime?.toLocalTime(),
    alarmEnabled = alarmEnabled,
    ttsEnabled = ttsEnabled,
    vibrateOnStep = vibrateOnStep,
    position = position,
    archived = archived,
    steps = steps.sortedBy { it.position }
)

private fun Routine.toEntity() = RoutineEntity(
    id = id,
    name = name,
    icon = icon,
    startTime = startTime?.toMinuteOfDay(),
    alarmEnabled = alarmEnabled,
    ttsEnabled = ttsEnabled,
    vibrateOnStep = vibrateOnStep,
    position = position,
    archived = archived
)

private fun RoutineStepEntity.toDomain() = RoutineStep(
    id = id, routineId = routineId, title = title,
    durationSeconds = durationSeconds, position = position, note = note, ttsText = ttsText
)

private fun RoutineStep.toEntity() = RoutineStepEntity(
    id = id, routineId = routineId, title = title,
    durationSeconds = durationSeconds, position = position, note = note, ttsText = ttsText
)

private fun RoutineSessionEntity.toDomain(steps: List<RoutineSessionStep>) = RoutineSession(
    id = id,
    routineId = routineId,
    startedAt = Instant.ofEpochMilli(startedAt),
    endedAt = endedAt?.let { Instant.ofEpochMilli(it) },
    completed = completed,
    plannedSeconds = plannedSeconds,
    actualSeconds = actualSeconds,
    skippedSteps = skippedSteps,
    steps = steps
)

private fun RoutineSessionStepEntity.toDomain() = RoutineSessionStep(
    id = id, sessionId = sessionId, stepId = stepId, title = title, position = position,
    plannedSeconds = plannedSeconds, actualSeconds = actualSeconds,
    skipped = skipped, addedOnTheFly = addedOnTheFly
)

private fun RoutineSessionStep.toEntity() = RoutineSessionStepEntity(
    id = id, sessionId = sessionId, stepId = stepId, title = title, position = position,
    plannedSeconds = plannedSeconds, actualSeconds = actualSeconds,
    skipped = skipped, addedOnTheFly = addedOnTheFly
)

private fun FocusSessionEntity.toDomain() = FocusSession(
    id = id,
    taskId = taskId,
    projectId = projectId,
    mode = FocusMode.valueOf(mode),
    phase = FocusPhase.valueOf(phase),
    startedAt = Instant.ofEpochMilli(startedAt),
    endedAt = endedAt?.let { Instant.ofEpochMilli(it) },
    plannedSeconds = plannedSeconds,
    actualSeconds = actualSeconds,
    completed = completed,
    interruptions = interruptions,
    whiteNoiseId = whiteNoiseId
)
