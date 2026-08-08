package dev.ashwake.data.assets

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Шаблон рутины: имя и шаги как пары «заголовок → секунды». */
data class RoutinePreset(
    val name: String,
    val steps: List<Pair<String, Int>>
) {
    val totalSeconds: Int get() = steps.sumOf { it.second }
}

/** Готовые рутины из assets: утро, вечер, тренировка, глубокая работа (п. 6). */
@Singleton
class RoutinePresetLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile
    private var cache: List<RoutinePreset>? = null

    suspend fun load(): List<RoutinePreset> {
        cache?.let { return it }
        return withContext(Dispatchers.IO) {
            val parsed = runCatching { parse() }.getOrDefault(emptyList())
            cache = parsed
            parsed
        }
    }

    private fun parse(): List<RoutinePreset> {
        val raw = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        val array = JSONObject(raw).getJSONArray("routines")

        return (0 until array.length()).map { index ->
            val routine = array.getJSONObject(index)
            val steps = routine.getJSONArray("steps")
            RoutinePreset(
                name = routine.getString("name"),
                steps = (0 until steps.length()).map { stepIndex ->
                    val step = steps.getJSONObject(stepIndex)
                    step.getString("title") to step.getInt("seconds")
                }
            )
        }
    }

    private companion object {
        const val ASSET_PATH = "presets/routines.json"
    }
}
