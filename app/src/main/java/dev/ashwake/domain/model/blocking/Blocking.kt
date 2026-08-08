package dev.ashwake.domain.model.blocking

import java.time.Instant
import java.time.LocalTime

/** Условие разблокировки (п. 7). */
enum class UnlockCondition {
    /** Все утренние привычки отмечены. */
    MORNING_HABITS_DONE,
    /** Пройдена конкретная рутина. */
    ROUTINE_DONE,
    /** Наступило заданное время. */
    TIME_AFTER
}

/**
 * Способ определения приложения на переднем плане.
 *
 * USAGE_STATS честнее по разрешениям и не рискует модерацией магазина,
 * но опрашивает систему с задержкой около секунды.
 * ACCESSIBILITY мгновенный и надёжный, но требует разрешения, которого
 * пользователи справедливо опасаются. По умолчанию первый (docs/03-plan.md).
 */
enum class DetectionMode { USAGE_STATS, ACCESSIBILITY }

data class BlockingRule(
    val id: Long = 0,
    val name: String,
    /** Функция выключена по умолчанию (п. 7). */
    val enabled: Boolean = false,
    val condition: UnlockCondition = UnlockCondition.MORNING_HABITS_DONE,
    val routineId: Long? = null,
    val unlockTime: LocalTime? = null,
    val packages: List<String> = emptyList(),
    val createdAt: Instant = Instant.EPOCH
)

/** Запись об экстренном обходе: обход возможен, но не бесследен (п. 7). */
data class BypassRecord(
    val id: Long = 0,
    val at: Instant,
    val packageName: String,
    val ruleId: Long?,
    val delaySeconds: Int
)

/** Что мешает разблокировке прямо сейчас. */
data class BlockingVerdict(
    val blocked: Boolean,
    val ruleName: String? = null,
    val ruleId: Long? = null,
    /** Что осталось сделать — это и показывает оверлей. */
    val remaining: List<String> = emptyList(),
    val unlockAt: LocalTime? = null
)
