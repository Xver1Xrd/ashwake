package dev.ashwake.data.db.mapper.tasks

import dev.ashwake.core.model.Priority
import dev.ashwake.core.model.WeekdayMask
import dev.ashwake.data.db.entity.tasks.ProjectEntity
import dev.ashwake.data.db.entity.tasks.RecurrenceRuleEntity
import dev.ashwake.data.db.entity.tasks.TagEntity
import dev.ashwake.data.db.entity.tasks.TaskEntity
import dev.ashwake.data.db.entity.tasks.TaskWithRelations
import dev.ashwake.domain.model.tasks.EisenhowerQuadrant
import dev.ashwake.domain.model.tasks.Project
import dev.ashwake.domain.model.tasks.RecurrenceRule
import dev.ashwake.domain.model.tasks.RecurrenceType
import dev.ashwake.domain.model.tasks.Tag
import dev.ashwake.domain.model.tasks.Task
import dev.ashwake.domain.model.tasks.TaskStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

// --- примитивы хранения --------------------------------------------------

internal fun LocalDate.toEpochDayInt(): Int = toEpochDay().toInt()
internal fun Int.toLocalDate(): LocalDate = LocalDate.ofEpochDay(toLong())
internal fun LocalTime.toMinuteOfDay(): Int = hour * 60 + minute
internal fun Int.toLocalTime(): LocalTime = LocalTime.of(this / 60, this % 60)
internal fun Instant.toMillis(): Long = toEpochMilli()
internal fun Long.toInstant(): Instant = Instant.ofEpochMilli(this)

// --- задачи --------------------------------------------------------------

fun TaskWithRelations.toDomain(): Task = Task(
    id = task.id,
    title = task.title,
    emoji = task.emoji,
    iconPath = task.iconPath,
    note = task.note,
    projectId = task.projectId,
    parentTaskId = task.parentTaskId,
    priority = Priority.valueOf(task.priority),
    dueDate = task.dueDate?.toLocalDate(),
    dueTime = task.dueTime?.toLocalTime(),
    estimateMinutes = task.estimateMinutes,
    status = TaskStatus.valueOf(task.status),
    completedAt = task.completedAt?.toInstant(),
    position = task.position,
    quadrantOverride = task.quadrantOverride?.let { EisenhowerQuadrant.valueOf(it) },
    recurrence = recurrence?.toDomain(),
    seriesId = task.seriesId,
    isTemplate = task.isTemplate,
    persistentReminderMinutes = task.persistentReminderMinutes,
    postponeCount = task.postponeCount,
    lastPostponedAt = task.lastPostponedAt?.toInstant(),
    sourceLink = task.sourceLink,
    delegatedTo = task.delegatedTo,
    tags = tags.map { it.toDomain() },
    subtasks = subtasks.map { it.toDomainShallow() },
    createdAt = task.createdAt.toInstant(),
    updatedAt = task.updatedAt.toInstant()
)

/** Подзадача без своих тегов и вложенности: список второго уровня нам не нужен. */
fun TaskEntity.toDomainShallow(): Task = Task(
    id = id,
    title = title,
    emoji = emoji,
    iconPath = iconPath,
    note = note,
    projectId = projectId,
    parentTaskId = parentTaskId,
    priority = Priority.valueOf(priority),
    dueDate = dueDate?.toLocalDate(),
    dueTime = dueTime?.toLocalTime(),
    estimateMinutes = estimateMinutes,
    status = TaskStatus.valueOf(status),
    completedAt = completedAt?.toInstant(),
    position = position,
    postponeCount = postponeCount,
    createdAt = createdAt.toInstant(),
    updatedAt = updatedAt.toInstant()
)

/** [recurrenceId] задаётся снаружи: правило сохраняется до задачи и знает свой id. */
fun Task.toEntity(recurrenceId: Long? = recurrence?.id?.takeIf { it != 0L }): TaskEntity = TaskEntity(
    id = id,
    title = title,
    emoji = emoji,
    iconPath = iconPath,
    note = note,
    projectId = projectId,
    parentTaskId = parentTaskId,
    priority = priority.name,
    dueDate = dueDate?.toEpochDayInt(),
    dueTime = dueTime?.toMinuteOfDay(),
    estimateMinutes = estimateMinutes,
    status = status.name,
    completedAt = completedAt?.toMillis(),
    position = position,
    quadrantOverride = quadrantOverride?.name,
    recurrenceId = recurrenceId,
    seriesId = seriesId,
    isTemplate = isTemplate,
    persistentReminderMinutes = persistentReminderMinutes,
    postponeCount = postponeCount,
    lastPostponedAt = lastPostponedAt?.toMillis(),
    sourceLink = sourceLink,
    delegatedTo = delegatedTo,
    createdAt = createdAt.toMillis(),
    updatedAt = updatedAt.toMillis()
)

// --- повторы, теги, проекты ----------------------------------------------

fun RecurrenceRuleEntity.toDomain(): RecurrenceRule = RecurrenceRule(
    id = id,
    type = RecurrenceType.valueOf(type),
    intervalDays = intervalDays,
    weekdays = WeekdayMask(weekdaysMask),
    dayOfMonth = dayOfMonth,
    startDate = startDate.toLocalDate(),
    endDate = endDate?.toLocalDate(),
    fromCompletion = fromCompletion
)

fun RecurrenceRule.toEntity(): RecurrenceRuleEntity = RecurrenceRuleEntity(
    id = id,
    type = type.name,
    intervalDays = intervalDays,
    weekdaysMask = weekdays.bits,
    dayOfMonth = dayOfMonth,
    startDate = startDate.toEpochDayInt(),
    endDate = endDate?.toEpochDayInt(),
    fromCompletion = fromCompletion
)

fun TagEntity.toDomain(): Tag = Tag(id = id, name = name, color = color)

fun ProjectEntity.toDomain(): Project = Project(
    id = id,
    name = name,
    color = color,
    icon = icon,
    position = position,
    archived = archived,
    createdAt = createdAt.toInstant()
)

fun Project.toEntity(): ProjectEntity = ProjectEntity(
    id = id,
    name = name,
    color = color,
    icon = icon,
    position = position,
    archived = archived,
    createdAt = createdAt.toMillis()
)
