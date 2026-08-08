package dev.ashwake.data.repository.timebox

import androidx.room.withTransaction
import dev.ashwake.core.time.AppClock
import dev.ashwake.data.calendar.SystemCalendarReader
import dev.ashwake.data.db.AshwakeDatabase
import dev.ashwake.data.db.dao.timebox.TimeboxDao
import dev.ashwake.data.db.entity.timebox.TimeboxBlockEntity
import dev.ashwake.data.db.entity.timebox.TimeboxDayEntity
import dev.ashwake.data.db.mapper.tasks.toEpochDayInt
import dev.ashwake.data.db.mapper.tasks.toLocalDate
import dev.ashwake.data.settings.AppSettings
import dev.ashwake.domain.engine.timebox.PlanRequest
import dev.ashwake.domain.engine.timebox.PlanResult
import dev.ashwake.domain.engine.timebox.TimeboxPlanner
import dev.ashwake.domain.model.tasks.BlockKind
import dev.ashwake.domain.model.tasks.BlockOrigin
import dev.ashwake.domain.model.tasks.TimeboxBlock
import dev.ashwake.domain.model.tasks.TimeboxDay
import dev.ashwake.domain.repository.routines.RoutineRepository
import dev.ashwake.domain.repository.tasks.TaskFilter
import dev.ashwake.domain.repository.tasks.TaskRepository
import dev.ashwake.domain.repository.timebox.TimeboxRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimeboxRepositoryImpl @Inject constructor(
    private val db: AshwakeDatabase,
    private val dao: TimeboxDao,
    private val tasks: TaskRepository,
    private val routines: RoutineRepository,
    private val calendar: SystemCalendarReader,
    private val settings: AppSettings,
    private val planner: TimeboxPlanner,
    private val clock: AppClock
) : TimeboxRepository {

    override fun observeDay(date: LocalDate): Flow<TimeboxDay> = combine(
        dao.observeDay(date.toEpochDayInt()),
        dao.observeBlocks(date.toEpochDayInt())
    ) { day, blocks ->
        TimeboxDay(
            date = date,
            blocks = blocks.map { it.toDomain() },
            deficitMinutes = day?.deficitMinutes ?: 0,
            plannedAt = day?.plannedAt?.let { java.time.Instant.ofEpochMilli(it) }
        )
    }

    /**
     * «Разложить день».
     *
     * Ручные и закреплённые блоки переживают пересборку: кнопка не должна
     * стирать то, что человек поправил руками. Они же становятся занятыми
     * интервалами для новой раскладки.
     */
    override suspend fun planDay(date: LocalDate): PlanResult {
        val timeboxSettings = settings.timebox.first()
        val useCalendar = settings.useSystemCalendar.first()

        val dayTasks = tasks.observeTasks(TaskFilter(dueBefore = date)).first()
            .filter { it.dueDate != null && it.dueDate <= date }

        val kept = dao.blocks(date.toEpochDayInt())
            .map { it.toDomain() }
            .filter { it.pinned || it.createdBy == BlockOrigin.MANUAL }

        val busy = buildList {
            addAll(kept)
            addAll(routineBlocks(date))
            if (useCalendar) addAll(calendar.eventsOn(date))
        }

        val result = planner.plan(
            PlanRequest(
                date = date,
                tasks = dayTasks,
                busy = busy,
                settings = timeboxSettings,
                nowMinute = if (date == clock.today()) currentMinute() else null
            )
        )

        db.withTransaction {
            dao.upsertDay(
                TimeboxDayEntity(
                    date = date.toEpochDayInt(),
                    plannedAt = clock.now().toEpochMilli(),
                    workStartMinute = timeboxSettings.workStartMinute,
                    workEndMinute = timeboxSettings.workEndMinute,
                    bufferMinutes = timeboxSettings.bufferMinutes,
                    lunchStartMinute = timeboxSettings.lunchStartMinute,
                    lunchEndMinute = timeboxSettings.lunchEndMinute,
                    deficitMinutes = result.deficitMinutes
                )
            )
            dao.clearAutoBlocks(date.toEpochDayInt())
            // Сохраняем только новое: сохранённое ранее уже лежит в базе
            val existingIds = kept.mapNotNull { it.id.takeIf { id -> id != 0L } }.toSet()
            dao.insertBlocks(
                result.blocks
                    .filterNot { it.id in existingIds }
                    .map { it.toEntity() }
            )
        }
        return result
    }

    override suspend fun moveBlock(blockId: Long, newStartMinute: Int) {
        val timeboxSettings = settings.timebox.first()
        val block = findBlock(blockId) ?: return
        val moved = planner
            .move(listOf(block), blockId, newStartMinute, timeboxSettings)
            .first()
        dao.updateBlock(moved.toEntity())
    }

    override suspend fun setPinned(blockId: Long, pinned: Boolean) {
        val block = findBlock(blockId) ?: return
        dao.updateBlock(block.copy(pinned = pinned).toEntity())
    }

    override suspend fun deleteBlock(blockId: Long) = dao.deleteBlock(blockId)

    override suspend fun clearDay(date: LocalDate) = dao.clearAll(date.toEpochDayInt())

    /** Пересчёт на лету: блок занял больше времени, чем планировалось. */
    override suspend fun reflow(date: LocalDate, blockId: Long, actualEndMinute: Int) {
        val timeboxSettings = settings.timebox.first()
        val blocks = dao.blocks(date.toEpochDayInt()).map { it.toDomain() }
        planner.reflow(blocks, blockId, actualEndMinute, timeboxSettings)
            .forEach { dao.updateBlock(it.toEntity()) }
    }

    private suspend fun findBlock(blockId: Long): TimeboxBlock? {
        // Блок ищется по всему окну вокруг сегодня: id уникален, а дата неизвестна
        val today = clock.today()
        return (-1L..1L)
            .mapNotNull { offset ->
                dao.blocks(today.plusDays(offset).toEpochDayInt())
                    .firstOrNull { it.id == blockId }
            }
            .firstOrNull()
            ?.toDomain()
    }

    /** Рутины с привязкой ко времени — занятые интервалы дня (п. 2). */
    private suspend fun routineBlocks(date: LocalDate): List<TimeboxBlock> =
        routines.observeRoutines().first()
            .filter { it.startTime != null && it.steps.isNotEmpty() }
            .map { routine ->
                val start = routine.startTime!!.hour * 60 + routine.startTime.minute
                TimeboxBlock(
                    date = date,
                    startMinute = start,
                    endMinute = start + routine.plannedSeconds / 60,
                    kind = BlockKind.ROUTINE,
                    title = routine.name,
                    routineId = routine.id,
                    pinned = true
                )
            }

    private fun currentMinute(): Int {
        val now = clock.now().atZone(clock.zone() ?: ZoneId.systemDefault())
        return now.hour * 60 + now.minute
    }
}

private fun TimeboxBlockEntity.toDomain() = TimeboxBlock(
    id = id,
    date = date.toLocalDate(),
    startMinute = startMinute,
    endMinute = endMinute,
    kind = BlockKind.valueOf(kind),
    title = title,
    taskId = taskId,
    routineId = routineId,
    calendarEventId = calendarEventId,
    pinned = pinned,
    createdBy = BlockOrigin.valueOf(createdBy),
    actualStartMinute = actualStartMinute,
    actualEndMinute = actualEndMinute
)

private fun TimeboxBlock.toEntity() = TimeboxBlockEntity(
    id = id,
    date = date.toEpochDayInt(),
    startMinute = startMinute,
    endMinute = endMinute,
    kind = kind.name,
    title = title,
    taskId = taskId,
    routineId = routineId,
    calendarEventId = calendarEventId,
    pinned = pinned,
    createdBy = createdBy.name,
    actualStartMinute = actualStartMinute,
    actualEndMinute = actualEndMinute
)
