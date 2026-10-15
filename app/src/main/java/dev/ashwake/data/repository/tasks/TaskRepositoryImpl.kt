package dev.ashwake.data.repository.tasks

import androidx.room.withTransaction
import dev.ashwake.core.model.Priority
import dev.ashwake.core.time.AppClock
import dev.ashwake.data.db.AshwakeDatabase
import dev.ashwake.data.db.dao.tasks.TaskDao
import dev.ashwake.data.db.dao.tasks.TaskAttachmentDao
import dev.ashwake.data.db.entity.tasks.TaskPostponementEntity
import dev.ashwake.data.db.entity.tasks.TaskTagCrossRef
import dev.ashwake.data.db.entity.online.SyncTombstoneEntity
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
    private val recurrenceCalculator: RecurrenceCalculator,
    private val attachmentDao: TaskAttachmentDao,
    private val attachmentStore: TaskAttachmentStore
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

    override suspend fun tasksWithReminders(): List<Task> =
        dao.tasksWithReminders().map { it.toDomain() }

    override fun observeTask(id: Long): Flow<Task?> =
        dao.observeTask(id).map { it?.toDomain() }

    override suspend fun getTask(id: Long): Task? = dao.getTask(id)?.toDomain()

    override suspend fun upsert(task: Task): Long = db.withTransaction {
        val now = clock.now()
        val conflictRevision = if (
            task.syncState == dev.ashwake.domain.model.tasks.TaskSyncState.CONFLICT && task.globalId != null
        ) {
            db.syncDao().conflict(task.globalId)?.remoteRevision
        } else null

        // Правило повтора сохраняем первым: задача должна лечь уже со ссылкой на него.
        val recurrenceId = task.recurrence?.let { rule ->
            if (rule.id == 0L) dao.insertRecurrence(rule.toEntity())
            else rule.id.also { dao.updateRecurrence(rule.toEntity()) }
        }

        val entity = task.copy(
            globalId = task.globalId ?: UUID.randomUUID().toString(),
            syncRevision = conflictRevision ?: task.syncRevision,
            syncState = if (task.spaceId == null) task.syncState else dev.ashwake.domain.model.tasks.TaskSyncState.PENDING,
            updatedAt = now
        ).toEntity(recurrenceId = recurrenceId)
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
        if (conflictRevision != null && task.globalId != null) {
            db.syncDao().removeConflict(task.globalId)
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
            globalId = UUID.randomUUID().toString(),
            syncRevision = 0,
            syncState = if (row.task.spaceId == null) "LOCAL" else "PENDING",
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

        // Серию задним числом проставляем и закрытому экземпляру, чтобы история склеивалась.
        if (row.task.seriesId == null) {
            dao.update(row.task.copy(seriesId = series))
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
        val storedAttachments = attachmentDao.storedNamesForTaskTree(id)
        db.withTransaction {
            val row = dao.getTask(id) ?: return@withTransaction
            val task = row.task
            if (task.globalId != null && task.spaceId != null) {
                db.syncDao().addTombstone(
                    SyncTombstoneEntity(
                        globalId = task.globalId,
                        spaceId = task.spaceId,
                        recordType = "task",
                        baseRevision = task.syncRevision,
                        createdAt = clock.now().toEpochMilli()
                    )
                )
            }
            row.subtasks.forEach { child ->
                if (child.globalId != null && child.spaceId != null) {
                    db.syncDao().addTombstone(
                        SyncTombstoneEntity(
                            globalId = child.globalId,
                            spaceId = child.spaceId,
                            recordType = "task",
                            baseRevision = child.syncRevision,
                            createdAt = clock.now().toEpochMilli()
                        )
                    )
                }
            }
            dao.deleteById(id)
        }
        storedAttachments.forEach(attachmentStore::delete)
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
                        globalId = UUID.randomUUID().toString(),
                        syncRevision = 0,
                        syncState = if (parent.task.spaceId == null) "LOCAL" else "PENDING",
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
}
