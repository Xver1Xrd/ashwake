package dev.ashwake.data.assets

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ashwake.core.model.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Шаблон типовой задачи с предзаполненными подзадачами. */
data class TaskPreset(
    val title: String,
    val priority: Priority = Priority.P2,
    val estimateMinutes: Int? = null,
    val subtasks: List<String> = emptyList()
)

/** Загрузчик шаблонов задач из assets/presets/tasks.json. */
@Singleton
class TaskPresetLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile
    private var cache: List<TaskPreset>? = null

    suspend fun load(): List<TaskPreset> {
        cache?.let { return it }
        return withContext(Dispatchers.IO) {
            val parsed = runCatching { parse() }.getOrDefault(emptyList())
            cache = parsed
            parsed
        }
    }

    private fun parse(): List<TaskPreset> {
        val raw = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        val array = JSONObject(raw).getJSONArray("tasks")

        return (0 until array.length()).map { index ->
            val obj = array.getJSONObject(index)
            val subtasksArray = obj.optJSONArray("subtasks")
            val subtasks = subtasksArray?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList()

            TaskPreset(
                title = obj.getString("title"),
                priority = runCatching { Priority.valueOf(obj.optString("priority", "P2")) }.getOrDefault(Priority.P2),
                estimateMinutes = if (obj.isNull("estimateMinutes")) null else obj.optInt("estimateMinutes"),
                subtasks = subtasks
            )
        }
    }

    private companion object {
        const val ASSET_PATH = "presets/tasks.json"
    }
}
