package dev.ashwake.data.assets

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ashwake.domain.engine.achievement.AchievementCounter
import dev.ashwake.domain.engine.achievement.AchievementDefinition
import dev.ashwake.domain.model.character.MaterialType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Каталог достижений из `assets/catalog/achievements.json` (п. 16.9).
 *
 * Определения живут в assets, а не в коде: добавить достижение — это
 * поправить JSON, а не выпускать обновление приложения.
 */
@Singleton
class AchievementLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile
    private var cache: List<AchievementDefinition>? = null

    suspend fun load(): List<AchievementDefinition> {
        cache?.let { return it }
        return withContext(Dispatchers.IO) {
            val parsed = runCatching { parse() }.getOrDefault(emptyList())
            cache = parsed
            parsed
        }
    }

    private fun parse(): List<AchievementDefinition> {
        val raw = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        val array = JSONObject(raw).getJSONArray("achievements")

        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            AchievementDefinition(
                id = item.getString("id"),
                title = item.getString("title"),
                description = item.getString("description"),
                counter = AchievementCounter.valueOf(item.getString("counter")),
                target = item.getLong("target"),
                rewardCoins = item.optInt("coins", 0),
                rewardXp = item.optInt("xp", 0),
                rewardMaterial = item.optString("material").takeIf { it.isNotBlank() }
                    ?.let { MaterialType.valueOf(it) },
                rewardMaterialAmount = item.optInt("materialAmount", 1)
            )
        }
    }

    private companion object {
        const val ASSET_PATH = "catalog/achievements.json"
    }
}
