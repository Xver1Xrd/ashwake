package dev.ashwake.domain.model.habits

import dev.ashwake.core.model.Sphere
import dev.ashwake.core.model.WeekdayMask
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Тип привычки.
 *
 * NEGATIVE устроена наоборот: успех — это отсутствие отметки. Поэтому у неё
 * день без записи считается выполненным, а запись означает срыв.
 */
enum class HabitType { CHECK, COUNTER, NEGATIVE }

enum class HabitScheduleType {
    DAILY,
    TIMES_PER_WEEK,
    EVERY_OTHER_DAY,
    WEEKDAYS,
    BIWEEKLY
}

data class HabitSchedule(
    val type: HabitScheduleType = HabitScheduleType.DAILY,
    val timesPerWeek: Int = 3,
    val weekdays: WeekdayMask = WeekdayMask.EVERY_DAY,
    val intervalAnchor: LocalDate? = null,
    val biweeklyAnchor: LocalDate? = null
)

/**
 * Статус отметки за сутки.
 *
 * MINIMUM — «минимальная планка» из п. 5: сделал две минуты вместо тридцати.
 * Стрик сохраняется, вклад в score половинный.
 * FROZEN и PAUSED из расчёта исключаются целиком, а не считаются нулём.
 */
enum class EntryStatus { DONE, MINIMUM, SKIPPED, FROZEN, PAUSED }

enum class EntrySource { MANUAL, NOTIFICATION, WIDGET, RITUAL }

data class HabitEntry(
    val id: Long = 0,
    val habitId: Long,
    val date: LocalDate,
    val status: EntryStatus,
    val value: Float = 0f,
    val note: String? = null,
    val completedAt: Instant? = null,
    val skipReasonId: Long? = null,
    val source: EntrySource = EntrySource.MANUAL
) {
    /**
     * Вклад дня в score. Хранится в базе рядом со статусом, но пересчитывается
     * из статуса и цели, чтобы правка цели не переписывала историю задним числом.
     */
    val contribution: Float
        get() = when (status) {
            EntryStatus.DONE -> 1f
            EntryStatus.MINIMUM -> MINIMUM_CONTRIBUTION
            EntryStatus.SKIPPED -> 0f
            EntryStatus.FROZEN, EntryStatus.PAUSED -> 0f // из расчёта исключаются вовсе
        }

    val countsForStreak: Boolean
        get() = status == EntryStatus.DONE || status == EntryStatus.MINIMUM

    val isExcluded: Boolean
        get() = status == EntryStatus.FROZEN || status == EntryStatus.PAUSED

    companion object {
        /** Половина: минимум держит серию, но не равен полноценному дню. */
        const val MINIMUM_CONTRIBUTION = 0.5f
    }
}

/** Тип якоря: привычка привязана к событию, а не к часам (п. 5). */
enum class AnchorType { HABIT_DONE, ROUTINE_DONE, FIRST_UNLOCK, TASK_TAG_DONE, TIME }

data class HabitAnchor(
    val id: Long = 0,
    val habitId: Long,
    val type: AnchorType,
    val refHabitId: Long? = null,
    val refRoutineId: Long? = null,
    val refTagId: Long? = null,
    val delayMinutes: Int = 0
)

data class HabitFreeze(
    val id: Long = 0,
    val habitId: Long,
    val date: LocalDate,
    val spentAt: Instant,
    val auto: Boolean = false
)

/** `habitId == null` — пауза сразу для всех привычек (режим отпуска). */
data class HabitPause(
    val id: Long = 0,
    val habitId: Long? = null,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val reason: String? = null
) {
    fun covers(date: LocalDate): Boolean =
        date >= startDate && (endDate == null || date <= endDate)
}

data class SkipReason(
    val id: Long = 0,
    val code: String,
    val label: String,
    val builtin: Boolean = true
)

data class Habit(
    val id: Long = 0,
    val name: String,
    val icon: String? = null,
    val color: Int = 0,
    val type: HabitType = HabitType.CHECK,
    val sphere: Sphere = Sphere.HEALTH,
    val targetValue: Float = 1f,
    val unitName: String? = null,
    /** Цель «на плохой день». null — минимальная планка не настроена. */
    val minimumValue: Float? = null,
    val schedule: HabitSchedule = HabitSchedule(),
    val reminderTime: LocalTime? = null,
    val freezeQuotaPerMonth: Int = 3,
    val position: Int = 0,
    val archived: Boolean = false,
    val createdAt: Instant = Instant.EPOCH,
    val anchors: List<HabitAnchor> = emptyList()
) {
    val hasMinimum: Boolean get() = minimumValue != null && minimumValue > 0f
}

/** Привычка вместе с посчитанным состоянием — то, что показывает карточка. */
data class HabitWithProgress(
    val habit: Habit,
    val score: Float = 0f,
    val currentStreak: Int = 0,
    val recordStreak: Int = 0,
    val todayEntry: HabitEntry? = null,
    val dueToday: Boolean = true,
    val freezesLeftThisMonth: Int = 0,
    val paused: Boolean = false
) {
    val todayValue: Float get() = todayEntry?.value ?: 0f
    val doneToday: Boolean get() = todayEntry?.countsForStreak == true
}
