package dev.ashwake.domain.model.tasks

import java.time.LocalDate
import java.time.LocalTime

/** Что за блок стоит на шкале дня. */
enum class BlockKind { TASK, ROUTINE, CALENDAR, LUNCH, MANUAL }

enum class BlockOrigin { AUTO, MANUAL }

/**
 * Блок на шкале дня.
 *
 * Время в минутах от полуночи: сутки — плоская числовая ось,
 * и вся арифметика планировщика становится обычным сложением.
 */
data class TimeboxBlock(
    val id: Long = 0,
    val date: LocalDate,
    val startMinute: Int,
    val endMinute: Int,
    val kind: BlockKind,
    val title: String,
    val taskId: Long? = null,
    val routineId: Long? = null,
    val calendarEventId: Long? = null,
    /** «Не двигать»: планировщик обходит такой блок стороной. */
    val pinned: Boolean = false,
    val createdBy: BlockOrigin = BlockOrigin.AUTO,
    val actualStartMinute: Int? = null,
    val actualEndMinute: Int? = null
) {
    val durationMinutes: Int get() = (endMinute - startMinute).coerceAtLeast(0)

    /** Занятое время: факт, если он есть, иначе план. */
    val effectiveEndMinute: Int get() = actualEndMinute ?: endMinute

    fun overlaps(other: TimeboxBlock): Boolean =
        startMinute < other.endMinute && other.startMinute < endMinute

    fun startTime(): LocalTime = LocalTime.of(startMinute / 60, startMinute % 60)
    fun endTime(): LocalTime = LocalTime.of(endMinute / 60 % 24, endMinute % 60)
}

/** Настройки раскладки: рабочие часы, буфер, обед (п. 2). */
data class TimeboxSettings(
    val workStartMinute: Int = 9 * 60,
    val workEndMinute: Int = 19 * 60,
    val bufferMinutes: Int = 10,
    val lunchStartMinute: Int? = 13 * 60,
    val lunchDurationMinutes: Int = 60
) {
    val lunchEndMinute: Int? get() = lunchStartMinute?.plus(lunchDurationMinutes)
}

/** Итог одной раскладки дня. */
data class TimeboxDay(
    val date: LocalDate,
    val blocks: List<TimeboxBlock> = emptyList(),
    val deficitMinutes: Int = 0,
    val plannedAt: java.time.Instant? = null
) {
    val hasPlan: Boolean get() = blocks.any { it.kind == BlockKind.TASK }
}
