package dev.ashwake.data.repository.tasks

import androidx.room.withTransaction
import dev.ashwake.core.model.Priority
import dev.ashwake.core.time.AppClock
import dev.ashwake.data.db.AshwakeDatabase
import dev.ashwake.data.db.dao.tasks.TaskDao
import dev.ashwake.data.db.entity.tasks.TaskPostponementEntity
import dev.ashwake.data.db.entity.tasks.TaskTagCrossRef
import dev.ashwake.data.db.mapper.tasks.toDomain
import dev.ashwake.data.db.mapper.tasks.toEntity
import dev.ashwake.data.db.mapper.tasks.toEpochDayInt
import dev.ashwake.data.db.mapper.tasks.toLocalDate
import dev.ashwake.domain.engine.tasks.RecurrenceCalculator
import dev.ashwake.domain.model.tasks.EisenhowerQuadrant
import dev.ashwake.domain.model.tasks.PostponeSource
import dev.ashwake.domain.model.tasks.Task
import dev.ashwake.domain.model.tasks.TaskStatus
import dev.ashwake.domain.repository.tasks.TagRepository
import dev.ashwake.domain.repository.tasks.TaskFilter
import dev.ashwake.domain.repository.tasks.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Порог, с которого задача считается залежавшейся (п. 1 ТЗ). */
private const val STALE_THRESHOLD = 3

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val db: AshwakeDatabase,
    private val dao: TaskDao,
    private val tags: TagRepository,
    private val clock: AppClock,
    private val recurrenceCalculator: RecurrenceCalculator
) : TaskRepository {

    override fun observeTasks(filter: TaskFilter): Flow<List<Task>> =
        dao.observeTasks(
            includeDone = if (filter.includeDone) 1 else 0,
            projectId = filter.projectId,
            tagId = filter.tagId,
            priority = filter.priority?.name,
            dueBefore = filter.dueBefore?.toEpochDayInt(),
            minPostpones = if (filter.onlyStale) STALE_THRESHOLD else 0,
            query = filter.query?.takeIf { it.isNotBlank() }
        ).map { rows -> rows.map { it.toDomain() } }

    override fun observeTasksInRange(
        from: LocalDate,
        to: LocalDate,
        includeDone: Boolean
    ): Flow<List<Task>> =
        dao.observeTasksInRange(
            from = from.toEpochDayInt(),
            to = to.toEpochDayInt(),
            includeDone = if (includeDone) 1 else 0
        ).map { rows -> rows.map { it.toDomain() } }

    override fun observeTasksForDay(today: LocalDate): Flow<List<Task>> =
        dao.observeTasksForDay(today.toEpochDayInt())
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun tasksWithReminders(): List<Task> =
        dao.tasksWithReminders().map { it.toDomain() }

    override fun observeTask(id: Long): Flow<Task?> =
        dao.observeTask(id).map { it?.toDomain() }

    override suspend fun getTask(id: Long): Task? = dao.getTask(id)?.toDomain()

    override suspend fun upsert(task: Task): Long = db.withTransaction {
        val now = clock.now()

        // Правило повтора сохраняем первым: задача должна лечь уже со ссылкой на него.
        val recurrenceId = task.recurrence?.let { rule ->
            if (rule.id == 0L) dao.insertRecurrence(rule.toEntity())
            else rule.id.also { dao.updateRecurrence(rule.toEntity()) }
        }

        val entity = task.copy(updatedAt = now).toEntity(recurrenceId = recurrenceId)
        val id = if (task.id == 0L) {
            dao.insert(entity.copy(createdAt = now.toEpochMilli()))
        } else {
            dao.update(entity)
            task.id
        }

        // Теги из быстрого ввода приходят без id — создаём на лету.
        dao.clearTags(id)
        val tagIds = task.tags.map { tag ->
            if (tag.id != 0L) tag.id else tags.ensureTag(tag.name)
        }
        if (tagIds.isNotEmpty()) {
            dao.insertCrossRefs(tagIds.distinct().map { TaskTagCrossRef(taskId = id, tagId = it) })
        }
        id
    }

    /**
     * Закрытие задачи. У повторяющейся сразу порождается следующий экземпляр серии,
     * иначе повтор пришлось бы «досоздавать» из UI и он терялся бы при закрытии
     * из уведомления или виджета.
     */
    override suspend fun complete(id: Long): Long? = db.withTransaction {
        val row = dao.getTask(id) ?: return@withTransaction null
        val now = clock.now()
        dao.setStatus(id, TaskStatus.DONE.name, now.toEpochMilli(), now.toEpochMilli())

        val rule = row.recurrence?.toDomain() ?: return@withTransaction null
        val today = clock.today()
        val anchor = if (rule.fromCompletion) today else row.task.dueDate?.toLocalDate() ?: today
        val nextDate = recurrenceCalculator.next(rule, anchor) ?: return@withTransaction null

        val series = row.task.seriesId ?: UUID.randomUUID().toString()
        val nextEntity = row.task.copy(
            id = 0,
            status = TaskStatus.ACTIVE.name,
            completedAt = null,
            dueDate = nextDate.toEpochDayInt(),
            postponeCount = 0,          // новый экземпляр начинает с чистого счётчика
            lastPostponedAt = null,
            seriesId = series,
            createdAt = now.toEpochMilli(),
            updatedAt = now.toEpochMilli()
        )
        val newId = dao.insert(nextEntity)

        // Серию задним числом проставляем и закрытому экземпляру, чтобы история
        // склеивалась. Точечным запросом, а не записью строки целиком: `row`
        // снят до смены статуса, и запись его вернула бы задачу в работу —
        // повторяющаяся задача из-за этого не закрывалась вовсе, а только
        // плодила копии.
        if (row.task.seriesId == null) {
            dao.setSeriesId(id, series)
        }
        // Теги переносим на следующий экземпляр.
        val tagIds = row.tags.map { it.id }
        if (tagIds.isNotEmpty()) {
            dao.insertCrossRefs(tagIds.map { TaskTagCrossRef(taskId = newId, tagId = it) })
        }
        newId
    }

    override suspend fun reopen(id: Long) {
        val now = clock.now().toEpochMilli()
        dao.setStatus(id, TaskStatus.ACTIVE.name, null, now)
    }

    override suspend fun discardSpawnedRecurrence(taskId: Long): Long? = db.withTransaction {
        val row = dao.getTask(taskId) ?: return@withTransaction null
        val series = row.task.seriesId ?: return@withTransaction null
        // Порождённый экземпляр создан в тот же момент, что закрыта задача.
        // Секунда запаса — на случай, если вставка и запись completedAt
        // разошлись по времени внутри транзакции
        val completedAt = row.task.completedAt ?: return@withTransaction null

        val spawned = dao.untouchedSeriesInstance(
            seriesId = series,
            excludeId = taskId,
            createdAtLeast = completedAt - SPAWN_WINDOW_MILLIS
        ) ?: return@withTransaction null

        dao.deleteById(spawned.id)
        spawned.id
    }

    override suspend fun postpone(id: Long, toDate: LocalDate?, source: PostponeSource) {
        db.withTransaction {
            val row = dao.getTask(id) ?: return@withTransaction
            val now = clock.now().toEpochMilli()
            dao.insertPostponement(
                TaskPostponementEntity(
                    taskId = id,
                    fromDate = row.task.dueDate,
                    toDate = toDate?.toEpochDayInt(),
                    at = now,
                    source = source.name
                )
            )
            dao.applyPostpone(id, toDate?.toEpochDayInt(), now)
        }
    }

    override suspend fun setQuadrant(
        id: Long,
        quadrant: EisenhowerQuadrant,
        priority: Priority?
    ) {
        dao.setQuadrant(id, quadrant.name, priority?.name, clock.now().toEpochMilli())
    }

    override suspend fun delete(id: Long) {
        dao.moveToTrash(id, clock.now().toEpochMilli())
    }

    override fun observeTrash(): Flow<List<Task>> =
        dao.observeTrash().map { list -> list.map { it.toDomain() } }

    override suspend fun restoreFromTrash(id: Long) {
        dao.restoreFromTrash(id, clock.now().toEpochMilli())
    }

    override suspend fun purge(id: Long) = dao.deleteById(id)

    override suspend fun emptyTrash() = dao.emptyTrash()

    override suspend fun purgeTrashOlderThan(days: Long): Int {
        val before = clock.now().minus(java.time.Duration.ofDays(days)).toEpochMilli()
        return dao.purgeTrashOlderThan(before)
    }

    override suspend fun setDelegate(id: Long, delegateTo: String?) {
        dao.setDelegate(id, delegateTo, clock.now().toEpochMilli())
    }

    override suspend fun addSubtasks(parentId: Long, titles: List<String>) {
        db.withTransaction {
            val parent = dao.getTask(parentId) ?: return@withTransaction
            val now = clock.now().toEpochMilli()
            var position = dao.maxSubtaskPosition(parentId)
            titles.filter { it.isNotBlank() }.forEach { title ->
                position++
                dao.insert(
                    parent.task.copy(
                        id = 0,
                        title = title.trim(),
                        note = null,
                        parentTaskId = parentId,
                        estimateMinutes = null,
                        status = TaskStatus.ACTIVE.name,
                        completedAt = null,
                        position = position,
                        recurrenceId = null,
                        seriesId = null,
                        postponeCount = 0,
                        lastPostponedAt = null,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
        }
    }

    private companion object {
        /**
         * Насколько раньше закрытия мог быть создан порождённый экземпляр.
         * Вставка и запись completedAt идут в одной транзакции, но берут
         * «сейчас» по отдельности, поэтому запас нужен.
         */
        const val SPAWN_WINDOW_MILLIS = 1_000L
    }
}
