package dev.ashwake.data.backup

import androidx.room.withTransaction
import dev.ashwake.data.db.AshwakeDatabase
import dev.ashwake.data.db.dao.abstinence.AbstinenceDao
import dev.ashwake.data.db.dao.backup.BackupDao
import dev.ashwake.data.db.dao.character.CharacterDao
import dev.ashwake.data.db.dao.habits.HabitDao
import dev.ashwake.data.db.dao.ritual.RitualDao
import dev.ashwake.data.db.dao.tasks.TaskDao
import dev.ashwake.data.db.entity.abstinence.AbstinenceAttemptEntity
import dev.ashwake.data.db.entity.abstinence.AbstinenceEntity
import dev.ashwake.data.db.entity.character.CharacterStatEntity
import dev.ashwake.data.db.entity.character.EquippedItemEntity
import dev.ashwake.data.db.entity.character.OwnedItemEntity
import dev.ashwake.data.db.entity.character.WalletEntity
import dev.ashwake.data.db.entity.habits.HabitEntity
import dev.ashwake.data.db.entity.habits.HabitEntryEntity
import dev.ashwake.data.db.entity.ritual.DailyReviewEntity
import dev.ashwake.data.db.entity.tasks.TaskEntity
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
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
    private val taskDao: TaskDao,
    private val habitDao: HabitDao,
    private val abstinenceDao: AbstinenceDao,
    private val ritualDao: RitualDao,
    private val characterDao: CharacterDao,
    private val backupDao: BackupDao
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
                    put("icon", item.icon)
                    put("paletteId", item.paletteId)
                    put("milestonesEnabled", item.milestonesEnabled)
                    put("baselineUnitName", item.baselineUnitName)
                    put("baselineUnitsPerDay", item.baselineUnitsPerDay)
                    put("baselineCostPerUnit", item.baselineCostPerUnit)
                    put("baselineCurrency", item.baselineCurrency)
                    put("stickyNotification", item.stickyNotification)
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
            put("stats", JSONObject().apply {
                stats.forEach { stat ->
                    put(stat.stat, JSONObject().apply {
                        put("points", stat.points)
                        put("value", stat.value)
                    })
                }
            })
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
    suspend fun import(json: String): BackupContents = db.withTransaction {
        val root = JSONObject(json)

        val tasks = root.optJSONArray("tasks").objects().map { it.toTaskEntity() }
        val habits = root.optJSONArray("habits").objects().map { it.toHabitEntity() }
        val entries = root.optJSONArray("habitEntries").objects().map { it.toEntryEntity() }
        val abstinences = root.optJSONArray("abstinences").objects().map { it.toAbstinenceEntity() }
        val attempts = root.optJSONArray("abstinenceAttempts").objects().map { it.toAttemptEntity() }
        val reviews = root.optJSONArray("dailyReviews").objects().map { it.toReviewEntity() }

        // Порядок очистки: сначала ссылающиеся таблицы, потом те, на которые
        // ссылаются. Полагаться на CASCADE при полной замене нельзя
        backupDao.clearHabitEntries()
        backupDao.clearHabitFreezes()
        backupDao.clearHabitPauses()
        backupDao.clearHabitAnchors()
        backupDao.clearHabits()

        backupDao.clearTaskTags()
        backupDao.clearPostponements()
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
        backupDao.clearLedger()

        backupDao.insertTasks(tasks)
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

        BackupContents(
            tasks = tasks.size,
            habits = habits.size,
            habitEntries = entries.size,
            abstinences = abstinences.size,
            reviews = reviews.size
        )
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

    private fun JSONObject.toTaskEntity() = TaskEntity(
        id = optLong("id"),
        title = optString("title"),
        emoji = optStringOrNull("emoji"),
        iconPath = optStringOrNull("iconPath"),
        note = optStringOrNull("note"),
        priority = optString("priority", "P4"),
        dueDate = optIntOrNull("dueDate"),
        dueTime = optIntOrNull("dueTime"),
        estimateMinutes = optIntOrNull("estimateMinutes"),
        status = optString("status", "ACTIVE"),
        completedAt = optLongOrNull("completedAt"),
        postponeCount = optInt("postponeCount"),
        sourceLink = optStringOrNull("sourceLink"),
        createdAt = optLong("createdAt"),
        updatedAt = optLong("createdAt")
    )

    private fun JSONObject.toHabitEntity() = HabitEntity(
        id = optLong("id"),
        name = optString("name"),
        type = optString("type", "CHECK"),
        sphere = optString("sphere", "HEALTH"),
        targetValue = optDouble("targetValue", 1.0).toFloat(),
        unitName = optStringOrNull("unitName"),
        minimumValue = if (isNull("minimumValue")) null else optDouble("minimumValue").toFloat(),
        scheduleType = optString("scheduleType", "DAILY"),
        timesPerWeek = optInt("timesPerWeek", 3),
        weekdaysMask = optInt("weekdaysMask", 0b1111111),
        archived = optBoolean("archived"),
        createdAt = optLong("createdAt")
    )

    private fun JSONObject.toEntryEntity() = HabitEntryEntity(
        habitId = optLong("habitId"),
        date = optInt("date"),
        status = optString("status", "DONE"),
        value = optDouble("value").toFloat(),
        note = optStringOrNull("note"),
        source = optString("source", "MANUAL")
    )

    private fun JSONObject.toAbstinenceEntity() = AbstinenceEntity(
        id = optLong("id"),
        name = optString("name"),
        icon = optStringOrNull("icon"),
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
        put("title", title)
        put("emoji", emoji)
        put("iconPath", iconPath)
        put("note", note)
        put("priority", priority)
        put("dueDate", dueDate)
        put("dueTime", dueTime)
        put("estimateMinutes", estimateMinutes)
        put("status", status)
        put("completedAt", completedAt)
        put("postponeCount", postponeCount)
        put("createdAt", createdAt)
    }

    private fun HabitEntryEntity.toJson() = JSONObject().apply {
        put("habitId", habitId)
        put("date", date)
        put("status", status)
        put("value", value)
        put("note", note)
    }

    private companion object {
        const val FORMAT_VERSION = 2
        /** Диапазон epochDay с запасом: от 1970 до 2100 года. */
        const val MIN_DAY = 0
        const val MAX_DAY = 47_500
    }
}
