package dev.ashwake.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ashwake.core.time.DEFAULT_DAY_START_HOUR
import dev.ashwake.domain.model.tasks.TimeboxSettings
import dev.ashwake.ui.theme.AccentColor
import dev.ashwake.ui.theme.BackgroundStyle
import dev.ashwake.ui.theme.CornerStyle
import dev.ashwake.ui.theme.ThemeMode
import dev.ashwake.ui.theme.ThemeSettings
import dev.ashwake.ui.theme.UiDensity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ashwake")

/**
 * Настройки приложения.
 *
 * Живут в DataStore, а не в базе: это скаляры, которые читаются на каждом
 * экране и не участвуют ни в одной выборке. Держать их таблицей значило бы
 * гонять запрос ради двух чисел.
 */
@Singleton
class AppSettings @Inject constructor(
    @ApplicationContext private val context: Context
) {

    val timebox: Flow<TimeboxSettings> = context.dataStore.data.map { prefs ->
        TimeboxSettings(
            workStartMinute = prefs[WORK_START] ?: DEFAULT_WORK_START,
            workEndMinute = prefs[WORK_END] ?: DEFAULT_WORK_END,
            bufferMinutes = prefs[BUFFER] ?: DEFAULT_BUFFER,
            lunchStartMinute = if (prefs[LUNCH_ENABLED] != false) {
                prefs[LUNCH_START] ?: DEFAULT_LUNCH_START
            } else null,
            lunchDurationMinutes = prefs[LUNCH_DURATION] ?: DEFAULT_LUNCH_DURATION
        )
    }

    val useSystemCalendar: Flow<Boolean> =
        context.dataStore.data.map { it[USE_CALENDAR] ?: false }

    /** Папка для авто-бэкапа, выбранная через SAF. null — бэкап выключен. */
    val backupFolderUri: Flow<String?> =
        context.dataStore.data.map { it[BACKUP_FOLDER] }

    /** Шифровать ли архив. Незашифрованный разрешён, но с предупреждением (п. 13). */
    val backupEncrypted: Flow<Boolean> =
        context.dataStore.data.map { it[BACKUP_ENCRYPTED] ?: true }

    /** Начало суток: отметка в 01:00 принадлежит вчерашнему дню. */
    val dayStartHour: Flow<Int> =
        context.dataStore.data.map { it[DAY_START_HOUR] ?: DEFAULT_DAY_START_HOUR }

    suspend fun setWorkHours(startMinute: Int, endMinute: Int) {
        context.dataStore.edit { prefs ->
            prefs[WORK_START] = startMinute.coerceIn(0, 24 * 60)
            // Конец не раньше начала: иначе окно планирования схлопнется в ноль
            prefs[WORK_END] = endMinute.coerceIn(startMinute + 60, 24 * 60)
        }
    }

    suspend fun setBuffer(minutes: Int) {
        context.dataStore.edit { it[BUFFER] = minutes.coerceIn(0, 60) }
    }

    suspend fun setLunch(enabled: Boolean, startMinute: Int? = null, durationMinutes: Int? = null) {
        context.dataStore.edit { prefs ->
            prefs[LUNCH_ENABLED] = enabled
            startMinute?.let { prefs[LUNCH_START] = it }
            durationMinutes?.let { prefs[LUNCH_DURATION] = it.coerceIn(15, 180) }
        }
    }

    suspend fun setUseSystemCalendar(enabled: Boolean) {
        context.dataStore.edit { it[USE_CALENDAR] = enabled }
    }

    suspend fun setBackupFolder(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri == null) prefs.remove(BACKUP_FOLDER) else prefs[BACKUP_FOLDER] = uri
        }
    }

    suspend fun setBackupEncrypted(enabled: Boolean) {
        context.dataStore.edit { it[BACKUP_ENCRYPTED] = enabled }
    }

    suspend fun setDayStartHour(hour: Int) {
        context.dataStore.edit { it[DAY_START_HOUR] = hour.coerceIn(0, 12) }
    }

    /**
     * Оформление целиком.
     *
     * Enum-поля хранятся именами, а не индексами: при добавлении нового
     * акцента или стиля индексы разъехались бы и у людей поменялось бы
     * оформление само собой. Неизвестное имя разбирается в значение по
     * умолчанию — настройки старых сборок не должны ронять запуск.
     */
    val theme: Flow<ThemeSettings> = context.dataStore.data.map { prefs ->
        val fallback = ThemeSettings()
        ThemeSettings(
            mode = prefs[THEME_MODE].toEnum(ThemeMode.entries, fallback.mode),
            accent = AccentColor.of(prefs[ACCENT]),
            customAccent = prefs[CUSTOM_ACCENT],
            gradient = prefs[GRADIENT] ?: fallback.gradient,
            background = prefs[BACKGROUND].toEnum(BackgroundStyle.entries, fallback.background),
            corner = prefs[CORNER_STYLE].toEnum(CornerStyle.entries, fallback.corner),
            cornerScale = prefs[CORNER_SCALE] ?: fallback.cornerScale,
            density = prefs[DENSITY].toEnum(UiDensity.entries, fallback.density),
            blur = prefs[BLUR] ?: fallback.blur,
            warm = prefs[COLOR_WARM],
            cold = prefs[COLOR_COLD],
            danger = prefs[COLOR_DANGER],
            success = prefs[COLOR_SUCCESS]
        )
    }

    suspend fun setTheme(theme: ThemeSettings) {
        context.dataStore.edit { prefs ->
            prefs[THEME_MODE] = theme.mode.name
            prefs[ACCENT] = theme.accent.name
            prefs[GRADIENT] = theme.gradient
            prefs[BACKGROUND] = theme.background.name
            prefs[CORNER_STYLE] = theme.corner.name
            prefs[CORNER_SCALE] = theme.cornerScale
            prefs[DENSITY] = theme.density.name
            prefs[BLUR] = theme.blur
            prefs.putOrRemove(CUSTOM_ACCENT, theme.customAccent)
            prefs.putOrRemove(COLOR_WARM, theme.warm)
            prefs.putOrRemove(COLOR_COLD, theme.cold)
            prefs.putOrRemove(COLOR_DANGER, theme.danger)
            prefs.putOrRemove(COLOR_SUCCESS, theme.success)
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setAccent(accent: AccentColor) {
        context.dataStore.edit { prefs ->
            prefs[ACCENT] = accent.name
            // Выбор пресета снимает свой цвет: иначе пресет не применился бы,
            // а человек продолжал бы тыкать в кружки без всякого эффекта
            prefs.remove(CUSTOM_ACCENT)
        }
    }

    private companion object {
        val WORK_START = intPreferencesKey("work_start_minute")
        val WORK_END = intPreferencesKey("work_end_minute")
        val BUFFER = intPreferencesKey("buffer_minutes")
        val LUNCH_ENABLED = booleanPreferencesKey("lunch_enabled")
        val LUNCH_START = intPreferencesKey("lunch_start_minute")
        val LUNCH_DURATION = intPreferencesKey("lunch_duration_minutes")
        val USE_CALENDAR = booleanPreferencesKey("use_system_calendar")
        val DAY_START_HOUR = intPreferencesKey("day_start_hour")
        val BACKUP_FOLDER = stringPreferencesKey("backup_folder_uri")
        val BACKUP_ENCRYPTED = booleanPreferencesKey("backup_encrypted")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT = stringPreferencesKey("accent_color")
        val CUSTOM_ACCENT = intPreferencesKey("accent_custom")
        val GRADIENT = booleanPreferencesKey("theme_gradient")
        val BACKGROUND = stringPreferencesKey("theme_background")
        val CORNER_STYLE = stringPreferencesKey("theme_corner_style")
        val CORNER_SCALE = floatPreferencesKey("theme_corner_scale")
        val DENSITY = stringPreferencesKey("theme_density")
        val BLUR = booleanPreferencesKey("theme_blur")
        val COLOR_WARM = intPreferencesKey("theme_color_warm")
        val COLOR_COLD = intPreferencesKey("theme_color_cold")
        val COLOR_DANGER = intPreferencesKey("theme_color_danger")
        val COLOR_SUCCESS = intPreferencesKey("theme_color_success")

        const val DEFAULT_WORK_START = 9 * 60
        const val DEFAULT_WORK_END = 19 * 60
        const val DEFAULT_BUFFER = 10
        const val DEFAULT_LUNCH_START = 13 * 60
        const val DEFAULT_LUNCH_DURATION = 60
    }
}

/** Имя из хранилища в enum. Неизвестное значение — не повод падать. */
private inline fun <reified T : Enum<T>> String?.toEnum(entries: List<T>, fallback: T): T =
    entries.firstOrNull { it.name == this } ?: fallback

/** Null убирает ключ: «не задано» и «задано нулём» это разные вещи. */
private fun <T> MutablePreferences.putOrRemove(key: Preferences.Key<T>, value: T?) {
    if (value == null) remove(key) else set(key, value)
}
