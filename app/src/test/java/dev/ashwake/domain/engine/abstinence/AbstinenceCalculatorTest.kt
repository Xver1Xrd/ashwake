package dev.ashwake.domain.engine.abstinence

import dev.ashwake.domain.model.abstinence.Abstinence
import dev.ashwake.domain.model.abstinence.AbstinenceMode
import dev.ashwake.domain.model.abstinence.Attempt
import dev.ashwake.domain.model.abstinence.Baseline
import dev.ashwake.domain.model.abstinence.Milestone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class AbstinenceCalculatorTest {

    private val calculator = AbstinenceCalculator()
    private val now: Instant = Instant.parse("2026-06-01T12:00:00Z")

    private fun daysAgo(days: Long): Instant = now.minus(Duration.ofDays(days))

    private fun abstinence(
        attempts: List<Attempt>,
        mode: AbstinenceMode = AbstinenceMode.STRICT,
        baseline: Baseline? = null,
        milestones: List<Milestone> = emptyList()
    ) = Abstinence(
        id = 1,
        name = "Не курю",
        mode = mode,
        baseline = baseline,
        attempts = attempts,
        milestones = milestones
    )

    @Test
    fun `счётчик текущей попытки идёт от её старта`() {
        val stats = calculator.stats(
            abstinence(listOf(Attempt(abstinenceId = 1, ordinal = 1, startedAt = daysAgo(10)))),
            now
        )
        assertEquals(10L, stats.currentDays)
        assertEquals(1, stats.attemptNumber)
    }

    @Test
    fun `рекорд берётся из лучшей попытки, даже если она в прошлом`() {
        val attempts = listOf(
            Attempt(abstinenceId = 1, ordinal = 1, startedAt = daysAgo(100), endedAt = daysAgo(60)),
            Attempt(abstinenceId = 1, ordinal = 2, startedAt = daysAgo(50), endedAt = daysAgo(45)),
            Attempt(abstinenceId = 1, ordinal = 3, startedAt = daysAgo(10))
        )
        val stats = calculator.stats(abstinence(attempts), now)

        assertEquals(40L, stats.record.toDays())
        assertEquals(10L, stats.currentDays)
        assertEquals(3, stats.attemptNumber)
    }

    @Test
    fun `всего чистых дней складывается по всем попыткам`() {
        val attempts = listOf(
            Attempt(abstinenceId = 1, ordinal = 1, startedAt = daysAgo(100), endedAt = daysAgo(60)),
            Attempt(abstinenceId = 1, ordinal = 2, startedAt = daysAgo(50), endedAt = daysAgo(45)),
            Attempt(abstinenceId = 1, ordinal = 3, startedAt = daysAgo(10))
        )
        val stats = calculator.stats(abstinence(attempts), now)
        assertEquals(40L + 5L + 10L, stats.totalCleanDays)
    }

    @Test
    fun `в режиме GENTLE штрафные дни отодвигают старт, а не обнуляют попытку`() {
        val attempt = Attempt(
            abstinenceId = 1, ordinal = 1, startedAt = daysAgo(30), penaltyDays = 7
        )
        val stats = calculator.stats(abstinence(listOf(attempt), AbstinenceMode.GENTLE), now)

        assertEquals(23L, stats.currentDays)
        assertEquals(1, stats.attemptNumber)
    }

    @Test
    fun `штраф больше прожитого не уводит счётчик в минус`() {
        val attempt = Attempt(
            abstinenceId = 1, ordinal = 1, startedAt = daysAgo(3), penaltyDays = 7
        )
        val stats = calculator.stats(abstinence(listOf(attempt), AbstinenceMode.GENTLE), now)
        assertEquals(0L, stats.currentDays)
        assertTrue(stats.current >= Duration.ZERO)
    }

    @Test
    fun `ближайшая веха выбирается из стандартных`() {
        val stats = calculator.stats(
            abstinence(listOf(Attempt(abstinenceId = 1, ordinal = 1, startedAt = daysAgo(10)))),
            now
        )
        assertNotNull(stats.nextMilestone)
        assertEquals(14, stats.nextMilestone?.days)
    }

    @Test
    fun `прогресс до вехи считается от предыдущей, а не от нуля`() {
        // 10 дней: предыдущая веха 7, следующая 14 → пройдено 3 из 7
        val stats = calculator.stats(
            abstinence(listOf(Attempt(abstinenceId = 1, ordinal = 1, startedAt = daysAgo(10)))),
            now
        )
        assertEquals(3f / 7f, stats.progressToNextMilestone, 0.01f)
    }

    @Test
    fun `кастомная веха попадает в список и может стать ближайшей`() {
        val custom = Milestone(abstinenceId = 1, days = 12, title = "до дня рождения", custom = true)
        val stats = calculator.stats(
            abstinence(
                listOf(Attempt(abstinenceId = 1, ordinal = 1, startedAt = daysAgo(10))),
                milestones = listOf(custom)
            ),
            now
        )
        assertEquals(12, stats.nextMilestone?.days)
    }

    @Test
    fun `без baseline блок экономии отсутствует`() {
        val stats = calculator.stats(
            abstinence(listOf(Attempt(abstinenceId = 1, ordinal = 1, startedAt = daysAgo(10)))),
            now
        )
        assertNull(stats.savings)
    }

    @Test
    fun `экономия считается из baseline`() {
        // 20 сигарет в день по 12 рублей, 21 день → 420 сигарет и 5040 рублей
        val baseline = Baseline("сигарет", unitsPerDay = 20f, costPerUnit = 12f)
        val stats = calculator.stats(
            abstinence(
                listOf(Attempt(abstinenceId = 1, ordinal = 1, startedAt = daysAgo(21))),
                baseline = baseline
            ),
            now
        )
        assertEquals(420f, stats.savings!!.units, 0.5f)
        assertEquals(5040f, stats.savings!!.money, 5f)
    }

    @Test
    fun `вехи отключаются флагом`() {
        val target = abstinence(listOf(Attempt(abstinenceId = 1, ordinal = 1, startedAt = daysAgo(10))))
            .copy(milestonesEnabled = false)
        assertNull(calculator.stats(target, now).nextMilestone)
    }

    @Test
    fun `достигнутые но не отмеченные вехи возвращаются один раз`() {
        val milestones = listOf(
            Milestone(id = 1, abstinenceId = 1, days = 7, title = "7 дней"),
            Milestone(id = 2, abstinenceId = 1, days = 14, title = "14 дней", reachedAt = now),
            Milestone(id = 3, abstinenceId = 1, days = 30, title = "30 дней")
        )
        val target = abstinence(
            listOf(Attempt(abstinenceId = 1, ordinal = 1, startedAt = daysAgo(20))),
            milestones = milestones
        )
        val reached = calculator.newlyReached(target, now)

        assertEquals(1, reached.size)
        assertEquals(7, reached.first().days)
    }

    @Test
    fun `склонение дней и лет в названиях вех`() {
        assertEquals("1 день", calculator.titleOf(1))
        assertEquals("3 дня", calculator.titleOf(3))
        assertEquals("14 дней", calculator.titleOf(14))
        assertEquals("1 год", calculator.titleOf(365))
        assertEquals("2 года", calculator.titleOf(730))
        assertEquals("5 лет", calculator.titleOf(1825))
    }

    @Test
    fun `пустая история не падает`() {
        val stats = calculator.stats(abstinence(emptyList()), now)
        assertEquals(0L, stats.currentDays)
        assertEquals(0L, stats.totalCleanDays)
        assertEquals(Duration.ZERO, stats.record)
    }
}
