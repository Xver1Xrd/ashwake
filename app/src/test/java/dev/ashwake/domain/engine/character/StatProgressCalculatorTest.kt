package dev.ashwake.domain.engine.character

import dev.ashwake.core.model.Sphere
import dev.ashwake.core.model.Stat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatProgressCalculatorTest {

    private val calculator = StatProgressCalculator()

    @Test
    fun `значение считается по формуле корня из очков делённых на четыре`() {
        assertEquals(0, calculator.valueOf(0))
        assertEquals(1, calculator.valueOf(4))
        assertEquals(2, calculator.valueOf(16))
        assertEquals(5, calculator.valueOf(100))
        assertEquals(10, calculator.valueOf(400))
    }

    @Test
    fun `рост замедляется на высоких значениях`() {
        // От 1 до 2 нужно 12 очков, от 10 до 11 — уже 84
        val lowStep = calculator.pointsForValue(2) - calculator.pointsForValue(1)
        val highStep = calculator.pointsForValue(11) - calculator.pointsForValue(10)
        assertTrue("шаг должен расти: $lowStep против $highStep", highStep > lowStep)
    }

    @Test
    fun `порог очков согласован со значением`() {
        (0..20).forEach { value ->
            assertEquals(value, calculator.valueOf(calculator.pointsForValue(value)))
        }
    }

    @Test
    fun `прогресс до следующего значения в пределах нуля и единицы`() {
        listOf(0L, 5L, 50L, 399L, 1000L).forEach { points ->
            assertTrue(calculator.progressToNext(points) in 0f..1f)
        }
    }

    @Test
    fun `отрицательные очки не ломают расчёт`() {
        assertEquals(0, calculator.valueOf(-100))
    }

    @Test
    fun `сфера привычки определяет характеристику`() {
        assertEquals(Stat.STRENGTH, calculator.sphereStat(Sphere.SPORT))
        assertEquals(Stat.INTELLECT, calculator.sphereStat(Sphere.STUDY))
        assertEquals(Stat.WILL, calculator.sphereStat(Sphere.MENTAL))
        assertEquals(Stat.ENDURANCE, calculator.sphereStat(Sphere.HEALTH))
        assertEquals(Stat.AGILITY, calculator.sphereStat(Sphere.CHORES))
        assertNull(calculator.sphereStat(null))
    }

    @Test
    fun `день отказа качает и волю, и выносливость`() {
        val points = calculator.pointsFor(StatSource.ABSTINENCE_DAY)
        assertTrue(Stat.WILL in points)
        assertTrue(Stat.ENDURANCE in points)
    }

    @Test
    fun `минимум в плохой день качает волю независимо от сферы`() {
        val points = calculator.pointsFor(StatSource.HABIT_MINIMUM, Sphere.SPORT)
        assertTrue(Stat.WILL in points)
        assertTrue(Stat.STRENGTH in points)
    }

    @Test
    fun `спортивная привычка качает силу`() {
        val points = calculator.pointsFor(StatSource.HABIT_DONE, Sphere.SPORT)
        assertEquals(setOf(Stat.STRENGTH), points.keys)
    }

    @Test
    fun `закрытие залежавшейся задачи качает удачу`() {
        assertEquals(setOf(Stat.LUCK), calculator.pointsFor(StatSource.STALE_TASK_CLOSED).keys)
    }
}
