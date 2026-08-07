package dev.ashwake.domain.engine.abstinence

import dev.ashwake.domain.model.abstinence.CravingEvent
import dev.ashwake.domain.model.abstinence.CravingTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CravingAnalyzerTest {

    private val analyzer = CravingAnalyzer()
    private val at: Instant = Instant.parse("2026-06-01T12:00:00Z")

    private fun event(
        hour: Int,
        dayOfWeek: Int,
        intensity: Int = 3,
        triggerId: Long? = null,
        resisted: Boolean? = null
    ) = CravingEvent(
        abstinenceId = 1,
        at = at,
        hourOfDay = hour,
        dayOfWeek = dayOfWeek,
        intensity = intensity,
        triggerId = triggerId,
        resisted = resisted
    )

    @Test
    fun `heatmap считает эпизоды по дню и часу`() {
        val events = listOf(event(19, 1), event(19, 1), event(20, 3))
        val analytics = analyzer.analyze(events)

        assertEquals(2, analytics.heatmap[1][19])
        assertEquals(1, analytics.heatmap[3][20])
        assertEquals(0, analytics.heatmap[5][10])
        assertEquals(3, analytics.totalEvents)
    }

    @Test
    fun `топ триггеров отсортирован по частоте`() {
        val triggers = listOf(
            CravingTrigger(id = 1, code = "STRESS", label = "Стресс"),
            CravingTrigger(id = 2, code = "COMPANY", label = "Компания")
        )
        val events = listOf(
            event(10, 1, triggerId = 1), event(11, 2, triggerId = 1),
            event(12, 3, triggerId = 1), event(13, 4, triggerId = 2)
        )
        val analytics = analyzer.analyze(events, triggers)

        assertEquals("Стресс", analytics.topTriggers.first().trigger?.label)
        assertEquals(3, analytics.topTriggers.first().count)
        assertEquals("Компания", analytics.topTriggers[1].trigger?.label)
    }

    @Test
    fun `доля переждённых считается только по заполненным исходам`() {
        val events = listOf(
            event(10, 1, resisted = true),
            event(11, 1, resisted = true),
            event(12, 1, resisted = false),
            event(13, 1, resisted = null)
        )
        val analytics = analyzer.analyze(events)
        // Три заполненных из четырёх, из них два успешных
        assertEquals(2f / 3f, analytics.resistedShare!!, 0.001f)
    }

    @Test
    fun `без заполненных исходов доля не выдумывается`() {
        val analytics = analyzer.analyze(listOf(event(10, 1), event(11, 2)))
        assertNull(analytics.resistedShare)
    }

    @Test
    fun `средняя интенсивность`() {
        val events = listOf(event(10, 1, intensity = 2), event(11, 1, intensity = 4))
        assertEquals(3f, analyzer.analyze(events).averageIntensity!!, 0.001f)
    }

    @Test
    fun `пик определяется окном из двух часов, а не одиночным часом`() {
        val events = listOf(
            event(19, 1), event(20, 1), event(19, 2), event(20, 2), event(19, 3),
            event(3, 6)
        )
        val peak = analyzer.peakWindow(events)!!
        assertEquals(19, peak.startHour)
        assertEquals(21, peak.endHour)
        assertEquals(5, peak.count)
    }

    @Test
    fun `перекос в будни отмечается флагом`() {
        val events = (1..5).flatMap { day -> listOf(event(19, day), event(20, day)) }
        val peak = analyzer.peakWindow(events)!!

        assertTrue(peak.weekdaysOnly)
        assertFalse(peak.weekendOnly)
        assertEquals("Пик тяги — будни 19:00–21:00", analyzer.describePeak(peak))
    }

    @Test
    fun `перекос в выходные отмечается флагом`() {
        val events = listOf(6, 7).flatMap { day ->
            listOf(event(21, day), event(22, day), event(21, day))
        }
        val peak = analyzer.peakWindow(events)!!
        assertTrue(peak.weekendOnly)
    }

    @Test
    fun `на малой выборке пик не объявляется`() {
        val events = listOf(event(19, 1), event(20, 1))
        assertNull(analyzer.peakWindow(events))
        assertNull(analyzer.describePeak(null))
    }

    @Test
    fun `пустой список не падает`() {
        val analytics = analyzer.analyze(emptyList())
        assertEquals(0, analytics.totalEvents)
        assertNull(analytics.peak)
        assertNull(analytics.averageIntensity)
    }
}
