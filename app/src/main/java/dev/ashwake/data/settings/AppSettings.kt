package dev.ashwake.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ashwake.core.time.DEFAULT_DAY_START_HOUR
import dev.ashwake.domain.model.tasks.TimeboxSettings
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

        const val DEFAULT_WORK_START = 9 * 60
        const val DEFAULT_WORK_END = 19 * 60
        const val DEFAULT_BUFFER = 10
        const val DEFAULT_LUNCH_START = 13 * 60
        const val DEFAULT_LUNCH_DURATION = 60
    }
}
