package dev.ashwake.data.backup

import android.database.Cursor
import android.util.Base64
import androidx.room.withTransaction
import dev.ashwake.data.db.AshwakeDatabase
import dev.ashwake.data.db.dao.abstinence.AbstinenceDao
import dev.ashwake.data.db.dao.backup.BackupDao
import dev.ashwake.data.db.dao.character.CharacterDao
import dev.ashwake.data.db.dao.habits.HabitDao
import dev.ashwake.data.db.dao.ritual.RitualDao
import dev.ashwake.data.db.dao.tasks.TaskDao
import dev.ashwake.data.db.dao.tasks.TaskAttachmentDao
import dev.ashwake.data.db.entity.abstinence.AbstinenceAttemptEntity
import dev.ashwake.data.db.entity.abstinence.AbstinenceEntity
import dev.ashwake.data.db.entity.character.AchievementEntity
import dev.ashwake.data.db.entity.character.CharacterStatEntity
import dev.ashwake.data.db.entity.character.DailyChestEntity
import dev.ashwake.data.db.entity.character.EquippedItemEntity
import dev.ashwake.data.db.entity.character.MaterialInventoryEntity
import dev.ashwake.data.db.entity.character.OwnedItemEntity
import dev.ashwake.data.db.entity.character.WalletEntity
import dev.ashwake.data.db.entity.habits.HabitEntity
import dev.ashwake.data.db.entity.habits.HabitEntryEntity
import dev.ashwake.data.db.entity.ritual.DailyReviewEntity
import dev.ashwake.data.db.entity.tasks.TaskEntity
import dev.ashwake.data.db.entity.tasks.TaskAttachmentEntity
import dev.ashwake.data.icons.IconStore
import dev.ashwake.data.attachments.TaskAttachmentStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

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
    private val icons: IconStore,
    private val taskDao: TaskDao,
    private val attachmentDao: TaskAttachmentDao,
    private val attachmentStore: TaskAttachmentStore,
    private val habitDao: HabitDao,
    private val abstinenceDao: AbstinenceDao,
    private val ritualDao: RitualDao,
    private val characterDao: CharacterDao,
    private val backupDao: BackupDao
) {

    suspend fun export(): Pair<String, BackupContents> {
        val tasks = taskDao.allTasks()
        val attachments = attachmentDao.allAttachments()
        val habits = habitDao.observeHabits(1).first()
        val entries = habitDao.observeEntriesInRange(MIN_DAY, MAX_DAY).first()
        val abstinences = abstinenceDao.observeAll(1).first()
        val attempts = abstinenceDao.observeAttempts().first()
        val reviews = ritualDao.observeReviews(MIN_DAY, MAX_DAY).first()
        val wallet = characterDao.wallet()
        val owned = characterDao.observeOwned().first()
        val equipped = characterDao.equipped()
        val stats = characterDao.observeStats().first()
        val materials = characterDao.observeMaterials().first()
        val achievements = characterDao.observeAchievements().first()
        var attachmentBytes = 0L
        val attachmentFiles = attachments.map { attachment ->
            val file = attachmentStore.file(attachment.storedName)
            require(file.isFile) { "Не найдено вложение ${attachment.displayName}" }
            require(file.length() == attachment.sizeBytes) {
                "Размер вложения ${attachment.displayName} не совпадает с базой"
            }
            attachmentBytes += file.length()
            require(attachmentBytes <= TaskAttachmentStore.MAX_TOTAL_BYTES) {
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
            put("taskAttachments", JSONArray(attachments.map { it.toJson() }))
            put("attachmentFiles", JSONArray(attachmentFiles))
            put("habits", JSONArray(habits.map { habit ->
                JSONObject().apply {
                    put("id", habit.id)
                    put("name", habit.name)
                    put("icon", habit.icon)
                    put("iconPath", habit.iconPath)
                    put("color", habit.color)
                    put("type", habit.type)
                    put("sphere", habit.sphere)
                    put("targetValue", habit.targetValue)
                    put("unitName", habit.unitName)
                    put("minimumValue", habit.minimumValue)
                    put("scheduleType", habit.scheduleType)
                    put("timesPerWeek", habit.timesPerWeek)
                    put("weekdaysMask", habit.weekdaysMask)
                    put("intervalAnchor", habit.intervalAnchor)
                    put("biweeklyAnchor", habit.biweeklyAnchor)
                    put("reminderTime", habit.reminderTime)
                    put("freezeQuotaPerMonth", habit.freezeQuotaPerMonth)
                    put("position", habit.position)
                    put("archived", habit.archived)
                    put("createdAt", habit.createdAt)
                }
            }))
            put("habitEntries", JSONArray(entries.map { it.toJson() }))
            put("abstinences", JSONArray(abstinences.map { item ->
                JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("icon", item.icon)
                    put("iconPath", item.iconPath)
                    put("paletteId", item.paletteId)
                    put("mode", item.mode)
                    put("gentlePenaltyDays", item.gentlePenaltyDays)
                    put("milestonesEnabled", item.milestonesEnabled)
                    put("motivationText", item.motivationText)
                    put("baselineUnitName", item.baselineUnitName)
                    put("baselineUnitsPerDay", item.baselineUnitsPerDay)
                    put("baselineCostPerUnit", item.baselineCostPerUnit)
                    put("baselineCurrency", item.baselineCurrency)
                    put("stickyNotification", item.stickyNotification)
                    put("substanceWarningAck", item.substanceWarningAck)
                    put("position", item.position)
                    put("archived", item.archived)
                    put("createdAt", item.createdAt)
                }
            }))
            put("abstinenceAttempts", JSONArray(attempts.map { attempt ->
                JSONObject().apply {
                    put("id", attempt.id)
                    put("abstinenceId", attempt.abstinenceId)
                    put("ordinal", attempt.ordinal)
                    put("startedAt", attempt.startedAt)
                    put("endedAt", attempt.endedAt)
                    put("relapseReasonId", attempt.relapseReasonId)
                    put("note", attempt.note)
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
                put("level", wallet?.level ?: 1)
            })
            // Не список id, а записи целиком: апгрейд предмета — это вложенные
            // монеты, и терять его при восстановлении нельзя (сценарий 18)
            put("ownedItems", JSONArray(owned.map { item ->
                JSONObject().apply {
                    put("itemId", item.itemId)
                    put("acquiredAt", item.acquiredAt)
                    put("source", item.source)
                    put("upgradeLevel", item.upgradeLevel)
                    put("favorite", item.favorite)
                }
            }))
            put("equippedItems", JSONObject().apply {
                equipped.forEach { put(it.slot, it.itemId) }
            })
            // Картинки значков едут внутри архива: имя файла без самого файла
            // восстанавливает пустой кружок, а починить его будет уже нечем
            put("icons", JSONObject().apply {
                icons.exportAll(backupDao.usedIconPaths().toSet())
                    .forEach { (name, encoded) -> put(name, encoded) }
            })
            put("stats", JSONObject().apply {
                stats.forEach { stat ->
                    put(stat.stat, JSONObject().apply {
                        put("points", stat.points)
                        put("value", stat.value)
                    })
                }
            })
            put("materials", JSONArray(materials.map { mat ->
                JSONObject().apply {
                    put("materialId", mat.materialId)
                    put("amount", mat.amount)
                }
            }))
            put("achievements", JSONArray(achievements.map { ach ->
                JSONObject().apply {
                    put("id", ach.id)
                    put("unlockedAt", ach.unlockedAt)
                    put("progress", ach.progress)
                }
            }))
            // Полный снимок сохраняет проекты, теги, рутины, таймбоксы,
            // персонажа и остальные таблицы, которые не входят в краткий отчёт выше.
            put("database", exportDatabase())
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
        BackupContents(
            tasks = root.optJSONArray("tasks")?.length() ?: 0,
            habits = root.optJSONArray("habits")?.length() ?: 0,
            habitEntries = root.optJSONArray("habitEntries")?.length() ?: 0,
            abstinences = root.optJSONArray("abstinences")?.length() ?: 0,
            reviews = root.optJSONArray("dailyReviews")?.length() ?: 0
        )
    }.getOrNull()

    /**
     * Запись архива в базу — полная замена данных.
     *
     * Всё внутри одной транзакции: наполовину восстановленная база хуже,
     * чем не восстановленная вовсе. Если разбор упадёт на середине,
     * откатится и очистка.
     *
     * Возвращает то, что реально записано, — чтобы отчёт показывал факт,
     * а не намерение.
     */
    suspend fun import(json: String): BackupContents {
        val root = JSONObject(json)
        root.optJSONObject("database")?.let { snapshot ->
            return importDatabaseSnapshot(root, snapshot)
        }

        val tasks = root.optJSONArray("tasks").objects().map { it.toTaskEntity() }
        val attachments = root.optJSONArray("taskAttachments").objects()
            .map { it.toTaskAttachmentEntity() }
        val habits = root.optJSONArray("habits").objects().map { it.toHabitEntity() }
        val entries = root.optJSONArray("habitEntries").objects().map { it.toEntryEntity() }
        val abstinences = root.optJSONArray("abstinences").objects().map { it.toAbstinenceEntity() }
        val attempts = root.optJSONArray("abstinenceAttempts").objects().map { it.toAttemptEntity() }
        val reviews = root.optJSONArray("dailyReviews").objects().map { it.toReviewEntity() }
        val staging = stageAttachmentFiles(
            root = root,
            expected = attachments.associate { it.storedName to it.sizeBytes }
        )
        var previousDirectory: File? = null
        var filesReplaced = false

        try {
            val contents = db.withTransaction {
                // Картинки кладём до записи строк: иначе между восстановлением задачи
                // и появлением файла список успевает нарисовать пустой кружок
                root.optJSONObject("icons")?.let { node ->
                    icons.importAll(node.keys().asSequence().associateWith { node.optString(it) })
                }

                // Порядок очистки: сначала ссылающиеся таблицы, потом те, на которые
                // ссылаются. Полагаться на CASCADE при полной замене нельзя
                backupDao.clearHabitEntries()
                backupDao.clearHabitFreezes()
                backupDao.clearHabitPauses()
                backupDao.clearHabitAnchors()
                backupDao.clearHabits()

                backupDao.clearTaskTags()
                backupDao.clearPostponements()
                backupDao.clearTaskAttachments()
                backupDao.clearTasks()

                backupDao.clearAttempts()
                backupDao.clearCravings()
                backupDao.clearMilestones()
                backupDao.clearAbstinences()

                backupDao.clearReviewTopTasks()
                backupDao.clearReviews()

                backupDao.clearEquipped()
                backupDao.clearOwned()
                backupDao.clearStats()
                backupDao.clearMaterials()
                backupDao.clearAchievements()
                backupDao.clearChests()
                backupDao.clearLedger()

                // Старые архивы не содержали проектов и правил повтора. Их id
                // могли остаться в задачах, но после очистки таблиц на них уже
                // некому ссылаться — именно это давало SQLITE_CONSTRAINT_FOREIGNKEY.
                // Сохраняем сами задачи и их иерархию, а отсутствующие ссылки
                // честно обнуляем.
                backupDao.insertTasks(legacyTasksForRestore(tasks))
                backupDao.insertTaskAttachments(attachments)
                backupDao.insertHabits(habits)
                backupDao.insertHabitEntries(entries)
                backupDao.insertAbstinences(abstinences)
                backupDao.insertAttempts(attempts)
                backupDao.insertReviews(reviews)

                root.optJSONObject("wallet")?.let { wallet ->
                    backupDao.upsertWallet(
                        WalletEntity(
                            id = 1,
                            coins = wallet.optLong("coins"),
                            xp = wallet.optLong("xp"),
                            level = wallet.optInt("level", 1)
                        )
                    )
                }

                backupDao.insertOwned(readOwnedItems(root))

                root.optJSONObject("equippedItems")?.let { equipped ->
                    backupDao.insertEquipped(
                        equipped.keys().asSequence().map { slot ->
                            EquippedItemEntity(slot = slot, itemId = equipped.getString(slot))
                        }.toList()
                    )
                }

                root.optJSONObject("stats")?.let { stats ->
                    backupDao.insertStats(
                        stats.keys().asSequence().map { key ->
                            // Формат 1 хранил очки числом, формат 2 — объектом
                            when (val value = stats.get(key)) {
                                is JSONObject -> CharacterStatEntity(
                                    stat = key,
                                    points = value.optLong("points"),
                                    value = value.optInt("value")
                                )
                                else -> CharacterStatEntity(
                                    stat = key,
                                    points = (value as? Number)?.toLong() ?: 0L
                                )
                            }
                        }.toList()
                    )
                }

                root.optJSONArray("materials")?.let { mats ->
                    backupDao.insertMaterials(
                        mats.objects().map {
                            MaterialInventoryEntity(
                                materialId = it.getString("materialId"),
                                amount = it.optInt("amount")
                            )
                        }
                    )
                }

                root.optJSONArray("achievements")?.let { achievements ->
                    backupDao.insertAchievements(
                        achievements.objects().map {
                            AchievementEntity(
                                id = it.getString("id"),
                                unlockedAt = it.optLongOrNull("unlockedAt"),
                                progress = it.optDouble("progress", 0.0).toFloat()
                            )
                        }
                    )
                }

                previousDirectory = replaceAttachmentFiles(staging)
                filesReplaced = true

                BackupContents(
                    tasks = tasks.size,
                    habits = habits.size,
                    habitEntries = entries.size,
                    abstinences = abstinences.size,
                    reviews = reviews.size
                )
            }
            previousDirectory?.deleteRecursively()
            return contents
        } catch (error: Throwable) {
            if (filesReplaced) {
                val previous = previousDirectory
                if (previous != null) {
                    val current = File(previous.parentFile, TaskAttachmentStore.DIRECTORY)
                    current.deleteRecursively()
                    previous.renameTo(current)
                }
            }
            throw error
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Восстанавливает полный снимок новых архивов; явные массивы остаются для старых. */
    private suspend fun importDatabaseSnapshot(
        root: JSONObject,
        snapshot: JSONObject
    ): BackupContents {
        val expectedAttachments = attachmentSizes(snapshot)
        val staging = stageAttachmentFiles(root, expectedAttachments)
        validateDatabaseSnapshot(snapshot)
        validateAttachmentSnapshot(expectedAttachments, staging)

        val contents = BackupContents(
            tasks = root.optJSONArray("tasks")?.length() ?: 0,
            habits = root.optJSONArray("habits")?.length() ?: 0,
            habitEntries = root.optJSONArray("habitEntries")?.length() ?: 0,
            abstinences = root.optJSONArray("abstinences")?.length() ?: 0,
            reviews = root.optJSONArray("dailyReviews")?.length() ?: 0
        )
        var previousDirectory: File? = null
        var filesReplaced = false

        try {
            root.optJSONObject("icons")?.let { node ->
                icons.importAll(node.keys().asSequence().associateWith { node.optString(it) })
            }
            db.withTransaction {
                restoreDatabase(snapshot)
                previousDirectory = replaceAttachmentFiles(staging)
                filesReplaced = true
            }
            previousDirectory?.deleteRecursively()
            return contents
        } catch (error: Throwable) {
            if (filesReplaced) {
                restorePreviousAttachmentDirectory(previousDirectory)
            }
            throw error
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun stageAttachmentFiles(
        root: JSONObject,
        expected: Map<String, Long>
    ): File {
        val current = attachmentStore.directory
        val staging = File(current.parentFile, ".attachments-restore-${UUID.randomUUID()}")
        check(staging.mkdirs()) { "Не удалось подготовить восстановление вложений" }
        val seen = mutableSetOf<String>()
        var totalBytes = 0L

        try {
            root.optJSONArray("attachmentFiles").objects().forEach { item ->
                val storedName = item.getString("storedName")
                require(TaskAttachmentStore.STORED_NAME.matches(storedName)) {
                    "Архив содержит некорректное имя вложения"
                }
                require(seen.add(storedName)) { "Архив содержит дубликат вложения" }
                val expectedSize = expected[storedName]
                    ?: error("Архив содержит вложение без метаданных")
                val bytes = Base64.decode(item.getString("data"), Base64.DEFAULT)
                require(bytes.size.toLong() == expectedSize) { "Размер вложения в архиве не совпадает" }
                require(bytes.size <= TaskAttachmentStore.MAX_ATTACHMENT_BYTES) {
                    "Вложение в архиве больше 25 МБ"
                }
                totalBytes += bytes.size
                require(totalBytes <= TaskAttachmentStore.MAX_TOTAL_BYTES) {
                    "Вложения в архиве занимают больше 64 МБ"
                }
                File(staging, storedName).writeBytes(bytes)
            }
            require(seen == expected.keys) { "В архиве отсутствуют файлы вложений" }
            return staging
        } catch (error: Throwable) {
            staging.deleteRecursively()
            throw error
        }
    }

    /** Меняет каталог атомарными переименованиями и возвращает прежний каталог. */
    private fun replaceAttachmentFiles(staging: File): File {
        val current = attachmentStore.directory
        val previous = File(current.parentFile, ".attachments-previous-${UUID.randomUUID()}")
        check(current.renameTo(previous)) { "Не удалось заменить папку вложений" }
        if (!staging.renameTo(current)) {
            previous.renameTo(current)
            error("Не удалось восстановить вложения")
        }
        return previous
    }

    private fun restorePreviousAttachmentDirectory(previous: File?) {
        if (previous == null) return
        val current = File(previous.parentFile, TaskAttachmentStore.DIRECTORY)
        current.deleteRecursively()
        previous.renameTo(current)
    }

    private fun attachmentSizes(snapshot: JSONObject): Map<String, Long> {
        val attachmentTable = snapshot.optJSONArray("tables").objects()
            .firstOrNull { it.getString("name") == "task_attachments" }
            ?: return emptyMap()
        val columns = attachmentTable.getJSONArray("columns").strings()
        val nameIndex = columns.indexOf("storedName")
        val sizeIndex = columns.indexOf("sizeBytes")
        require(nameIndex >= 0 && sizeIndex >= 0) { "Повреждена таблица вложений" }
        val rows = attachmentTable.getJSONArray("rows")
        return buildMap {
            repeat(rows.length()) { index ->
                val row = rows.getJSONArray(index)
                val storedName = row.getString(nameIndex)
                require(put(storedName, row.getLong(sizeIndex)) == null) {
                    "Архив содержит дубликат вложения"
                }
            }
        }
    }

    private fun validateAttachmentSnapshot(expected: Map<String, Long>, staging: File) {
        val staged = staging.listFiles().orEmpty().associate { it.name to it.length() }
        require(expected == staged) { "Файлы вложений не совпадают с метаданными архива" }
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
            put("schemaVersion", sql.version)
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

    /** Проверяет имена до удаления рабочих данных. */
    private fun validateDatabaseSnapshot(snapshot: JSONObject) {
        require(snapshot.optInt("schemaVersion", 0) in 1..db.openHelper.readableDatabase.version) {
            "Архив создан несовместимой версией базы"
        }
        val sql = db.openHelper.readableDatabase
        val currentTables = currentTableNames()
        val currentColumns = currentTables.associateWith { table ->
            sql.query("PRAGMA table_info(${table.quoted()})").use { cursor ->
                buildSet {
                    while (cursor.moveToNext()) {
                        add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                    }
                }
            }
        }
        val seenTables = mutableSetOf<String>()
        snapshot.optJSONArray("tables").objects().forEach { table ->
            val name = table.getString("name")
            require(name in currentTables && seenTables.add(name)) {
                "Архив содержит неизвестную или повторяющуюся таблицу"
            }
            val columns = table.getJSONArray("columns").strings()
            require(
                columns.isNotEmpty() &&
                    columns.distinct().size == columns.size &&
                    columns.all { it in currentColumns.getValue(name) }
            ) { "Архив содержит неизвестные или повторяющиеся столбцы" }
        }
    }

    /** Вызывается только внутри общей Room-транзакции восстановления. */
    private fun restoreDatabase(snapshot: JSONObject) {
        val sql = db.openHelper.writableDatabase
        val currentTables = currentTableNames()
        sql.execSQL("PRAGMA defer_foreign_keys = ON")
        val orderedCurrentTables = tablesInDependencyOrder(currentTables)
        orderedCurrentTables.asReversed().forEach { table ->
            sql.execSQL("DELETE FROM ${table.quoted()}")
        }

        val snapshotTables = snapshot.getJSONArray("tables").objects()
            .associateBy { it.getString("name") }
        tablesInDependencyOrder(snapshotTables.keys).forEach { name ->
            val table = snapshotTables.getValue(name)
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

    /** Родительские таблицы идут раньше дочерних и при вставке, и при удалении наоборот. */
    private fun tablesInDependencyOrder(tables: Set<String>): List<String> {
        val sql = db.openHelper.readableDatabase
        val dependencies = tables.associateWith { table ->
            sql.query("PRAGMA foreign_key_list(${table.quoted()})").use { cursor ->
                buildSet {
                    val column = cursor.getColumnIndexOrThrow("table")
                    while (cursor.moveToNext()) {
                        cursor.getString(column)
                            .takeIf { it in tables && it != table }
                            ?.let(::add)
                    }
                }
            }
        }
        val remaining = tables.toMutableSet()
        val result = mutableListOf<String>()
        while (remaining.isNotEmpty()) {
            val ready = remaining.filter { table ->
                dependencies.getValue(table).none { it in remaining }
            }.sorted()
            require(ready.isNotEmpty()) { "В схеме базы найдена циклическая внешняя ссылка" }
            result += ready
            remaining.removeAll(ready.toSet())
        }
        return result
    }

    private fun legacyTasksForRestore(tasks: List<TaskEntity>): List<TaskEntity> {
        val byId = tasks.associateBy { it.id }
        val remaining = tasks.associateBy { it.id }.toMutableMap()
        val result = mutableListOf<TaskEntity>()
        while (remaining.isNotEmpty()) {
            val ready = remaining.values.filter { task ->
                task.parentTaskId == null || task.parentTaskId !in remaining
            }
            if (ready.isEmpty()) {
                // Повреждённая циклическая иерархия не должна ломать весь
                // бэкап: восстанавливаем задачи без циклической связи.
                result += remaining.values.sortedBy { it.id }.map {
                    it.copy(projectId = null, recurrenceId = null, parentTaskId = null)
                }
                break
            }
            ready.sortedBy { it.id }.forEach { task ->
                result += task.copy(
                    projectId = null,
                    recurrenceId = null,
                    parentTaskId = task.parentTaskId?.takeIf { parent -> parent in byId }
                )
                remaining.remove(task.id)
            }
        }
        return result
    }

    private fun currentTableNames(): Set<String> = db.openHelper.readableDatabase.query(
        "SELECT name FROM sqlite_master WHERE type='table' " +
            "AND name NOT LIKE 'sqlite_%' " +
            "AND name NOT IN ('android_metadata', 'room_master_table')"
    ).use { cursor ->
        buildSet {
            while (cursor.moveToNext()) add(cursor.getString(0))
        }
    }

    /**
     * Предметы. В первой версии формата это был массив строк-id, во второй —
     * записи с уровнем апгрейда. Читаются обе: архив, сделанный до обновления,
     * должен восстанавливаться, иначе резервная копия ничего не гарантирует.
     */
    private fun readOwnedItems(root: JSONObject): List<OwnedItemEntity> {
        val array = root.optJSONArray("ownedItems") ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            when (val raw = array.get(index)) {
                is JSONObject -> OwnedItemEntity(
                    itemId = raw.getString("itemId"),
                    acquiredAt = raw.optLong("acquiredAt"),
                    source = raw.optString("source", "restore"),
                    upgradeLevel = raw.optInt("upgradeLevel"),
                    favorite = raw.optBoolean("favorite")
                )
                is String -> OwnedItemEntity(
                    itemId = raw,
                    acquiredAt = 0L,
                    source = "restore"
                )
                else -> null
            }
        }
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList()
        else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONArray.strings(): List<String> =
        (0 until length()).map(::getString)

    private fun JSONObject.toTaskEntity() = TaskEntity(
        id = optLong("id"),
        globalId = optStringOrNull("globalId"),
        spaceId = optStringOrNull("spaceId"),
        syncRevision = optLong("syncRevision"),
        syncState = optString("syncState", "LOCAL"),
        title = optString("title"),
        emoji = optStringOrNull("emoji"),
        iconPath = optStringOrNull("iconPath"),
        note = optStringOrNull("note"),
        projectId = optLongOrNull("projectId"),
        parentTaskId = optLongOrNull("parentTaskId"),
        priority = optString("priority", "P4"),
        dueDate = optIntOrNull("dueDate"),
        dueTime = optIntOrNull("dueTime"),
        estimateMinutes = optIntOrNull("estimateMinutes"),
        status = optString("status", "ACTIVE"),
        completedAt = optLongOrNull("completedAt"),
        position = optInt("position", 0),
        quadrantOverride = optStringOrNull("quadrantOverride"),
        recurrenceId = optLongOrNull("recurrenceId"),
        seriesId = optStringOrNull("seriesId"),
        isTemplate = optBoolean("isTemplate", false),
        persistentReminderMinutes = optIntOrNull("persistentReminderMinutes"),
        postponeCount = optInt("postponeCount"),
        lastPostponedAt = optLongOrNull("lastPostponedAt"),
        sourceLink = optStringOrNull("sourceLink"),
        delegatedTo = optStringOrNull("delegatedTo"),
        createdAt = optLong("createdAt"),
        updatedAt = optLong("updatedAt", optLong("createdAt"))
    )

    private fun JSONObject.toTaskAttachmentEntity() = TaskAttachmentEntity(
        id = optLong("id"),
        taskId = optLong("taskId"),
        storedName = getString("storedName"),
        displayName = getString("displayName").take(180),
        mimeType = optString("mimeType", "application/octet-stream").take(120),
        sizeBytes = optLong("sizeBytes"),
        kind = optString("kind", "FILE"),
        createdAt = optLong("createdAt")
    )

    private fun JSONObject.toHabitEntity() = HabitEntity(
        id = optLong("id"),
        name = optString("name"),
        icon = optStringOrNull("icon"),
        iconPath = optStringOrNull("iconPath"),
        color = optInt("color", 0),
        type = optString("type", "CHECK"),
        sphere = optString("sphere", "HEALTH"),
        targetValue = optDouble("targetValue", 1.0).toFloat(),
        unitName = optStringOrNull("unitName"),
        minimumValue = if (isNull("minimumValue")) null else optDouble("minimumValue").toFloat(),
        scheduleType = optString("scheduleType", "DAILY"),
        timesPerWeek = optInt("timesPerWeek", 3),
        weekdaysMask = optInt("weekdaysMask", 0b1111111),
        intervalAnchor = optIntOrNull("intervalAnchor"),
        biweeklyAnchor = optIntOrNull("biweeklyAnchor"),
        reminderTime = optIntOrNull("reminderTime"),
        freezeQuotaPerMonth = optInt("freezeQuotaPerMonth", 3),
        position = optInt("position", 0),
        archived = optBoolean("archived"),
        createdAt = optLong("createdAt")
    )

    private fun JSONObject.toEntryEntity() = HabitEntryEntity(
        id = optLong("id", 0L),
        habitId = optLong("habitId"),
        date = optInt("date"),
        status = optString("status", "DONE"),
        value = optDouble("value").toFloat(),
        note = optStringOrNull("note"),
        completedAt = optLongOrNull("completedAt"),
        skipReasonId = optLongOrNull("skipReasonId"),
        source = optString("source", "MANUAL")
    )

    private fun JSONObject.toAbstinenceEntity() = AbstinenceEntity(
        id = optLong("id"),
        name = optString("name"),
        icon = optStringOrNull("icon"),
        iconPath = optStringOrNull("iconPath"),
        paletteId = optString("paletteId", "default"),
        mode = optString("mode", "STRICT"),
        gentlePenaltyDays = optInt("gentlePenaltyDays", 7),
        milestonesEnabled = optBoolean("milestonesEnabled", true),
        motivationText = optStringOrNull("motivationText"),
        baselineUnitName = optStringOrNull("baselineUnitName"),
        baselineUnitsPerDay = if (isNull("baselineUnitsPerDay")) null
        else optDouble("baselineUnitsPerDay").toFloat(),
        baselineCostPerUnit = if (isNull("baselineCostPerUnit")) null
        else optDouble("baselineCostPerUnit").toFloat(),
        baselineCurrency = optStringOrNull("baselineCurrency"),
        stickyNotification = optBoolean("stickyNotification"),
        substanceWarningAck = optBoolean("substanceWarningAck", false),
        position = optInt("position", 0),
        archived = optBoolean("archived", false),
        createdAt = optLong("createdAt")
    )

    private fun JSONObject.toAttemptEntity() = AbstinenceAttemptEntity(
        id = optLong("id"),
        abstinenceId = optLong("abstinenceId"),
        ordinal = optInt("ordinal", 1),
        startedAt = optLong("startedAt"),
        endedAt = optLongOrNull("endedAt"),
        relapseReasonId = optLongOrNull("relapseReasonId"),
        note = optStringOrNull("note"),
        penaltyDays = optInt("penaltyDays")
    )

    private fun JSONObject.toReviewEntity() = DailyReviewEntity(
        date = optInt("date"),
        dayRating = optIntOrNull("dayRating"),
        mood = optIntOrNull("mood"),
        energy = optIntOrNull("energy"),
        note = optStringOrNull("note"),
        completedAt = optLong("completedAt"),
        completedAs = optString("completedAs", "EVENING")
    )

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (isNull(key)) null else optInt(key)

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (isNull(key)) null else optLong(key)

    private fun TaskEntity.toJson() = JSONObject().apply {
        put("id", id)
        put("globalId", globalId)
        put("spaceId", spaceId)
        put("syncRevision", syncRevision)
        put("syncState", syncState)
        put("title", title)
        put("emoji", emoji)
        put("iconPath", iconPath)
        put("note", note)
        put("projectId", projectId)
        put("parentTaskId", parentTaskId)
        put("priority", priority)
        put("dueDate", dueDate)
        put("dueTime", dueTime)
        put("estimateMinutes", estimateMinutes)
        put("status", status)
        put("completedAt", completedAt)
        put("position", position)
        put("quadrantOverride", quadrantOverride)
        put("recurrenceId", recurrenceId)
        put("seriesId", seriesId)
        put("isTemplate", isTemplate)
        put("persistentReminderMinutes", persistentReminderMinutes)
        put("postponeCount", postponeCount)
        put("lastPostponedAt", lastPostponedAt)
        put("sourceLink", sourceLink)
        put("delegatedTo", delegatedTo)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    private fun TaskAttachmentEntity.toJson() = JSONObject().apply {
        put("id", id)
        put("taskId", taskId)
        put("storedName", storedName)
        put("displayName", displayName)
        put("mimeType", mimeType)
        put("sizeBytes", sizeBytes)
        put("kind", kind)
        put("createdAt", createdAt)
    }

    private fun HabitEntryEntity.toJson() = JSONObject().apply {
        put("id", id)
        put("habitId", habitId)
        put("date", date)
        put("status", status)
        put("value", value)
        put("note", note)
        put("completedAt", completedAt)
        put("skipReasonId", skipReasonId)
        put("source", source)
    }

    private companion object {
        const val FORMAT_VERSION = 3
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
