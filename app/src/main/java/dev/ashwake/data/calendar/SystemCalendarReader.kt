package dev.ashwake.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ashwake.core.time.AppClock
import dev.ashwake.domain.model.tasks.BlockKind
import dev.ashwake.domain.model.tasks.TimeboxBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Чтение событий системного календаря для планировщика (п. 2).
 *
 * Только чтение и только по разрешению: приложение офлайн-first и в чужой
 * календарь не пишет. Нет разрешения — возвращается пустой список,
 * и раскладка просто не знает о встречах, вместо того чтобы падать.
 */
@Singleton
class SystemCalendarReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: AppClock
) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun eventsOn(date: LocalDate): List<TimeboxBlock> {
        if (!hasPermission()) return emptyList()

        return withContext(Dispatchers.IO) {
            runCatching { query(date) }.getOrDefault(emptyList())
        }
    }

    private fun query(date: LocalDate): List<TimeboxBlock> {
        val zone = clock.zone() ?: ZoneId.systemDefault()
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        // Instances даёт уже развёрнутые повторяющиеся события — в отличие от Events,
        // где пришлось бы самим раскрывать правила повтора
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, dayStart)
            ContentUris.appendId(this, dayEnd)
        }.build()

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.AVAILABILITY
        )

        val result = mutableListOf<TimeboxBlock>()
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val allDay = cursor.getInt(4) == 1
                // Событие на весь день не занимает конкретных часов — планировать вокруг
                // него весь день значило бы оставить человека без расписания
                if (allDay) continue

                val availability = cursor.getInt(5)
                if (availability == CalendarContract.Instances.AVAILABILITY_FREE) continue

                val begin = cursor.getLong(2)
                val end = cursor.getLong(3)
                val startMinute = minuteOfDay(begin, zone, date, floor = true)
                val endMinute = minuteOfDay(end, zone, date, floor = false)
                if (endMinute <= startMinute) continue

                result += TimeboxBlock(
                    date = date,
                    startMinute = startMinute,
                    endMinute = endMinute,
                    kind = BlockKind.CALENDAR,
                    title = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: "Событие",
                    calendarEventId = cursor.getLong(0),
                    // Чужое событие двигать нельзя — оно не наше
                    pinned = true
                )
            }
        }
        return result
    }

    /** Событие может начинаться вчера и кончаться завтра — обрезаем по границам дня. */
    private fun minuteOfDay(
        millis: Long,
        zone: ZoneId,
        date: LocalDate,
        floor: Boolean
    ): Int {
        val moment = java.time.Instant.ofEpochMilli(millis).atZone(zone)
        return when {
            moment.toLocalDate() < date -> if (floor) 0 else 0
            moment.toLocalDate() > date -> 24 * 60
            else -> moment.hour * 60 + moment.minute
        }
    }
}
