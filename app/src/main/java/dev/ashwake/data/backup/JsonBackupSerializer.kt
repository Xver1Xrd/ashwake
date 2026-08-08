package dev.ashwake.data.backup

import dev.ashwake.data.db.AshwakeDatabase
import dev.ashwake.data.db.dao.abstinence.AbstinenceDao
import dev.ashwake.data.db.dao.character.CharacterDao
import dev.ashwake.data.db.dao.habits.HabitDao
import dev.ashwake.data.db.dao.ritual.RitualDao
import dev.ashwake.data.db.dao.tasks.TaskDao
import dev.ashwake.data.db.entity.habits.HabitEntryEntity
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

    private fun TaskEntity.toJson() = JSONObject().apply {
        put("id", id)
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
