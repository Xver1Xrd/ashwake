package dev.ashwake.domain.engine.analytics

import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

enum class CorrelationMetric { MOOD, ENERGY }

data class CorrelationPair(
    val habitId: Long,
    val habitName: String,
    val metric: CorrelationMetric,
    /** 0 — тот же день, 1 — привычка сегодня, шкала завтра. */
    val lagDays: Int,
    val coefficient: Float,
    val sampleSize: Int,
    /** На сколько в среднем выше шкала в дни с привычкой. Это и показываем человеку. */
    val meanDifference: Float
) {
    val isPositive: Boolean get() = coefficient > 0
}

data class CorrelationReport(
    val pairs: List<CorrelationPair>,
    val sampleDays: Int,
    /** Данных мало — выводов не делаем вовсе. */
    val hasEnoughData: Boolean
) {
    fun topPositive(count: Int = 3): List<CorrelationPair> =
        pairs.filter { it.coefficient > 0 }.sortedByDescending { it.coefficient }.take(count)

    fun topNegative(count: Int = 3): List<CorrelationPair> =
        pairs.filter { it.coefficient < 0 }.sortedBy { it.coefficient }.take(count)
}

/**
 * Связь привычек с настроением и энергией (п. 10).
 *
 * Считается коэффициент Пирсона по парам «привычка ↔ шкала» с лагом 0 и 1 день.
 *
 * Три ограничения стоят здесь намеренно, потому что аналитика на своих данных
 * очень легко превращается во вранье:
 * - выборка меньше [MIN_SAMPLE_DAYS] дней не анализируется вовсе;
 * - день без заполненной шкалы выбрасывается, а не считается нулём;
 * - если привычка выполнена во все дни или ни в один, коэффициент не определён —
 *   возвращается null, а не ноль.
 *
 * Формулировки в интерфейсе строятся из [describe] и всегда говорят о связи,
 * а не о причине.
 */
@Singleton
class CorrelationAnalyzer @Inject constructor() {

    /** Коэффициент Пирсона. null, если у любого ряда нет разброса. */
    fun pearson(x: List<Float>, y: List<Float>): Float? {
        if (x.size != y.size || x.size < 2) return null

        val meanX = x.average().toFloat()
        val meanY = y.average().toFloat()

        var covariance = 0.0
        var varianceX = 0.0
        var varianceY = 0.0
        for (index in x.indices) {
            val dx = (x[index] - meanX).toDouble()
            val dy = (y[index] - meanY).toDouble()
            covariance += dx * dy
            varianceX += dx * dx
            varianceY += dy * dy
        }

        // Нет разброса — связи не существует, а не «связь нулевая»
        if (varianceX <= EPSILON || varianceY <= EPSILON) return null
        return (covariance / sqrt(varianceX * varianceY)).toFloat()
    }

    /**
     * @param habitDays для каждой привычки — дни, в которые она выполнена
     * @param moodByDate настроение по датам, только заполненные дни
     * @param energyByDate энергия по датам
     */
    fun analyze(
        habitNames: Map<Long, String>,
        habitDays: Map<Long, Set<LocalDate>>,
        moodByDate: Map<LocalDate, Int>,
        energyByDate: Map<LocalDate, Int>,
        minSampleDays: Int = MIN_SAMPLE_DAYS
    ): CorrelationReport {
        val sampleDays = (moodByDate.keys + energyByDate.keys).size
        if (sampleDays < minSampleDays) {
            return CorrelationReport(emptyList(), sampleDays, hasEnoughData = false)
        }

        val pairs = mutableListOf<CorrelationPair>()
        habitNames.forEach { (habitId, name) ->
            val days = habitDays[habitId].orEmpty()
            listOf(
                CorrelationMetric.MOOD to moodByDate,
                CorrelationMetric.ENERGY to energyByDate
            ).forEach { (metric, values) ->
                listOf(0, 1).forEach { lag ->
                    build(habitId, name, days, values, metric, lag, minSampleDays)
                        ?.let { pairs += it }
                }
            }
        }

        return CorrelationReport(
            pairs = pairs.sortedByDescending { abs(it.coefficient) },
            sampleDays = sampleDays,
            hasEnoughData = true
        )
    }

    private fun build(
        habitId: Long,
        habitName: String,
        habitDays: Set<LocalDate>,
        values: Map<LocalDate, Int>,
        metric: CorrelationMetric,
        lagDays: Int,
        minSampleDays: Int
    ): CorrelationPair? {
        val x = mutableListOf<Float>()
        val y = mutableListOf<Float>()
        val withHabit = mutableListOf<Float>()
        val withoutHabit = mutableListOf<Float>()

        // Граница окна: при лаге в день у самого раннего дня предыдущего просто нет
        // в данных. Считать его «привычка не выполнена» — значит выдумать разброс
        // там, где его нет, и получить связь на пустом месте.
        val earliest = values.keys.minOrNull()

        // Ряд строится по дням, где шкала заполнена: пропуск ритуала —
        // это отсутствие данных, а не плохое настроение
        values.forEach { (date, value) ->
            val habitDate = date.minusDays(lagDays.toLong())
            if (earliest != null && habitDate < earliest) return@forEach

            val done = habitDate in habitDays
            x += if (done) 1f else 0f
            y += value.toFloat()
            if (done) withHabit += value.toFloat() else withoutHabit += value.toFloat()
        }

        if (x.size < minSampleDays) return null
        if (withHabit.isEmpty() || withoutHabit.isEmpty()) return null

        val coefficient = pearson(x, y) ?: return null
        return CorrelationPair(
            habitId = habitId,
            habitName = habitName,
            metric = metric,
            lagDays = lagDays,
            coefficient = coefficient,
            sampleSize = x.size,
            meanDifference = (withHabit.average() - withoutHabit.average()).toFloat()
        )
    }

    /**
     * Формулировка для экрана.
     *
     * Осторожная намеренно: «в дни с зарядкой настроение выше в среднем на 0.8».
     * Слова «повышает» или «улучшает» здесь недопустимы — это корреляция,
     * а не причинность, и приложение не должно делать вид, что знает больше.
     */
    fun describe(pair: CorrelationPair): String {
        val metric = when (pair.metric) {
            CorrelationMetric.MOOD -> "настроение"
            CorrelationMetric.ENERGY -> "энергия"
        }
        val direction = if (pair.meanDifference >= 0) "выше" else "ниже"
        // Локаль зафиксирована: иначе разделитель менялся бы вместе с настройками
        // устройства, и один и тот же вывод выглядел бы по-разному
        val amount = String.format(Locale.US, "%.1f", abs(pair.meanDifference))
        val when_ = if (pair.lagDays == 0) "в дни с привычкой" else "на следующий день"

        return "«${pair.habitName}»: $when_ $metric $direction в среднем на $amount"
    }

    /** Оговорка, которая должна стоять рядом с любым выводом (п. 10). */
    val disclaimer: String
        get() = "Это связь, а не причина: одно наблюдение по вашим же данным"

    private companion object {
        /** Меньше двух недель — статистики нет, есть совпадения. */
        const val MIN_SAMPLE_DAYS = 14
        const val EPSILON = 1e-9
    }
}
