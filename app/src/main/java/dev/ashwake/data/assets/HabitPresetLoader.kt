package dev.ashwake.data.assets

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ashwake.core.model.Sphere
import dev.ashwake.core.model.WeekdayMask
import dev.ashwake.domain.model.habits.Habit
import dev.ashwake.domain.model.habits.HabitSchedule
import dev.ashwake.domain.model.habits.HabitScheduleType
import dev.ashwake.domain.model.habits.HabitType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton

data class HabitPresetCategory(
    val sphere: Sphere,
    val title: String,
    val habits: List<Habit>
)

/**
 * Каталог готовых привычек из `assets/presets/habits.json` (п. 3).
 *
 * Парсится через встроенный org.json: тянуть сериализатор ради одного файла,
 * который читается один раз за сессию, незачем.
 */
@Singleton
class HabitPresetLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile
    private var cache: List<HabitPresetCategory>? = null

    suspend fun load(): List<HabitPresetCategory> {
        cache?.let { return it }
        return withContext(Dispatchers.IO) {
            val parsed = runCatching { parse() }.getOrDefault(emptyList())
            cache = parsed
            parsed
        }
    }

    private fun parse(): List<HabitPresetCategory> {
        val raw = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        val categories = JSONObject(raw).getJSONArray("categories")

        return (0 until categories.length()).map { index ->
            val category = categories.getJSONObject(index)
            val sphere = Sphere.valueOf(category.getString("sphere"))
            val habits = category.getJSONArray("habits")

            HabitPresetCategory(
                sphere = sphere,
                title = category.getString("title"),
                habits = (0 until habits.length()).map { habitIndex ->
                    habitOf(habits.getJSONObject(habitIndex), sphere)
                }
            )
        }
    }

    private fun habitOf(json: JSONObject, sphere: Sphere): Habit {
        val scheduleType = HabitScheduleType.valueOf(json.optString("schedule", "DAILY"))
        val weekdays = json.optJSONArray("weekdays")?.let { array ->
            (0 until array.length()).fold(WeekdayMask.NONE) { mask, i ->
                mask.with(DayOfWeek.valueOf(array.getString(i)))
            }
        } ?: WeekdayMask.EVERY_DAY

        return Habit(
            name = json.getString("name"),
            type = HabitType.valueOf(json.optString("type", "CHECK")),
            sphere = sphere,
            targetValue = json.optDouble("target", 1.0).toFloat(),
            unitName = json.optString("unit").takeIf { it.isNotBlank() },
            minimumValue = if (json.has("minimum")) json.getDouble("minimum").toFloat() else null,
            schedule = HabitSchedule(
                type = scheduleType,
                timesPerWeek = json.optInt("timesPerWeek", 3),
                weekdays = weekdays
            )
        )
    }

    private companion object {
        const val ASSET_PATH = "presets/habits.json"
    }
}
