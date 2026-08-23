package dev.ashwake.domain.model.abstinence

import java.time.Instant

/**
 * Режим учёта срыва.
 *
 * STRICT — классическое обнуление: попытка закрывается, начинается следующая.
 * GENTLE — срыв не закрывает попытку, а отнимает у неё штрафные дни.
 * Разница принципиальная: в GENTLE человек не теряет три месяца из-за одного вечера.
 */
enum class AbstinenceMode { GENTLE, STRICT }

/**
 * Базовые данные для расчёта сэкономленного.
 * Если не заполнено — блок «сэкономлено» на экране просто скрыт,
 * приложение не выдумывает цифры.
 */
data class Baseline(
    val unitName: String,
    val unitsPerDay: Float,
    val costPerUnit: Float,
    val currency: String = "RUB"
)

data class Attempt(
    val id: Long = 0,
    val abstinenceId: Long,
    val ordinal: Int,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    val relapseReasonId: Long? = null,
    val note: String? = null,
    /** Штрафные дни, накопленные срывами в режиме GENTLE. */
    val penaltyDays: Int = 0,
    /** До этого момента срыв можно отменить (24 часа, п. 4). */
    val undoDeadline: Instant? = null
) {
    val isCurrent: Boolean get() = endedAt == null
}

data class Milestone(
    val id: Long = 0,
    val abstinenceId: Long,
    val days: Int,
    val title: String,
    /**
     * Текст пишет сам пользователь. Приложение не выдаёт медицинских
     * утверждений и не обещает эффектов для здоровья (п. 4).
     */
    val userText: String? = null,
    val custom: Boolean = false,
    val reachedAt: Instant? = null,
    val notified: Boolean = false
)

data class RelapseReason(
    val id: Long = 0,
    val code: String,
    val label: String,
    val builtin: Boolean = true
)

data class CravingTrigger(
    val id: Long = 0,
    val code: String,
    val label: String,
    val builtin: Boolean = true
)

/** Событие тяги: то, что фиксируется после кнопки «Тяжело». */
data class CravingEvent(
    val id: Long = 0,
    val abstinenceId: Long,
    val at: Instant,
    val hourOfDay: Int,
    val dayOfWeek: Int,
    val intensity: Int,
    val triggerId: Long? = null,
    val note: String? = null,
    /** null — пользователь не заполнил исход. */
    val resisted: Boolean? = null,
    val durationSeconds: Int? = null,
    val usedBreathing: Boolean = false,
    val usedSubstituteId: Long? = null
)

data class Substitute(
    val id: Long = 0,
    val abstinenceId: Long,
    val text: String,
    val position: Int = 0
)

data class Abstinence(
    val id: Long = 0,
    val name: String,
    /** Значок: одна эмодзи. */
    val icon: String? = null,
    /** Имя файла картинки-значка. Перекрывает эмодзи. */
    val iconPath: String? = null,
    val paletteId: String = "default",
    val mode: AbstinenceMode = AbstinenceMode.GENTLE,
    /** Сколько дней снимает один срыв в режиме GENTLE. */
    val gentlePenaltyDays: Int = 7,
    val milestonesEnabled: Boolean = true,
    /** Личный текст «зачем я это бросил» — показывается при тяге. */
    val motivationText: String? = null,
    val baseline: Baseline? = null,
    val stickyNotification: Boolean = false,
    /** Однократный нейтральный экран про вещества уже показан. */
    val substanceWarningAck: Boolean = false,
    val position: Int = 0,
    val archived: Boolean = false,
    val createdAt: Instant = Instant.EPOCH,
    val attempts: List<Attempt> = emptyList(),
    val milestones: List<Milestone> = emptyList(),
    val substitutes: List<Substitute> = emptyList()
) {
    val currentAttempt: Attempt? get() = attempts.firstOrNull { it.isCurrent }
}
