package dev.ashwake.domain.engine.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CorrelationAnalyzerTest {

    private val analyzer = CorrelationAnalyzer()
    private val start = LocalDate.of(2026, 3, 1)

    private fun days(count: Int): List<LocalDate> = (0 until count).map { start.plusDays(it.toLong()) }

    // --- коэффициент --------------------------------------------------------

    @Test
    fun `полная прямая связь даёт единицу`() {
        val x = listOf(0f, 1f, 0f, 1f, 0f, 1f)
        val y = listOf(2f, 4f, 2f, 4f, 2f, 4f)
        assertEquals(1f, analyzer.pearson(x, y)!!, 0.0001f)
    }

    @Test
    fun `полная обратная связь даёт минус единицу`() {
        val x = listOf(0f, 1f, 0f, 1f)
        val y = listOf(5f, 1f, 5f, 1f)
        assertEquals(-1f, analyzer.pearson(x, y)!!, 0.0001f)
    }

    @Test
    fun `ряд без разброса не даёт коэффициента вместо нуля`() {
        // Привычка выполнена каждый день: связи не существует, а не «связь нулевая»
        assertNull(analyzer.pearson(listOf(1f, 1f, 1f, 1f), listOf(3f, 4f, 2f, 5f)))
        assertNull(analyzer.pearson(listOf(0f, 1f, 0f, 1f), listOf(3f, 3f, 3f, 3f)))
    }

    @Test
    fun `ряды разной длины не считаются`() {
        assertNull(analyzer.pearson(listOf(1f, 0f), listOf(1f)))
    }

    // --- анализ -------------------------------------------------------------

    @Test
    fun `выборка меньше двух недель не анализируется`() {
        val dates = days(10)
        val report = analyzer.analyze(
            habitNames = mapOf(1L to "зарядка"),
            habitDays = mapOf(1L to dates.filterIndexed { i, _ -> i % 2 == 0 }.toSet()),
            moodByDate = dates.associateWith { 3 },
            energyByDate = dates.associateWith { 3 }
        )
        assertFalse(report.hasEnoughData)
        assertTrue(report.pairs.isEmpty())
    }

    @Test
    fun `находит связь привычки с настроением`() {
        val dates = days(20)
        // В дни с зарядкой настроение 5, без неё 3
        val withHabit = dates.filterIndexed { index, _ -> index % 2 == 0 }.toSet()
        val mood = dates.associateWith { if (it in withHabit) 5 else 3 }

        val report = analyzer.analyze(
            habitNames = mapOf(1L to "зарядка"),
            habitDays = mapOf(1L to withHabit),
            moodByDate = mood,
            energyByDate = emptyMap()
        )
        val pair = report.pairs.first { it.metric == CorrelationMetric.MOOD && it.lagDays == 0 }

        assertTrue(report.hasEnoughData)
        assertEquals(1f, pair.coefficient, 0.0001f)
        assertEquals(2f, pair.meanDifference, 0.0001f)
    }

    @Test
    fun `находит отрицательную связь`() {
        val dates = days(20)
        val withHabit = dates.filterIndexed { index, _ -> index % 2 == 0 }.toSet()
        val energy = dates.associateWith { if (it in withHabit) 2 else 4 }

        val report = analyzer.analyze(
            habitNames = mapOf(1L to "поздний кофе"),
            habitDays = mapOf(1L to withHabit),
            moodByDate = emptyMap(),
            energyByDate = energy
        )
        val pair = report.pairs.first { it.metric == CorrelationMetric.ENERGY && it.lagDays == 0 }

        assertTrue(pair.coefficient < 0)
        assertEquals(-2f, pair.meanDifference, 0.0001f)
    }

    @Test
    fun `лаг в день сдвигает привычку относительно шкалы`() {
        val dates = days(20)
        // Привычка по чётным дням, а шкала растёт на следующий день
        val withHabit = dates.filterIndexed { index, _ -> index % 2 == 0 }.toSet()
        val mood = dates.associateWithIndexed { index, _ -> if (index % 2 == 1) 5 else 3 }

        val report = analyzer.analyze(
            habitNames = mapOf(1L to "тренировка"),
            habitDays = mapOf(1L to withHabit),
            moodByDate = mood,
            energyByDate = emptyMap()
        )
        val lagged = report.pairs.first { it.lagDays == 1 && it.metric == CorrelationMetric.MOOD }
        val sameDay = report.pairs.first { it.lagDays == 0 && it.metric == CorrelationMetric.MOOD }

        assertTrue("лаг должен ловить перенос эффекта", lagged.coefficient > 0.9f)
        assertTrue("в тот же день связь обратная", sameDay.coefficient < 0)
    }

    @Test
    fun `привычка без вариации не попадает в отчёт`() {
        val dates = days(20)
        val report = analyzer.analyze(
            habitNames = mapOf(1L to "каждый день"),
            habitDays = mapOf(1L to dates.toSet()),
            moodByDate = dates.associateWithIndexed { index, _ -> 3 + index % 3 },
            energyByDate = emptyMap()
        )
        assertTrue(report.pairs.isEmpty())
    }

    @Test
    fun `дни без заполненной шкалы выбрасываются, а не считаются нулём`() {
        val dates = days(20)
        val withHabit = dates.filterIndexed { index, _ -> index % 2 == 0 }.toSet()
        // Шкала заполнена только в 16 днях из 20
        val mood = dates.take(16).associateWith { if (it in withHabit) 5 else 3 }

        val report = analyzer.analyze(
            habitNames = mapOf(1L to "зарядка"),
            habitDays = mapOf(1L to withHabit),
            moodByDate = mood,
            energyByDate = emptyMap()
        )
        val pair = report.pairs.first { it.metric == CorrelationMetric.MOOD && it.lagDays == 0 }
        assertEquals(16, pair.sampleSize)
    }

    @Test
    fun `топ положительных и отрицательных не пересекаются`() {
        val dates = days(20)
        val good = dates.filterIndexed { index, _ -> index % 2 == 0 }.toSet()
        val bad = dates.filterIndexed { index, _ -> index % 2 == 1 }.toSet()
        val mood = dates.associateWith { if (it in good) 5 else 2 }

        val report = analyzer.analyze(
            habitNames = mapOf(1L to "хорошая", 2L to "плохая"),
            habitDays = mapOf(1L to good, 2L to bad),
            moodByDate = mood,
            energyByDate = emptyMap()
        )
        assertTrue(report.topPositive().all { it.coefficient > 0 })
        assertTrue(report.topNegative().all { it.coefficient < 0 })
        assertTrue(report.topPositive().size <= 3)
    }

    // --- формулировки -------------------------------------------------------

    @Test
    fun `формулировка говорит о связи, а не о причине`() {
        val pair = CorrelationPair(
            habitId = 1, habitName = "зарядка", metric = CorrelationMetric.MOOD,
            lagDays = 0, coefficient = 0.6f, sampleSize = 20, meanDifference = 0.8f
        )
        val text = analyzer.describe(pair)

        assertEquals("«зарядка»: в дни с привычкой настроение выше в среднем на 0.8", text)
        assertFalse("нельзя обещать эффект", text.contains("повыш"))
        assertFalse(text.contains("улучш"))
        assertNotNull(analyzer.disclaimer)
    }

    @Test
    fun `формулировка для лага говорит про следующий день`() {
        val pair = CorrelationPair(
            habitId = 1, habitName = "тренировка", metric = CorrelationMetric.ENERGY,
            lagDays = 1, coefficient = 0.5f, sampleSize = 20, meanDifference = -1.2f
        )
        assertEquals(
            "«тренировка»: на следующий день энергия ниже в среднем на 1.2",
            analyzer.describe(pair)
        )
    }
}

/** Ассоциация с индексом — только для читаемости тестов. */
private fun <K, V> List<K>.associateWithIndexed(transform: (Int, K) -> V): Map<K, V> =
    mapIndexed { index, key -> key to transform(index, key) }.toMap()
