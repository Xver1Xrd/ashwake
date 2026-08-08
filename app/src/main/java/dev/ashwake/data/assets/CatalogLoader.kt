package dev.ashwake.data.assets

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ashwake.BuildConfig
import dev.ashwake.core.model.Stat
import dev.ashwake.domain.engine.character.ItemBudgetValidator
import dev.ashwake.domain.model.character.Affix
import dev.ashwake.domain.model.character.Bulk
import dev.ashwake.domain.model.character.EquipItem
import dev.ashwake.domain.model.character.EquipSlot
import dev.ashwake.domain.model.character.ItemEffect
import dev.ashwake.domain.model.character.ItemSet
import dev.ashwake.domain.model.character.Palette
import dev.ashwake.domain.model.character.Rarity
import dev.ashwake.domain.model.character.Style
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class Catalog(
    val items: List<EquipItem>,
    val itemsById: Map<String, EquipItem>,
    val palettes: Map<String, Palette>,
    val paletteTints: Map<String, Int>,
    val affixes: Map<String, Affix>,
    val sets: Map<String, ItemSet>
) {
    fun item(id: String): EquipItem? = itemsById[id]

    fun bySlot(slot: EquipSlot): List<EquipItem> = items.filter { it.slot == slot }

    companion object {
        val EMPTY = Catalog(emptyList(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap())
    }
}

/**
 * Каталог снаряжения из assets.
 *
 * Каждая запись `items.json` — это **базовый спрайт**, а не предмет каталога.
 * Загрузчик разворачивает его по палитрам: один файл на диске даёт до двенадцати
 * записей (п. 16.1). Так каталог растёт без роста числа спрайтов.
 *
 * Валидация бюджета редкости идёт на старте: в debug превышение роняет
 * приложение, в release эффект обрезается и пишется в лог (п. 16.6).
 */
@Singleton
class CatalogLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val validator: ItemBudgetValidator
) {
    @Volatile
    private var cache: Catalog? = null

    suspend fun load(): Catalog {
        cache?.let { return it }
        return withContext(Dispatchers.IO) {
            val loaded = runCatching { parse() }.getOrElse { error ->
                if (BuildConfig.DEBUG) throw error
                Log.e(TAG, "Каталог не прочитан", error)
                Catalog.EMPTY
            }
            cache = loaded
            loaded
        }
    }

    private fun parse(): Catalog {
        val palettes = parsePalettes()
        val affixes = parseAffixes()
        val sets = parseSets()
        val expanded = parseItems(palettes.keys.toList())

        val validation = validator.validate(expanded)
        if (validation.issues.isNotEmpty()) {
            val message = validation.issues.joinToString("\n") { it.toString() }
            // Кривой каталог ломает экономику молча — в debug падаем громко
            if (BuildConfig.DEBUG) error("Каталог не прошёл проверку:\n$message")
            Log.w(TAG, "Каталог поправлен на лету:\n$message")
        }

        val items = validation.items
        return Catalog(
            items = items,
            itemsById = items.associateBy { it.id },
            palettes = palettes.mapValues { (id, tint) -> Palette(id = id, title = id) }
                .also { /* mapping для LUT появится вместе с настоящими спрайтами */ },
            paletteTints = palettes,
            affixes = affixes,
            sets = sets
        )
    }

    /** @return id палитры → цвет для плейсхолдера и превью. */
    private fun parsePalettes(): Map<String, Int> {
        val json = JSONObject(readAsset("catalog/palettes.json")).getJSONArray("palettes")
        return (0 until json.length()).associate { index ->
            val palette = json.getJSONObject(index)
            palette.getString("id") to parseColor(palette.getString("tint"))
        }
    }

    private fun parseAffixes(): Map<String, Affix> {
        val json = JSONObject(readAsset("catalog/affixes.json"))
        val prefixes = affixesOf(json.getJSONArray("prefixes"), isPrefix = true)
        val suffixes = affixesOf(json.getJSONArray("suffixes"), isPrefix = false)
        return (prefixes + suffixes).associateBy { it.id }
    }

    private fun affixesOf(array: JSONArray, isPrefix: Boolean): List<Affix> =
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            Affix(
                id = item.getString("id"),
                isPrefix = isPrefix,
                label = item.getString("label"),
                budgetMultiplier = item.optDouble("budgetMultiplier", 1.0).toFloat(),
                priceMultiplier = item.optDouble("priceMultiplier", 1.0).toFloat(),
                effects = effectsOf(item.optJSONArray("effects")),
                statBonus = statsOf(item.optJSONObject("statBonus")),
                styles = item.optJSONArray("styles")?.let { styles ->
                    (0 until styles.length()).map { Style.valueOf(styles.getString(it)) }
                }.orEmpty()
            )
        }

    private fun parseSets(): Map<String, ItemSet> {
        val array = JSONObject(readAsset("catalog/sets.json")).getJSONArray("sets")
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            ItemSet(
                tag = item.getString("tag"),
                title = item.getString("title"),
                style = Style.valueOf(item.getString("style")),
                bonus2 = effectsOf(item.optJSONArray("bonus2")),
                bonus4 = effectsOf(item.optJSONArray("bonus4")),
                bonus6 = effectsOf(item.optJSONArray("bonus6"))
            )
        }.associateBy { it.tag }
    }

    /** Разворот базовых спрайтов по палитрам в записи каталога. */
    private fun parseItems(knownPalettes: List<String>): List<EquipItem> {
        val array = JSONObject(readAsset("catalog/items.json")).getJSONArray("items")

        return (0 until array.length()).flatMap { index ->
            val item = array.getJSONObject(index)
            val baseId = item.getString("id")
            val slot = EquipSlot.valueOf(item.getString("slot"))
            val rarity = Rarity.valueOf(item.optString("rarity", "COMMON"))
            val basePrice = if (item.isNull("price")) null else item.optInt("price")
            val effects = effectsOf(item.optJSONArray("effects"))
            val hides = item.optJSONArray("hides")?.let { hidden ->
                (0 until hidden.length()).map { EquipSlot.valueOf(hidden.getString(it)) }
            }.orEmpty()

            val palettes = item.optJSONArray("palettes")?.let { array ->
                (0 until array.length()).map { array.getString(it) }
            }?.filter { it in knownPalettes } ?: listOf(DEFAULT_PALETTE)

            palettes.map { paletteId ->
                EquipItem(
                    id = "${baseId}_$paletteId",
                    baseSpriteId = baseId,
                    slot = slot,
                    frames = item.optInt("frames", 1),
                    hides = hides,
                    dimsFace = item.optBoolean("dimsFace", false),
                    paletteId = paletteId,
                    bulk = Bulk.valueOf(item.optString("bulk", "NORMAL")),
                    style = Style.valueOf(item.optString("style", "CASUAL")),
                    setTag = item.optString("setTag").takeIf { it.isNotBlank() },
                    rarity = rarity,
                    price = basePrice,
                    unlockCondition = item.optString("unlockCondition").takeIf { it.isNotBlank() },
                    requires = statsOf(item.optJSONObject("requires")),
                    effects = effects,
                    name = item.getString("name"),
                    lore = item.optString("lore")
                )
            }
        }
    }

    private fun effectsOf(array: JSONArray?): List<ItemEffect> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { index ->
            val effect = array.getJSONObject(index)
            ItemEffect(effect.getString("key"), effect.getDouble("value").toFloat())
        }
    }

    private fun statsOf(json: JSONObject?): Map<Stat, Int> {
        if (json == null) return emptyMap()
        return json.keys().asSequence().associate { key ->
            Stat.valueOf(key) to json.getInt(key)
        }
    }

    private fun parseColor(hex: String): Int =
        (0xFF000000.toInt()) or hex.removePrefix("#").toInt(16)

    private fun readAsset(path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }

    private companion object {
        const val TAG = "CatalogLoader"
        const val DEFAULT_PALETTE = "ash"
    }
}
