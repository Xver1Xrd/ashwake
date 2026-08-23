package dev.ashwake.data.db.mapper.habits

import dev.ashwake.core.model.Sphere
import dev.ashwake.core.model.WeekdayMask
import dev.ashwake.data.db.entity.habits.HabitAnchorEntity
import dev.ashwake.data.db.entity.habits.HabitEntity
import dev.ashwake.data.db.entity.habits.HabitEntryEntity
import dev.ashwake.data.db.entity.habits.HabitFreezeEntity
import dev.ashwake.data.db.entity.habits.HabitPauseEntity
import dev.ashwake.data.db.entity.habits.HabitSkipReasonEntity
import dev.ashwake.data.db.mapper.tasks.toEpochDayInt
import dev.ashwake.data.db.mapper.tasks.toInstant
import dev.ashwake.data.db.mapper.tasks.toLocalDate
import dev.ashwake.data.db.mapper.tasks.toLocalTime
import dev.ashwake.data.db.mapper.tasks.toMillis
import dev.ashwake.data.db.mapper.tasks.toMinuteOfDay
import dev.ashwake.domain.model.habits.AnchorType
import dev.ashwake.domain.model.habits.EntrySource
import dev.ashwake.domain.model.habits.EntryStatus
import dev.ashwake.domain.model.habits.Habit
import dev.ashwake.domain.model.habits.HabitAnchor
import dev.ashwake.domain.model.habits.HabitEntry
import dev.ashwake.domain.model.habits.HabitFreeze
import dev.ashwake.domain.model.habits.HabitPause
import dev.ashwake.domain.model.habits.HabitSchedule
import dev.ashwake.domain.model.habits.HabitScheduleType
import dev.ashwake.domain.model.habits.HabitType
import dev.ashwake.domain.model.habits.SkipReason
import java.time.LocalDate

fun HabitEntity.toDomain(anchors: List<HabitAnchor> = emptyList()): Habit = Habit(
    id = id,
    name = name,
    icon = icon,
    iconPath = iconPath,
    color = color,
    type = HabitType.valueOf(type),
    sphere = Sphere.valueOf(sphere),
    targetValue = targetValue,
    unitName = unitName,
    minimumValue = minimumValue,
    schedule = HabitSchedule(
        type = HabitScheduleType.valueOf(scheduleType),
        timesPerWeek = timesPerWeek,
        weekdays = WeekdayMask(weekdaysMask),
        intervalAnchor = intervalAnchor?.toLocalDate(),
        biweeklyAnchor = biweeklyAnchor?.toLocalDate()
    ),
    reminderTime = reminderTime?.toLocalTime(),
    freezeQuotaPerMonth = freezeQuotaPerMonth,
    position = position,
    archived = archived,
    createdAt = createdAt.toInstant(),
    anchors = anchors
)

fun Habit.toEntity(): HabitEntity = HabitEntity(
    id = id,
    name = name,
    icon = icon,
    iconPath = iconPath,
    color = color,
    type = type.name,
    sphere = sphere.name,
    targetValue = targetValue,
    unitName = unitName,
    minimumValue = minimumValue,
    scheduleType = schedule.type.name,
    timesPerWeek = schedule.timesPerWeek,
    weekdaysMask = schedule.weekdays.bits,
    intervalAnchor = schedule.intervalAnchor?.toEpochDayInt(),
    biweeklyAnchor = schedule.biweeklyAnchor?.toEpochDayInt(),
    reminderTime = reminderTime?.toMinuteOfDay(),
    freezeQuotaPerMonth = freezeQuotaPerMonth,
    position = position,
    archived = archived,
    createdAt = createdAt.toMillis()
)

fun HabitEntryEntity.toDomain(): HabitEntry = HabitEntry(
    id = id,
    habitId = habitId,
    date = date.toLocalDate(),
    status = EntryStatus.valueOf(status),
    value = value,
    note = note,
    completedAt = completedAt?.toInstant(),
    skipReasonId = skipReasonId,
    source = EntrySource.valueOf(source)
)

fun HabitEntry.toEntity(): HabitEntryEntity = HabitEntryEntity(
    id = id,
    habitId = habitId,
    date = date.toEpochDayInt(),
    status = status.name,
    value = value,
    note = note,
    completedAt = completedAt?.toMillis(),
    skipReasonId = skipReasonId,
    source = source.name
)

fun HabitFreezeEntity.toDomain(): HabitFreeze = HabitFreeze(
    id = id,
    habitId = habitId,
    date = date.toLocalDate(),
    spentAt = spentAt.toInstant(),
    auto = auto
)

fun HabitPauseEntity.toDomain(): HabitPause = HabitPause(
    id = id,
    habitId = habitId,
    startDate = startDate.toLocalDate(),
    endDate = endDate?.toLocalDate(),
    reason = reason
)

fun HabitPause.toEntity(): HabitPauseEntity = HabitPauseEntity(
    id = id,
    habitId = habitId,
    startDate = startDate.toEpochDayInt(),
    endDate = endDate?.toEpochDayInt(),
    reason = reason
)

fun HabitAnchorEntity.toDomain(): HabitAnchor = HabitAnchor(
    id = id,
    habitId = habitId,
    type = AnchorType.valueOf(type),
    refHabitId = refHabitId,
    refRoutineId = refRoutineId,
    refTagId = refTagId,
    delayMinutes = delayMinutes,
    lastFiredDate = lastFiredDate?.let { LocalDate.ofEpochDay(it.toLong()) }
)

fun HabitAnchor.toEntity(): HabitAnchorEntity = HabitAnchorEntity(
    id = id,
    habitId = habitId,
    type = type.name,
    refHabitId = refHabitId,
    refRoutineId = refRoutineId,
    refTagId = refTagId,
    delayMinutes = delayMinutes,
    lastFiredDate = lastFiredDate?.toEpochDayInt()
)

fun HabitSkipReasonEntity.toDomain(): SkipReason = SkipReason(
    id = id, code = code, label = label, builtin = builtin
)
