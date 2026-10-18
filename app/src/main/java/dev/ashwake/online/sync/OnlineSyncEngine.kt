package dev.ashwake.online.sync

import androidx.room.withTransaction
import dev.ashwake.data.db.AshwakeDatabase
import dev.ashwake.data.db.dao.online.SyncDao
import dev.ashwake.data.db.dao.tasks.TaskDao
import dev.ashwake.data.db.entity.online.SyncConflictEntity
import dev.ashwake.data.db.entity.online.SyncStateEntity
import dev.ashwake.data.db.entity.tasks.TaskEntity
import dev.ashwake.data.db.entity.tasks.RecurrenceRuleEntity
import dev.ashwake.data.db.entity.tasks.TaskTagCrossRef
import dev.ashwake.data.db.entity.tasks.TaskWithRelations
import dev.ashwake.online.api.OnlineApi
import dev.ashwake.online.crypto.SpaceCrypto
import dev.ashwake.online.model.RemoteRecord
import dev.ashwake.online.model.SyncMutation
import dev.ashwake.online.repository.OnlineRepository
import dev.ashwake.domain.repository.tasks.TagRepository
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnlineSyncEngine @Inject constructor(
    private val db: AshwakeDatabase,
    private val taskDao: TaskDao,
    private val syncDao: SyncDao,
    private val tags: TagRepository,
    private val online: OnlineRepository,
    private val api: OnlineApi,
    private val crypto: SpaceCrypto
) {
    /** Один push/pull за раз: ручной запуск и WorkManager не должны читать
     * одинаковую базовую ревизию и создавать ложные конфликты. */
    private val syncMutex = Mutex()

    suspend fun sync() {
        syncMutex.withLock { syncLocked() }
    }

    private suspend fun syncLocked() {
        if (!online.configured || !online.hasSession()) return
        val snapshot = online.loadSnapshot()
        val session = online.session()
        val token = online.token()
        val personal = snapshot.spaces.firstOrNull { it.type == "personal" }
            ?: error("Личное пространство не найдено")
        syncDao.moveLocalTasksToPersonalSpace(personal.id)

        val spaces = snapshot.spaces.associateBy { it.id }
        val pending = syncDao.pendingTasks()
        val tombstones = syncDao.tombstones()
        val mutations = mutableListOf<SyncMutation>()
        for (task in pending) {
            val space = spaces[task.spaceId] ?: continue
            if (crypto.key(space.id, space.keyVersion) == null) continue
            val taskWithRelations = taskDao.getTask(task.id) ?: continue
            val parentGlobalId = task.parentTaskId?.let { taskDao.getTask(it)?.task?.globalId }
            mutations.add(
                    SyncMutation(
                        spaceId = space.id,
                        recordId = task.globalId ?: continue,
                        recordType = "task",
                        keyVersion = space.keyVersion,
                        baseRevision = task.syncRevision,
                        ciphertext = crypto.encrypt(
                            space.id,
                            space.keyVersion,
                            taskWithRelations.toSyncJson(parentGlobalId).toString()
                        ),
                        deleted = false
                    )
                )
        }
        for (item in tombstones) {
            val space = spaces[item.spaceId] ?: continue
            if (crypto.key(space.id, space.keyVersion) == null) continue
            mutations.add(
                    SyncMutation(
                        spaceId = space.id,
                        recordId = item.globalId,
                        recordType = item.recordType,
                        keyVersion = space.keyVersion,
                        baseRevision = item.baseRevision,
                        ciphertext = crypto.encrypt(space.id, space.keyVersion, "{\"deleted\":true}"),
                        deleted = true
                    )
                )
        }
        if (mutations.isNotEmpty()) {
            // API намеренно ограничивает запрос 100 мутациями. В одном цикле
            // могут быть и задачи, и tombstone, поэтому делим итоговый список.
            mutations.chunked(MAX_MUTATIONS_PER_REQUEST).forEach { batch ->
                val results = api.push(batch, token)
                results.forEach { result ->
                    if (result.accepted && result.revision != null) {
                        if (tombstones.any { it.globalId == result.recordId }) {
                            syncDao.removeTombstone(result.recordId)
                        } else {
                            syncDao.markTaskSynced(result.recordId, result.revision)
                        }
                    } else {
                        syncDao.markTaskConflict(result.recordId)
                    }
                }
            }
        }

        var cursor = syncDao.state(session.userId)?.cursor ?: 0
        var hasMore: Boolean
        do {
            val page = api.pull(cursor, token)
            page.records.forEach { applyRemote(it) }
            cursor = page.cursor
            syncDao.saveState(
                SyncStateEntity(
                    accountId = session.userId,
                    cursor = cursor,
                    lastSuccessAt = System.currentTimeMillis()
                )
            )
            hasMore = page.hasMore
        } while (hasMore)
    }

    private suspend fun applyRemote(remote: RemoteRecord) {
        if (remote.recordType != "task") return
        val plaintext = runCatching {
            crypto.decrypt(remote.spaceId, remote.keyVersion, remote.ciphertext)
        }.getOrElse { return }

        db.withTransaction {
            val local = taskDao.getTaskByGlobalId(remote.recordId)?.task
            if (local?.syncState == "PENDING" && local.syncRevision < remote.revision) {
                syncDao.markTaskConflict(remote.recordId)
                syncDao.saveConflict(
                    SyncConflictEntity(
                        globalId = remote.recordId,
                        spaceId = remote.spaceId,
                        localRevision = local.syncRevision,
                        remoteRevision = remote.revision,
                        remoteCiphertext = remote.ciphertext,
                        createdAt = System.currentTimeMillis()
                    )
                )
                return@withTransaction
            }
            if (remote.deleted) {
                local?.let { taskDao.deleteById(it.id) }
                return@withTransaction
            }
            if (local != null && local.syncRevision >= remote.revision) return@withTransaction

            val root = JSONObject(plaintext)
            val recurrenceId = root.optJSONObject("recurrence")?.let { recurrence ->
                val entity = recurrence.toRecurrenceEntity(local?.recurrenceId ?: 0)
                if (local?.recurrenceId != null) {
                    taskDao.updateRecurrence(entity)
                    local.recurrenceId
                } else {
                    taskDao.insertRecurrence(entity.copy(id = 0))
                }
            }
            val parentTaskId = root.stringOrNull("parentGlobalId")?.let { parentGlobalId ->
                taskDao.getTaskByGlobalId(parentGlobalId)?.task?.id
            }
            val decoded = root.toTaskEntity(
                id = local?.id ?: 0,
                globalId = remote.recordId,
                spaceId = remote.spaceId,
                revision = remote.revision,
                local = local,
                recurrenceId = recurrenceId,
                parentTaskId = parentTaskId
            )
            val taskId = if (local == null) taskDao.insert(decoded) else {
                taskDao.update(decoded)
                local.id
            }
            taskDao.clearTags(taskId)
            val tagIds = mutableListOf<Long>()
            for (name in root.optJSONArray("tags")?.strings().orEmpty()) {
                tagIds += tags.ensureTag(name)
            }
            if (tagIds.isNotEmpty()) {
                taskDao.insertCrossRefs(tagIds.distinct().map { TaskTagCrossRef(taskId, it) })
            }
        }
    }
}

private fun TaskWithRelations.toSyncJson(parentGlobalId: String?) = JSONObject().apply {
    put("title", task.title)
    putNullable("emoji", task.emoji)
    putNullable("note", task.note)
    put("priority", task.priority)
    put("difficulty", task.difficulty)
    putNullable("dueDate", task.dueDate)
    putNullable("dueTime", task.dueTime)
    putNullable("estimateMinutes", task.estimateMinutes)
    put("status", task.status)
    putNullable("completedAt", task.completedAt)
    put("position", task.position)
    putNullable("quadrantOverride", task.quadrantOverride)
    put("isTemplate", task.isTemplate)
    putNullable("persistentReminderMinutes", task.persistentReminderMinutes)
    put("postponeCount", task.postponeCount)
    putNullable("lastPostponedAt", task.lastPostponedAt)
    putNullable("sourceLink", task.sourceLink)
    putNullable("delegatedTo", task.delegatedTo)
    putNullable("parentGlobalId", parentGlobalId)
    putNullable("seriesId", task.seriesId)
    put("createdAt", task.createdAt)
    put("updatedAt", task.updatedAt)
    put("tags", JSONArray(tags.map { it.name }))
    recurrence?.let { rule ->
        put("recurrence", JSONObject().apply {
            put("type", rule.type)
            putNullable("intervalDays", rule.intervalDays)
            put("weekdaysMask", rule.weekdaysMask)
            putNullable("dayOfMonth", rule.dayOfMonth)
            put("startDate", rule.startDate)
            putNullable("endDate", rule.endDate)
            put("fromCompletion", rule.fromCompletion)
        })
    }
}

private fun JSONObject.toTaskEntity(
    id: Long,
    globalId: String,
    spaceId: String,
    revision: Long,
    local: TaskEntity?,
    recurrenceId: Long?,
    parentTaskId: Long?
) = TaskEntity(
    id = id,
    globalId = globalId,
    spaceId = spaceId,
    syncRevision = revision,
    syncState = "SYNCED",
    title = getString("title"),
    emoji = stringOrNull("emoji"),
    // Картинка лежит в приватном хранилище конкретного устройства; её нельзя
    // записывать как путь на другом устройстве. Файл синхронизируется отдельным
    // протоколом, а до его получения сохраняем локальное изображение.
    iconPath = local?.iconPath,
    note = stringOrNull("note"),
    projectId = local?.projectId,
    parentTaskId = parentTaskId,
    priority = getString("priority"),
    difficulty = optInt("difficulty", local?.difficulty ?: 1),
    dueDate = intOrNull("dueDate"),
    dueTime = intOrNull("dueTime"),
    estimateMinutes = intOrNull("estimateMinutes"),
    status = getString("status"),
    completedAt = longOrNull("completedAt"),
    position = getInt("position"),
    quadrantOverride = stringOrNull("quadrantOverride"),
    recurrenceId = recurrenceId,
    seriesId = stringOrNull("seriesId"),
    isTemplate = getBoolean("isTemplate"),
    persistentReminderMinutes = intOrNull("persistentReminderMinutes"),
    postponeCount = getInt("postponeCount"),
    lastPostponedAt = longOrNull("lastPostponedAt"),
    sourceLink = stringOrNull("sourceLink"),
    delegatedTo = stringOrNull("delegatedTo"),
    createdAt = getLong("createdAt"),
    updatedAt = getLong("updatedAt")
)

private const val MAX_MUTATIONS_PER_REQUEST = 100

private fun JSONObject.toRecurrenceEntity(id: Long) = RecurrenceRuleEntity(
    id = id,
    type = getString("type"),
    intervalDays = intOrNull("intervalDays"),
    weekdaysMask = getInt("weekdaysMask"),
    dayOfMonth = intOrNull("dayOfMonth"),
    startDate = getInt("startDate"),
    endDate = intOrNull("endDate"),
    fromCompletion = getBoolean("fromCompletion")
)

private fun JSONObject.putNullable(name: String, value: Any?) {
    put(name, value ?: JSONObject.NULL)
}

private fun JSONObject.stringOrNull(name: String): String? = if (isNull(name)) null else getString(name)
private fun JSONObject.intOrNull(name: String): Int? = if (isNull(name)) null else getInt(name)
private fun JSONObject.longOrNull(name: String): Long? = if (isNull(name)) null else getLong(name)
private fun JSONArray.strings(): List<String> = (0 until length()).map(::getString)
