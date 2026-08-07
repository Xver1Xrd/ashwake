package dev.ashwake.data.db.mapper.abstinence

import dev.ashwake.data.db.entity.abstinence.AbstinenceAttemptEntity
import dev.ashwake.data.db.entity.abstinence.AbstinenceEntity
import dev.ashwake.data.db.entity.abstinence.AbstinenceMilestoneEntity
import dev.ashwake.data.db.entity.abstinence.AbstinenceSubstituteEntity
import dev.ashwake.data.db.entity.abstinence.CravingEventEntity
import dev.ashwake.data.db.entity.abstinence.CravingTriggerEntity
import dev.ashwake.data.db.entity.abstinence.RelapseReasonEntity
import dev.ashwake.data.db.mapper.tasks.toInstant
import dev.ashwake.data.db.mapper.tasks.toMillis
import dev.ashwake.domain.model.abstinence.Abstinence
import dev.ashwake.domain.model.abstinence.AbstinenceMode
import dev.ashwake.domain.model.abstinence.Attempt
import dev.ashwake.domain.model.abstinence.Baseline
import dev.ashwake.domain.model.abstinence.CravingEvent
import dev.ashwake.domain.model.abstinence.CravingTrigger
import dev.ashwake.domain.model.abstinence.Milestone
import dev.ashwake.domain.model.abstinence.RelapseReason
import dev.ashwake.domain.model.abstinence.Substitute

fun AbstinenceEntity.toDomain(
    attempts: List<Attempt> = emptyList(),
    milestones: List<Milestone> = emptyList(),
    substitutes: List<Substitute> = emptyList()
): Abstinence = Abstinence(
    id = id,
    name = name,
    icon = icon,
    paletteId = paletteId,
    mode = AbstinenceMode.valueOf(mode),
    gentlePenaltyDays = gentlePenaltyDays,
    milestonesEnabled = milestonesEnabled,
    motivationText = motivationText,
    // Baseline собирается, только если заполнен полностью: половинчатые данные
    // дали бы неверную экономию, а её показывают как факт
    baseline = if (baselineUnitName != null && baselineUnitsPerDay != null &&
        baselineCostPerUnit != null
    ) {
        Baseline(
            unitName = baselineUnitName,
            unitsPerDay = baselineUnitsPerDay,
            costPerUnit = baselineCostPerUnit,
            currency = baselineCurrency ?: "RUB"
        )
    } else null,
    stickyNotification = stickyNotification,
    substanceWarningAck = substanceWarningAck,
    position = position,
    archived = archived,
    createdAt = createdAt.toInstant(),
    attempts = attempts,
    milestones = milestones,
    substitutes = substitutes
)

fun Abstinence.toEntity(): AbstinenceEntity = AbstinenceEntity(
    id = id,
    name = name,
    icon = icon,
    paletteId = paletteId,
    mode = mode.name,
    gentlePenaltyDays = gentlePenaltyDays,
    milestonesEnabled = milestonesEnabled,
    motivationText = motivationText,
    baselineUnitName = baseline?.unitName,
    baselineUnitsPerDay = baseline?.unitsPerDay,
    baselineCostPerUnit = baseline?.costPerUnit,
    baselineCurrency = baseline?.currency,
    stickyNotification = stickyNotification,
    substanceWarningAck = substanceWarningAck,
    position = position,
    archived = archived,
    createdAt = createdAt.toMillis()
)

fun AbstinenceAttemptEntity.toDomain(): Attempt = Attempt(
    id = id,
    abstinenceId = abstinenceId,
    ordinal = ordinal,
    startedAt = startedAt.toInstant(),
    endedAt = endedAt?.toInstant(),
    relapseReasonId = relapseReasonId,
    note = note,
    penaltyDays = penaltyDays,
    undoDeadline = undoDeadline?.toInstant()
)

fun Attempt.toEntity(): AbstinenceAttemptEntity = AbstinenceAttemptEntity(
    id = id,
    abstinenceId = abstinenceId,
    ordinal = ordinal,
    startedAt = startedAt.toMillis(),
    endedAt = endedAt?.toMillis(),
    relapseReasonId = relapseReasonId,
    note = note,
    penaltyDays = penaltyDays,
    undoDeadline = undoDeadline?.toMillis()
)

fun AbstinenceMilestoneEntity.toDomain(): Milestone = Milestone(
    id = id,
    abstinenceId = abstinenceId,
    days = days,
    title = title,
    userText = userText,
    custom = custom,
    reachedAt = reachedAt?.toInstant(),
    notified = notified
)

fun Milestone.toEntity(): AbstinenceMilestoneEntity = AbstinenceMilestoneEntity(
    id = id,
    abstinenceId = abstinenceId,
    days = days,
    title = title,
    userText = userText,
    custom = custom,
    reachedAt = reachedAt?.toMillis(),
    notified = notified
)

fun CravingEventEntity.toDomain(): CravingEvent = CravingEvent(
    id = id,
    abstinenceId = abstinenceId,
    at = at.toInstant(),
    hourOfDay = hourOfDay,
    dayOfWeek = dayOfWeek,
    intensity = intensity,
    triggerId = triggerId,
    note = note,
    resisted = resisted,
    durationSeconds = durationSeconds,
    usedBreathing = usedBreathing,
    usedSubstituteId = usedSubstituteId
)

fun CravingEvent.toEntity(): CravingEventEntity = CravingEventEntity(
    id = id,
    abstinenceId = abstinenceId,
    at = at.toMillis(),
    hourOfDay = hourOfDay,
    dayOfWeek = dayOfWeek,
    intensity = intensity,
    triggerId = triggerId,
    note = note,
    resisted = resisted,
    durationSeconds = durationSeconds,
    usedBreathing = usedBreathing,
    usedSubstituteId = usedSubstituteId
)

fun CravingTriggerEntity.toDomain(): CravingTrigger =
    CravingTrigger(id = id, code = code, label = label, builtin = builtin)

fun RelapseReasonEntity.toDomain(): RelapseReason =
    RelapseReason(id = id, code = code, label = label, builtin = builtin)

fun AbstinenceSubstituteEntity.toDomain(): Substitute =
    Substitute(id = id, abstinenceId = abstinenceId, text = text, position = position)

fun Substitute.toEntity(): AbstinenceSubstituteEntity =
    AbstinenceSubstituteEntity(
        id = id, abstinenceId = abstinenceId, text = text, position = position
    )
