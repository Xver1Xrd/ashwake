package dev.ashwake.domain.engine.abstinence

import dev.ashwake.domain.model.abstinence.CravingEvent
import dev.ashwake.domain.model.abstinence.CravingTrigger
import javax.inject.Inject
import javax.inject.Singleton

data class TriggerCount(val trigger: CravingTrigger?, val count: Int)

data class PeakWindow(
    val startHour: Int,
    val endHour: Int,
    val weekdaysOnly: Boolean,
    val weekendOnly: Boolean,
    val count: Int
)

data class CravingAnalytics(
    /** [день недели 1..7][час 0..23] — число эпизодов. */
    val heatmap: Array<IntArray>,
    val topTriggers: List<TriggerCount>,
    val totalEvents: Int,
    val resistedShare: Float?,
    val averageIntensity: Float?,
    val peak: PeakWindow?
) {
    /** Массивы в data class ломают equals — сравнение здесь не нужно и не используется. */
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

/**
 * Аналитика тяги (п. 4): когда накрывает, от чего и чем кончается.
 *
 * Цель — вывод вида «пик тяги — будни 19:00–21:00», то есть подсказка,
 * куда заранее поставить заместитель. Поэтому окно ищется не по одному часу,
 * а по [WINDOW_HOURS] подряд: одиночный час — это шум выборки.
 */
@Singleton
class CravingAnalyzer @Inject constructor() {

    fun analyze(
        events: List<CravingEvent>,
        triggers: List<CravingTrigger> = emptyList()
    ): CravingAnalytics {
        val heatmap = Array(8) { IntArray(24) }
        events.forEach { event ->
            val day = event.dayOfWeek.coerceIn(1, 7)
            val hour = event.hourOfDay.coerceIn(0, 23)
            heatmap[day][hour]++
        }

        val triggersById = triggers.associateBy { it.id }
        val topTriggers = events
            .groupingBy { it.triggerId }
            .eachCount()
            .map { (id, count) -> TriggerCount(id?.let { triggersById[it] }, count) }
            .sortedByDescending { it.count }

        val withOutcome = events.mapNotNull { it.resisted }
        val resistedShare =
            if (withOutcome.isEmpty()) null
            else withOutcome.count { it }.toFloat() / withOutcome.size

        val averageIntensity =
            if (events.isEmpty()) null
            else events.sumOf { it.intensity }.toFloat() / events.size

        return CravingAnalytics(
            heatmap = heatmap,
            topTriggers = topTriggers,
            totalEvents = events.size,
            resistedShare = resistedShare,
            averageIntensity = averageIntensity,
            peak = peakWindow(events)
        )
    }

    /**
     * Самое плотное окно из [WINDOW_HOURS] подряд идущих часов.
     * Дополнительно определяется, приходится ли оно преимущественно на будни
     * или на выходные — при явном перекосе (доля выше [SKEW_THRESHOLD]).
     */
    fun peakWindow(events: List<CravingEvent>): PeakWindow? {
        if (events.size < MIN_EVENTS_FOR_PEAK) return null

        val byHour = IntArray(24)
        events.forEach { byHour[it.hourOfDay.coerceIn(0, 23)]++ }

        var bestStart = 0
        var bestCount = -1
        for (start in 0..(24 - WINDOW_HOURS)) {
            val count = (start until start + WINDOW_HOURS).sumOf { byHour[it] }
            if (count > bestCount) {
                bestCount = count
                bestStart = start
            }
        }
        if (bestCount <= 0) return null

        val inWindow = events.filter {
            it.hourOfDay in bestStart until (bestStart + WINDOW_HOURS)
        }
        val weekdayCount = inWindow.count { it.dayOfWeek in 1..5 }
        val weekdayShare = weekdayCount.toFloat() / inWindow.size

        return PeakWindow(
            startHour = bestStart,
            endHour = bestStart + WINDOW_HOURS,
            weekdaysOnly = weekdayShare >= SKEW_THRESHOLD,
            weekendOnly = (1f - weekdayShare) >= SKEW_THRESHOLD,
            count = bestCount
        )
    }

    /** Формулировка для экрана. Осторожная: это наблюдение, а не диагноз. */
    fun describePeak(peak: PeakWindow?): String? = describeCravingPeak(peak)

    private companion object {
        const val WINDOW_HOURS = 2
        /** Меньше пяти эпизодов — говорить о пике рано. */
        const val MIN_EVENTS_FOR_PEAK = 5
        const val SKEW_THRESHOLD = 0.7f
    }
}

/**
 * Текст о пике вынесен из класса: экрану он нужен без инъекции движка,
 * а зависимостей у формулировки никаких.
 */
fun describeCravingPeak(peak: PeakWindow?): String? {
    if (peak == null) return null
    val period = when {
        peak.weekdaysOnly -> "будни "
        peak.weekendOnly -> "выходные "
        else -> ""
    }
    return "Пик тяги — $period%02d:00–%02d:00".format(peak.startHour, peak.endHour)
}
