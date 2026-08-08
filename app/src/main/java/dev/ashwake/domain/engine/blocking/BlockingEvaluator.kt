package dev.ashwake.domain.engine.blocking

import dev.ashwake.domain.model.blocking.BlockingRule
import dev.ashwake.domain.model.blocking.BlockingVerdict
import dev.ashwake.domain.model.blocking.UnlockCondition
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Всё, что нужно знать движку о текущем моменте. */
data class BlockingContext(
    val nowMinute: Int,
    val now: Instant,
    /** Утренние привычки, ещё не отмеченные. Пусто — условие выполнено. */
    val unfinishedMorningHabits: List<String> = emptyList(),
    /** Рутины, пройденные сегодня. */
    val completedRoutineIds: Set<Long> = emptySet(),
    val completedRoutineNames: Map<Long, String> = emptyMap(),
    /** До этого момента действует экстренный обход. */
    val bypassUntil: Instant? = null
)

/**
 * Решает, блокировать ли приложение прямо сейчас (п. 7).
 *
 * Чистый класс без Android: «сейчас» приходит параметром, поэтому поведение
 * в 7 утра и в полдень проверяется тестом, а не подкручиванием часов телефона.
 *
 * Правило по умолчанию выключено, и выключенное правило не блокирует ничего —
 * функция, которая включается сама, у пользователя доверия не вызывает.
 */
@Singleton
class BlockingEvaluator @Inject constructor() {

    /**
     * Вердикт для конкретного приложения.
     *
     * Правила проверяются все: если приложение попало хотя бы под одно
     * невыполненное условие, оно заблокировано. Разрешающего правила нет
     * намеренно — иначе одно забытое исключение открывало бы всё.
     */
    fun evaluate(
        packageName: String,
        rules: List<BlockingRule>,
        context: BlockingContext
    ): BlockingVerdict {
        // Экстренный обход действует на всё сразу: он и нужен для случаев,
        // которые правилами не предусмотреть
        if (context.bypassUntil != null && context.bypassUntil.isAfter(context.now)) {
            return BlockingVerdict(blocked = false)
        }

        val blocking = rules
            .filter { it.enabled && packageName in it.packages }
            .firstOrNull { !isSatisfied(it, context) }
            ?: return BlockingVerdict(blocked = false)

        return BlockingVerdict(
            blocked = true,
            ruleName = blocking.name,
            ruleId = blocking.id,
            remaining = remainingFor(blocking, context),
            unlockAt = blocking.unlockTime.takeIf {
                blocking.condition == UnlockCondition.TIME_AFTER
            }
        )
    }

    /** Выполнено ли условие разблокировки. */
    fun isSatisfied(rule: BlockingRule, context: BlockingContext): Boolean =
        when (rule.condition) {
            UnlockCondition.MORNING_HABITS_DONE ->
                context.unfinishedMorningHabits.isEmpty()

            UnlockCondition.ROUTINE_DONE ->
                rule.routineId != null && rule.routineId in context.completedRoutineIds

            UnlockCondition.TIME_AFTER -> {
                val unlock = rule.unlockTime ?: return true
                context.nowMinute >= unlock.hour * 60 + unlock.minute
            }
        }

    /** Что осталось сделать. Текст оверлея строится отсюда. */
    fun remainingFor(rule: BlockingRule, context: BlockingContext): List<String> =
        when (rule.condition) {
            UnlockCondition.MORNING_HABITS_DONE -> context.unfinishedMorningHabits

            UnlockCondition.ROUTINE_DONE -> {
                val name = rule.routineId?.let { context.completedRoutineNames[it] }
                listOf(name?.let { "Пройти рутину «$it»" } ?: "Пройти рутину")
            }

            UnlockCondition.TIME_AFTER -> {
                val unlock = rule.unlockTime
                listOf(
                    if (unlock != null) "Откроется в %02d:%02d".format(unlock.hour, unlock.minute)
                    else "Ждать назначенного времени"
                )
            }
        }

    /**
     * Момент, до которого действует экстренный обход.
     *
     * Задержка перед обходом (п. 7) нужна не как наказание, а как пауза:
     * пятнадцати секунд достаточно, чтобы порыв «просто гляну» прошёл сам.
     */
    fun bypassUntil(now: Instant, durationMinutes: Int = BYPASS_MINUTES): Instant =
        now.plusSeconds(durationMinutes * 60L)

    val bypassDelaySeconds: Int get() = BYPASS_DELAY_SECONDS

    private companion object {
        const val BYPASS_DELAY_SECONDS = 15
        /** На сколько открывается доступ после обхода. */
        const val BYPASS_MINUTES = 5
    }
}
