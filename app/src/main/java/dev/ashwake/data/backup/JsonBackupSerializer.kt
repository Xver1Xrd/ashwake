package dev.ashwake.data.backup

import android.database.Cursor
import android.util.Base64
import dev.ashwake.data.db.AshwakeDatabase
import dev.ashwake.data.db.dao.abstinence.AbstinenceDao
import dev.ashwake.data.db.dao.character.CharacterDao
import dev.ashwake.data.db.dao.habits.HabitDao
import dev.ashwake.data.db.dao.ritual.RitualDao
import dev.ashwake.data.db.dao.tasks.TaskDao
import dev.ashwake.data.db.dao.tasks.TaskAttachmentDao
import dev.ashwake.data.repository.tasks.TaskAttachmentStore
import dev.ashwake.data.db.entity.habits.HabitEntryEntity
import dev.ashwake.data.db.entity.habits.HabitEntity
import dev.ashwake.data.db.entity.tasks.TaskEntity
import dev.ashwake.data.db.entity.abstinence.AbstinenceEntity
import dev.ashwake.data.db.entity.abstinence.AbstinenceAttemptEntity
import dev.ashwake.data.db.entity.ritual.DailyReviewEntity
import dev.ashwake.data.db.entity.character.CharacterStatEntity
import dev.ashwake.data.db.entity.character.EquippedItemEntity
import dev.ashwake.data.db.entity.character.OwnedItemEntity
import dev.ashwake.data.db.entity.character.WalletEntity
import androidx.room.withTransaction
import kotlin.math.floor
import kotlin.math.sqrt
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import java.io.File
import java.util.UUID

/** Сколько записей каждого вида попало в архив — показывается в отчёте. */
data class BackupContents(
    val tasks: Int,
    val habits: Int,
    val habitEntries: Int,
    val abstinences: Int,
    val reviews: Int
) {
    val total: Int get() = tasks + habits + habitEntries + abstinences + reviews
}

/**
 * Экспорт и импорт базы в JSON (п. 13).
 *
 * Формат — плоские массивы по таблицам с полем версии. Это не самый компактный
 * вариант, но единственный, который человек может открыть и прочитать глазами,
 * а офлайн-first приложение без облака обязано отдавать данные в понятном виде.
 */
@Singleton
class JsonBackupSerializer @Inject constructor(
    private val db: AshwakeDatabase,
    private val taskDao: TaskDao,
    private val attachmentDao: TaskAttachmentDao,
    private val attachmentStore: TaskAttachmentStore,
    private val habitDao: HabitDao,
    private val abstinenceDao: AbstinenceDao,
    private val ritualDao: RitualDao,
    private val characterDao: CharacterDao
) {

    suspend fun export(): Pair<String, BackupContents> {
        val tasks = taskDao.observeTasks(1, null, null, null, null, 0, null).first()
            .map { it.task }
        val habits = habitDao.observeHabits(1).first()
        val entries = habitDao.observeEntriesInRange(MIN_DAY, MAX_DAY).first()
        val abstinences = abstinenceDao.observeAll(1).first()
        val attempts = abstinenceDao.observeAttempts().first()
        val reviews = ritualDao.observeReviews(MIN_DAY, MAX_DAY).first()
        val wallet = characterDao.wallet()
        val owned = characterDao.observeOwned().first()
        val equipped = characterDao.equipped()
        val stats = characterDao.observeStats().first()
        var totalAttachmentBytes = 0L
        val attachmentFiles = attachmentDao.allAttachments().map { attachment ->
            val file = attachmentStore.file(attachment.storedName)
            require(file.isFile) { "Не найдено вложение ${attachment.displayName}" }
            require(file.length() == attachment.sizeBytes) {
                "Размер вложения ${attachment.displayName} не совпадает с базой"
            }
            totalAttachmentBytes += file.length()
            require(totalAttachmentBytes <= TaskAttachmentStore.MAX_TOTAL_BYTES) {
                "Общий объём вложений превышает 64 МБ"
            }
            JSONObject().apply {
                put("storedName", attachment.storedName)
                put("data", Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
            }
        }

        val root = JSONObject().apply {
            put("version", FORMAT_VERSION)
            put("exportedAt", System.currentTimeMillis())

            put("tasks", JSONArray(tasks.map { it.toJson() }))
            put("habits", JSONArray(habits.map { habit ->
                JSONObject().apply {
                    put("id", habit.id)
                    put("name", habit.name)
                    put("type", habit.type)
                    put("sphere", habit.sphere)
                    put("targetValue", habit.targetValue)
                    put("unitName", habit.unitName)
                    put("minimumValue", habit.minimumValue)
                    put("scheduleType", habit.scheduleType)
                    put("timesPerWeek", habit.timesPerWeek)
                    put("weekdaysMask", habit.weekdaysMask)
                    put("archived", habit.archived)
                    put("createdAt", habit.createdAt)
                }
            }))
            put("habitEntries", JSONArray(entries.map { it.toJson() }))
            put("abstinences", JSONArray(abstinences.map { item ->
                JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("mode", item.mode)
                    put("gentlePenaltyDays", item.gentlePenaltyDays)
                    put("motivationText", item.motivationText)
                    put("createdAt", item.createdAt)
                }
            }))
            put("abstinenceAttempts", JSONArray(attempts.map { attempt ->
                JSONObject().apply {
                    put("abstinenceId", attempt.abstinenceId)
                    put("ordinal", attempt.ordinal)
                    put("startedAt", attempt.startedAt)
                    put("endedAt", attempt.endedAt)
                    put("penaltyDays", attempt.penaltyDays)
                }
            }))
            put("dailyReviews", JSONArray(reviews.map { review ->
                JSONObject().apply {
                    put("date", review.date)
                    put("dayRating", review.dayRating)
                    put("mood", review.mood)
                    put("energy", review.energy)
                    put("note", review.note)
                    put("completedAt", review.completedAt)
                    put("completedAs", review.completedAs)
                }
            }))
            put("wallet", JSONObject().apply {
                put("coins", wallet?.coins ?: 0)
                put("xp", wallet?.xp ?: 0)
            })
            put("ownedItems", JSONArray(owned.map { it.itemId }))
            put("equippedItems", JSONObject().apply {
                equipped.forEach { put(it.slot, it.itemId) }
            })
            put("stats", JSONObject().apply {
                stats.forEach { put(it.stat, it.points) }
            })
            // Полный снимок всех прикладных таблиц. Именованные массивы выше
            // оставлены для читаемости и совместимости со старыми архивами.
            put("database", exportDatabase())
            put("attachmentFiles", JSONArray(attachmentFiles))
        }

        return root.toString(2) to BackupContents(
            tasks = tasks.size,
            habits = habits.size,
            habitEntries = entries.size,
            abstinences = abstinences.size,
            reviews = reviews.size
        )
    }

    /**
     * Разбор архива без записи в базу — предпросмотр перед восстановлением.
     * Показать, что внутри, до перезаписи данных обязательно: восстановление
     * необратимо.
     */
    fun peek(json: String): BackupContents? = runCatching {
        val root = JSONObject(json)
        require(root.getInt("version") == FORMAT_VERSION) { "Неподдерживаемая версия архива" }
        BackupContents(
            tasks = root.optJSONArray("tasks")?.length() ?: 0,
            habits = root.optJSONArray("habits")?.length() ?: 0,
            habitEntries = root.optJSONArray("habitEntries")?.length() ?: 0,
            abstinences = root.optJSONArray("abstinences")?.length() ?: 0,
            reviews = root.optJSONArray("dailyReviews")?.length() ?: 0
        )
    }.getOrNull()

    /**
     * Полное восстановление. Все JSON-объекты создаются до транзакции: битый
     * архив не должен успеть удалить рабочие данные. Внутри транзакции либо
     * применится весь снимок, либо Room откатит каждую запись.
     */
    suspend fun restore(json: String): BackupContents {
        val root = JSONObject(json)
        require(root.getInt("version") == FORMAT_VERSION) { "Неподдерживаемая версия архива" }
        val contents = peek(json) ?: error("Файл не похож на архив Ashwake")
        val stagedAttachments = stageAttachmentFiles(root)
        root.optJSONObject("database")?.let { snapshot ->
            try {
                validateAttachmentSnapshot(snapshot, stagedAttachments)
                restoreDatabase(snapshot)
                replaceAttachmentFiles(stagedAttachments)
            } finally {
                stagedAttachments.deleteRecursively()
            }
            return contents
        }

        val tasks = root.array("tasks").objects().map { value ->
            TaskEntity(
                id = value.getLong("id"),
                globalId = value.stringOrNull("globalId"),
                spaceId = value.stringOrNull("spaceId"),
                syncRevision = value.optLong("syncRevision", 0),
                syncState = value.optString("syncState", "LOCAL"),
                title = value.getString("title"),
                note = value.stringOrNull("note"),
                priority = value.getString("priority"),
                dueDate = value.intOrNull("dueDate"),
                dueTime = value.intOrNull("dueTime"),
                estimateMinutes = value.intOrNull("estimateMinutes"),
                status = value.getString("status"),
                completedAt = value.longOrNull("completedAt"),
                postponeCount = value.optInt("postponeCount", 0),
                createdAt = value.getLong("createdAt"),
                updatedAt = value.optLong("updatedAt", value.getLong("createdAt"))
            )
        }
        val habits = root.array("habits").objects().map { value ->
            HabitEntity(
                id = value.getLong("id"),
                name = value.getString("name"),
                type = value.getString("type"),
                sphere = value.getString("sphere"),
                targetValue = value.optDouble("targetValue", 1.0).toFloat(),
                unitName = value.stringOrNull("unitName"),
                minimumValue = value.doubleOrNull("minimumValue")?.toFloat(),
                scheduleType = value.getString("scheduleType"),
                timesPerWeek = value.optInt("timesPerWeek", 3),
                weekdaysMask = value.optInt("weekdaysMask", 0b1111111),
                archived = value.optBoolean("archived", false),
                createdAt = value.getLong("createdAt")
            )
        }
        val entries = root.array("habitEntries").objects().map { value ->
            HabitEntryEntity(
                habitId = value.getLong("habitId"),
                date = value.getInt("date"),
                status = value.getString("status"),
                value = value.optDouble("value", 0.0).toFloat(),
                note = value.stringOrNull("note"),
                source = "RESTORE"
            )
        }
        val abstinences = root.array("abstinences").objects().map { value ->
            AbstinenceEntity(
                id = value.getLong("id"),
                name = value.getString("name"),
                mode = value.getString("mode"),
                gentlePenaltyDays = value.optInt("gentlePenaltyDays", 7),
                motivationText = value.stringOrNull("motivationText"),
                createdAt = value.getLong("createdAt")
            )
        }
        val attempts = root.array("abstinenceAttempts").objects().map { value ->
            AbstinenceAttemptEntity(
                abstinenceId = value.getLong("abstinenceId"),
                ordinal = value.getInt("ordinal"),
                startedAt = value.getLong("startedAt"),
                endedAt = value.longOrNull("endedAt"),
                penaltyDays = value.optInt("penaltyDays", 0)
            )
        }
        val reviews = root.array("dailyReviews").objects().map { value ->
            DailyReviewEntity(
                date = value.getInt("date"),
                dayRating = value.intOrNull("dayRating"),
                mood = value.intOrNull("mood"),
                energy = value.intOrNull("energy"),
                note = value.stringOrNull("note"),
                completedAt = value.getLong("completedAt"),
                completedAs = value.getString("completedAs")
            )
        }
        val walletJson = root.optJSONObject("wallet") ?: JSONObject()
        val wallet = WalletEntity(coins = walletJson.optLong("coins"), xp = walletJson.optLong("xp"))
        val owned = root.array("ownedItems").strings().map { itemId ->
            OwnedItemEntity(itemId = itemId, acquiredAt = System.currentTimeMillis(), source = "RESTORE")
        }
        val equippedJson = root.optJSONObject("equippedItems") ?: JSONObject()
        val equipped = equippedJson.keys().asSequence().map { slot ->
            EquippedItemEntity(slot, equippedJson.getString(slot))
        }.toList()
        val statsJson = root.optJSONObject("stats") ?: JSONObject()
        val stats = statsJson.keys().asSequence().map { stat ->
            val points = statsJson.getLong(stat)
            CharacterStatEntity(stat, points, floor(sqrt(points / 4.0)).toInt())
        }.toList()

        db.withTransaction {
            val sql = db.openHelper.writableDatabase
            listOf(
                "daily_review_top_tasks", "daily_reviews", "task_attachments", "task_tag_cross_ref",
                "task_postponements", "tasks", "habit_entries", "habit_freezes",
                "habit_pauses", "habit_anchors", "habits", "abstinence_attempts",
                "abstinence_milestones", "craving_events", "abstinence_substitutes",
                "abstinences", "equipped_items", "owned_items", "character_stats", "wallet"
            ).forEach { table -> sql.execSQL("DELETE FROM $table") }

            tasks.forEach { taskDao.insert(it) }
            habits.forEach { habitDao.upsert(it) }
            entries.forEach { habitDao.upsertEntry(it) }
            abstinences.forEach { abstinenceDao.upsert(it) }
            attempts.forEach { abstinenceDao.insertAttempt(it) }
            reviews.forEach { ritualDao.upsertReview(it) }
            characterDao.upsertWallet(wallet)
            owned.forEach { characterDao.insertOwned(it) }
            equipped.forEach { characterDao.equip(it) }
            stats.forEach { characterDao.upsertStat(it) }
        }

        replaceAttachmentFiles(stagedAttachments)

        return BackupContents(tasks.size, habits.size, entries.size, abstinences.size, reviews.size)
    }

    private fun stageAttachmentFiles(root: JSONObject): File {
        val current = attachmentStore.directory
        val staging = File(current.parentFile, ".attachments-restore-${UUID.randomUUID()}")
        check(staging.mkdirs()) { "Не удалось подготовить восстановление вложений" }
        var totalBytes = 0L
        val seen = mutableSetOf<String>()
        try {
            root.array("attachmentFiles").objects().forEach { item ->
                val storedName = item.getString("storedName")
                require(TaskAttachmentStore.STORED_NAME.matches(storedName)) {
                    "Архив содержит некорректное имя вложения"
                }
                require(seen.add(storedName)) { "Архив содержит дубликат вложения" }
                val bytes = Base64.decode(item.getString("data"), Base64.DEFAULT)
                require(bytes.size <= TaskAttachmentStore.MAX_ATTACHMENT_BYTES) {
                    "Вложение в архиве больше 25 МБ"
                }
                totalBytes += bytes.size
                require(totalBytes <= TaskAttachmentStore.MAX_TOTAL_BYTES) {
                    "Вложения в архиве занимают больше 64 МБ"
                }
                File(staging, storedName).writeBytes(bytes)
            }
            return staging
        } catch (error: Throwable) {
            staging.deleteRecursively()
            throw error
        }
    }

    private fun replaceAttachmentFiles(staging: File) {
        val current = attachmentStore.directory
        val previous = File(current.parentFile, ".attachments-previous-${UUID.randomUUID()}")
        check(current.renameTo(previous)) { "Не удалось заменить папку вложений" }
        if (!staging.renameTo(current)) {
            previous.renameTo(current)
            error("Не удалось восстановить вложения")
        }
        previous.deleteRecursively()
    }

    private fun validateAttachmentSnapshot(snapshot: JSONObject, staging: File) {
        val attachmentTable = snapshot.array("tables").objects()
            .firstOrNull { it.getString("name") == "task_attachments" }
        val stagedSizes = staging.listFiles().orEmpty().associate { it.name to it.length() }
        if (attachmentTable == null) {
            require(stagedSizes.isEmpty()) { "Архив содержит вложения без метаданных" }
            return
        }

        val columns = attachmentTable.getJSONArray("columns").strings()
        val nameIndex = columns.indexOf("storedName")
        val sizeIndex = columns.indexOf("sizeBytes")
        require(nameIndex >= 0 && sizeIndex >= 0) { "Повреждена таблица вложений" }
        val archivedSizes = buildMap {
            val rows = attachmentTable.getJSONArray("rows")
            repeat(rows.length()) { index ->
                val row = rows.getJSONArray(index)
                put(row.getString(nameIndex), row.getLong(sizeIndex))
            }
        }
        require(archivedSizes == stagedSizes) {
            "Файлы вложений не совпадают с метаданными архива"
        }
    }

    private fun exportDatabase(): JSONObject {
        val sql = db.openHelper.readableDatabase
        val tables = sql.query(
            "SELECT name FROM sqlite_master WHERE type='table' " +
                "AND name NOT LIKE 'sqlite_%' " +
                "AND name NOT IN ('android_metadata', 'room_master_table') ORDER BY name"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
        return JSONObject().apply {
            put("schemaVersion", db.openHelper.readableDatabase.version)
            put("tables", JSONArray().apply {
                tables.forEach { table -> put(exportTable(table)) }
            })
        }
    }

    private fun exportTable(table: String): JSONObject {
        val cursor = db.openHelper.readableDatabase.query("SELECT * FROM ${table.quoted()}")
        return cursor.use {
            JSONObject().apply {
                put("name", table)
                put("columns", JSONArray((0 until cursor.columnCount).map(cursor::getColumnName)))
                put("rows", JSONArray().apply {
                    while (cursor.moveToNext()) {
                        put(JSONArray((0 until cursor.columnCount).map { cursor.backupValue(it) }))
                    }
                })
            }
        }
    }

    private suspend fun restoreDatabase(snapshot: JSONObject) {
        require(snapshot.optInt("schemaVersion", 0) in 1..db.openHelper.readableDatabase.version) {
            "Архив создан несовместимой версией базы"
        }
        val archivedTables = snapshot.array("tables").objects()
        val sql = db.openHelper.writableDatabase
        val currentTables = sql.query(
            "SELECT name FROM sqlite_master WHERE type='table' " +
                "AND name NOT LIKE 'sqlite_%' " +
                "AND name NOT IN ('android_metadata', 'room_master_table')"
        ).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
        val currentColumns = currentTables.associateWith { table ->
            sql.query("PRAGMA table_info(${table.quoted()})").use { cursor ->
                buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
            }
        }

        archivedTables.forEach { table ->
            require(table.getString("name") in currentTables) { "Архив содержит неизвестную таблицу" }
            val name = table.getString("name")
            val columns = table.getJSONArray("columns").strings()
            require(columns.isNotEmpty() && columns.all { it in currentColumns.getValue(name) }) {
                "Архив содержит неизвестные столбцы"
            }
        }

        db.withTransaction {
            sql.execSQL("PRAGMA defer_foreign_keys = ON")
            currentTables.forEach { table -> sql.execSQL("DELETE FROM ${table.quoted()}") }
            archivedTables.forEach { table ->
                val name = table.getString("name")
                val columns = table.getJSONArray("columns").strings()
                val statement = sql.compileStatement(
                    "INSERT INTO ${name.quoted()} (${columns.joinToString { it.quoted() }}) " +
                        "VALUES (${columns.joinToString { "?" }})"
                )
                val rows = table.getJSONArray("rows")
                repeat(rows.length()) { rowIndex ->
                    statement.clearBindings()
                    val row = rows.getJSONArray(rowIndex)
                    require(row.length() == columns.size) { "Повреждённая строка архива" }
                    repeat(row.length()) { columnIndex ->
                        statement.bindBackupValue(columnIndex + 1, row.opt(columnIndex))
                    }
                    statement.executeInsert()
                }
            }
        }
    }

    private fun TaskEntity.toJson() = JSONObject().apply {
        put("id", id)
        put("globalId", globalId)
        put("spaceId", spaceId)
        put("syncRevision", syncRevision)
        put("syncState", syncState)
        put("title", title)
        put("note", note)
        put("priority", priority)
        put("dueDate", dueDate)
        put("dueTime", dueTime)
        put("estimateMinutes", estimateMinutes)
        put("status", status)
        put("completedAt", completedAt)
        put("postponeCount", postponeCount)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    private fun HabitEntryEntity.toJson() = JSONObject().apply {
        put("habitId", habitId)
        put("date", date)
        put("status", status)
        put("value", value)
        put("note", note)
    }

    private companion object {
        const val FORMAT_VERSION = 1
        /** Диапазон epochDay с запасом: от 1970 до 2100 года. */
        const val MIN_DAY = 0
        const val MAX_DAY = 47_500
    }
}

private fun Cursor.backupValue(index: Int): Any = when (getType(index)) {
    Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
    Cursor.FIELD_TYPE_INTEGER -> getLong(index)
    Cursor.FIELD_TYPE_FLOAT -> getDouble(index)
    Cursor.FIELD_TYPE_STRING -> getString(index)
    Cursor.FIELD_TYPE_BLOB -> JSONObject().put(
        BLOB_MARKER,
        Base64.encodeToString(getBlob(index), Base64.NO_WRAP)
    )
    else -> error("Неизвестный SQLite-тип")
}

private fun androidx.sqlite.db.SupportSQLiteStatement.bindBackupValue(index: Int, value: Any?) {
    when {
        value == null || value == JSONObject.NULL -> bindNull(index)
        value is Int -> bindLong(index, value.toLong())
        value is Long -> bindLong(index, value)
        value is Number -> bindDouble(index, value.toDouble())
        value is String -> bindString(index, value)
        value is JSONObject && value.has(BLOB_MARKER) ->
            bindBlob(index, Base64.decode(value.getString(BLOB_MARKER), Base64.DEFAULT))
        else -> error("Повреждённое значение архива")
    }
}

private fun String.quoted(): String = "\"${replace("\"", "\"\"")}\""

private const val BLOB_MARKER = "__ashwake_blob_base64"

private fun JSONObject.array(name: String): JSONArray = optJSONArray(name) ?: JSONArray()
private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)
private fun JSONArray.strings(): List<String> = (0 until length()).map(::getString)
private fun JSONObject.stringOrNull(name: String): String? =
    if (!has(name) || isNull(name)) null else getString(name)
private fun JSONObject.intOrNull(name: String): Int? =
    if (!has(name) || isNull(name)) null else getInt(name)
private fun JSONObject.longOrNull(name: String): Long? =
    if (!has(name) || isNull(name)) null else getLong(name)
private fun JSONObject.doubleOrNull(name: String): Double? =
    if (!has(name) || isNull(name)) null else getDouble(name)
