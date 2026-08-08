package dev.ashwake.domain.engine.blocking

import dev.ashwake.domain.model.blocking.BlockingRule
import dev.ashwake.domain.model.blocking.UnlockCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalTime

class BlockingEvaluatorTest {

    private val evaluator = BlockingEvaluator()
    private val now: Instant = Instant.parse("2026-06-01T08:00:00Z")

    private fun context(
        nowMinute: Int = 8 * 60,
        unfinished: List<String> = emptyList(),
        routines: Set<Long> = emptySet(),
        bypassUntil: Instant? = null
    ) = BlockingContext(
        nowMinute = nowMinute,
        now = now,
        unfinishedMorningHabits = unfinished,
        completedRoutineIds = routines,
        completedRoutineNames = mapOf(1L to "Утро"),
        bypassUntil = bypassUntil
    )

    private fun rule(
        enabled: Boolean = true,
        condition: UnlockCondition = UnlockCondition.MORNING_HABITS_DONE,
        routineId: Long? = null,
        unlockTime: LocalTime? = null,
        packages: List<String> = listOf("com.social.app")
    ) = BlockingRule(
        id = 1,
        name = "Утро без соцсетей",
        enabled = enabled,
        condition = condition,
        routineId = routineId,
        unlockTime = unlockTime,
        packages = packages
    )

    @Test
    fun `выключенное правило не блокирует ничего`() {
        val verdict = evaluator.evaluate(
            "com.social.app",
            listOf(rule(enabled = false)),
            context(unfinished = listOf("зарядка"))
        )
        assertFalse(verdict.blocked)
    }

    @Test
    fun `приложение вне списка правила не блокируется`() {
        val verdict = evaluator.evaluate(
            "com.other.app",
            listOf(rule()),
            context(unfinished = listOf("зарядка"))
        )
        assertFalse(verdict.blocked)
    }

    @Test
    fun `невыполненные утренние привычки блокируют`() {
        val verdict = evaluator.evaluate(
            "com.social.app",
            listOf(rule()),
            context(unfinished = listOf("зарядка", "вода"))
        )
        assertTrue(verdict.blocked)
        assertEquals(listOf("зарядка", "вода"), verdict.remaining)
        assertEquals("Утро без соцсетей", verdict.ruleName)
    }

    @Test
    fun `все привычки отмечены — доступ открыт`() {
        val verdict = evaluator.evaluate("com.social.app", listOf(rule()), context())
        assertFalse(verdict.blocked)
    }

    @Test
    fun `рутина блокирует, пока не пройдена`() {
        val target = rule(condition = UnlockCondition.ROUTINE_DONE, routineId = 1)

        assertTrue(evaluator.evaluate("com.social.app", listOf(target), context()).blocked)
        assertFalse(
            evaluator.evaluate(
                "com.social.app", listOf(target), context(routines = setOf(1L))
            ).blocked
        )
    }

    @Test
    fun `правило по времени открывает доступ после назначенного часа`() {
        val target = rule(
            condition = UnlockCondition.TIME_AFTER,
            unlockTime = LocalTime.of(10, 0)
        )

        assertTrue(
            evaluator.evaluate("com.social.app", listOf(target), context(nowMinute = 9 * 60)).blocked
        )
        assertFalse(
            evaluator.evaluate("com.social.app", listOf(target), context(nowMinute = 10 * 60)).blocked
        )
    }

    @Test
    fun `экстренный обход открывает доступ до истечения срока`() {
        val verdict = evaluator.evaluate(
            "com.social.app",
            listOf(rule()),
            context(unfinished = listOf("зарядка"), bypassUntil = now.plusSeconds(60))
        )
        assertFalse(verdict.blocked)
    }

    @Test
    fun `истёкший обход снова блокирует`() {
        val verdict = evaluator.evaluate(
            "com.social.app",
            listOf(rule()),
            context(unfinished = listOf("зарядка"), bypassUntil = now.minusSeconds(1))
        )
        assertTrue(verdict.blocked)
    }

    @Test
    fun `хватает одного невыполненного правила из нескольких`() {
        val satisfied = rule(condition = UnlockCondition.TIME_AFTER, unlockTime = LocalTime.of(6, 0))
        val blocking = rule().copy(id = 2, name = "Привычки")

        val verdict = evaluator.evaluate(
            "com.social.app",
            listOf(satisfied, blocking),
            context(unfinished = listOf("зарядка"))
        )
        assertTrue(verdict.blocked)
        assertEquals("Привычки", verdict.ruleName)
    }

    @Test
    fun `правило по рутине без указанной рутины не считается выполненным`() {
        val broken = rule(condition = UnlockCondition.ROUTINE_DONE, routineId = null)
        assertFalse(evaluator.isSatisfied(broken, context()))
    }

    @Test
    fun `правило по времени без времени не блокирует`() {
        val broken = rule(condition = UnlockCondition.TIME_AFTER, unlockTime = null)
        assertTrue(evaluator.isSatisfied(broken, context()))
    }

    @Test
    fun `задержка обхода пятнадцать секунд`() {
        assertEquals(15, evaluator.bypassDelaySeconds)
    }

    @Test
    fun `обход действует ограниченное время`() {
        val until = evaluator.bypassUntil(now)
        assertTrue(until.isAfter(now))
        assertTrue(until.isBefore(now.plusSeconds(3600)))
    }

    @Test
    fun `пустой список правил не блокирует`() {
        assertFalse(evaluator.evaluate("com.social.app", emptyList(), context()).blocked)
    }
}
