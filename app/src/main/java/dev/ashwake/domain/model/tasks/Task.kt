package dev.ashwake.domain.model.tasks

import dev.ashwake.core.model.Priority
import dev.ashwake.core.model.WeekdayMask
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

enum class TaskStatus { ACTIVE, DONE, DROPPED }

/** Квадранты матрицы Эйзенхауэра. */
enum class EisenhowerQuadrant {
    URGENT_IMPORTANT,       // Q1 — сделать
    NOT_URGENT_IMPORTANT,   // Q2 — запланировать
    URGENT_NOT_IMPORTANT,   // Q3 — делегировать
    NOT_URGENT_NOT_IMPORTANT // Q4 — удалить
}

data class Project(
    val id: Long = 0,
    val name: String,
    val color: Int,
    val icon: String? = null,
    val position: Int = 0,
    val archived: Boolean = false,
    val createdAt: Instant = Instant.EPOCH
)

data class Tag(
    val id: Long = 0,
    val name: String,
    val color: Int
)

enum class RecurrenceType { DAILY, EVERY_N_DAYS, WEEKDAYS, DAY_OF_MONTH }

data class RecurrenceRule(
    val id: Long = 0,
    val type: RecurrenceType,
    val intervalDays: Int? = null,
    val weekdays: WeekdayMask = WeekdayMask.NONE,
    val dayOfMonth: Int? = null,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    /** Считать следующий срок от факта выполнения, а не от плановой даты. */
    val fromCompletion: Boolean = false
)

data class Task(
    val id: Long = 0,
    val title: String,
    /**
     * Значок задачи: одна эмодзи. Список из десятка одинаковых строк
     * читается плохо, а картинка ловится глазом раньше текста — поэтому
     * значок и стоит в начале строки вместо ещё одной подписи.
     */
    val emoji: String? = null,
    val note: String? = null,
    val projectId: Long? = null,
    val parentTaskId: Long? = null,
    val priority: Priority = Priority.P4,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    /** Оценка в минутах. Без неё задача не попадает в автораскладку дня (п. 2). */
    val estimateMinutes: Int? = null,
    val status: TaskStatus = TaskStatus.ACTIVE,
    val completedAt: Instant? = null,
    val position: Int = 0,
    /** null → квадрант вычисляется по приоритету и дедлайну, иначе — ручной выбор. */
    val quadrantOverride: EisenhowerQuadrant? = null,
    val recurrence: RecurrenceRule? = null,
    val seriesId: String? = null,
    val isTemplate: Boolean = false,
    /** Настойчивое напоминание: повторять каждые N минут до закрытия задачи. */
    val persistentReminderMinutes: Int? = null,
    val postponeCount: Int = 0,
    val lastPostponedAt: Instant? = null,
    val sourceLink: String? = null,
    val delegatedTo: String? = null,
    val tags: List<Tag> = emptyList(),
    val subtasks: List<Task> = emptyList(),
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH
) {
    val isDone: Boolean get() = status == TaskStatus.DONE

    /** Жёлтая метка с 3 переносов, красная с 5 (п. 1). */
    val staleLevel: StaleLevel
        get() = when {
            postponeCount >= 5 -> StaleLevel.CRITICAL
            postponeCount >= 3 -> StaleLevel.WARNING
            else -> StaleLevel.NONE
        }

    fun isOverdue(today: LocalDate): Boolean =
        status == TaskStatus.ACTIVE && dueDate != null && dueDate < today
}

enum class StaleLevel { NONE, WARNING, CRITICAL }

/** Что предлагается сделать с залежавшейся задачей на 5-м переносе. */
enum class StaleResolution { DELETE, DELEGATE, SPLIT, SCHEDULE_SLOT, KEEP }

/** Откуда пришёл перенос — нужно для аналитики «почему задачи залёживаются». */
enum class PostponeSource { SWIPE, RITUAL, DIALOG, AUTO }
