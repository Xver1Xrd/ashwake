package dev.ashwake.domain.engine.character

import dev.ashwake.core.model.Sphere
import dev.ashwake.core.model.Stat
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.floor
import kotlin.math.sqrt

/** Источник очков характеристики — из него видно, за что именно она выросла. */
enum class StatSource {
    HABIT_DONE, HABIT_MINIMUM, ROUTINE_DONE, TASK_DONE, TASK_ON_TIME,
    FOCUS_SESSION, ABSTINENCE_DAY, CRAVING_RESISTED, STREAK_DAY,
    STALE_TASK_CLOSED, RITUAL_DONE
}

/**
 * Характеристики персонажа (п. 16.5.1).
 *
 * Растут не за монеты, а от реального поведения — это принципиально:
 * купить силу нельзя, её надо натренировать.
 *
 * `stat = floor(sqrt(накопленные_очки / 4))` — быстрый рост в начале,
 * медленный на высоких значениях.
 */
@Singleton
class StatProgressCalculator @Inject constructor() {

    fun valueOf(points: Long): Int =
        floor(sqrt(points.coerceAtLeast(0L).toDouble() / POINTS_PER_UNIT)).toInt()

    /** Сколько очков нужно, чтобы достичь значения. Показывается прогрессом на экране. */
    fun pointsForValue(value: Int): Long =
        value.toLong() * value.toLong() * POINTS_PER_UNIT

    /** Доля пути до следующего значения характеристики. */
    fun progressToNext(points: Long): Float {
        val current = valueOf(points)
        val from = pointsForValue(current)
        val to = pointsForValue(current + 1)
        if (to <= from) return 0f
        return ((points - from).toFloat() / (to - from)).coerceIn(0f, 1f)
    }

    /**
     * Какие характеристики растут от события и на сколько очков.
     *
     * Соответствие сфер и характеристик — из таблицы п. 16.5.1. Одно событие
     * может кормить две характеристики: день отказа даёт и Волю, и Выносливость.
     */
    fun pointsFor(source: StatSource, sphere: Sphere? = null): Map<Stat, Int> = when (source) {
        StatSource.HABIT_DONE -> sphereStat(sphere)?.let { mapOf(it to HABIT_POINTS) }.orEmpty()
        StatSource.HABIT_MINIMUM ->
            // Минимум в плохой день — это про волю, а не про сферу привычки
            mapOf(Stat.WILL to MINIMUM_POINTS) +
                (sphereStat(sphere)?.let { mapOf(it to MINIMUM_POINTS) }.orEmpty())

        StatSource.ROUTINE_DONE -> mapOf(Stat.STRENGTH to ROUTINE_POINTS)
        StatSource.TASK_DONE -> mapOf(Stat.AGILITY to TASK_POINTS)
        StatSource.TASK_ON_TIME -> mapOf(Stat.AGILITY to TASK_ON_TIME_POINTS)
        StatSource.FOCUS_SESSION -> mapOf(Stat.INTELLECT to FOCUS_POINTS)
        StatSource.ABSTINENCE_DAY ->
            mapOf(Stat.WILL to ABSTINENCE_POINTS, Stat.ENDURANCE to ABSTINENCE_POINTS)
        StatSource.CRAVING_RESISTED -> mapOf(Stat.WILL to CRAVING_POINTS)
        StatSource.STREAK_DAY -> mapOf(Stat.ENDURANCE to STREAK_POINTS)
        StatSource.STALE_TASK_CLOSED -> mapOf(Stat.LUCK to LUCK_POINTS)
        StatSource.RITUAL_DONE -> mapOf(Stat.LUCK to RITUAL_POINTS)
    }

    /** Сфера привычки → характеристика, которую она качает. */
    fun sphereStat(sphere: Sphere?): Stat? = when (sphere) {
        Sphere.SPORT -> Stat.STRENGTH
        Sphere.STUDY -> Stat.INTELLECT
        Sphere.MENTAL -> Stat.WILL
        Sphere.HEALTH -> Stat.ENDURANCE
        Sphere.CHORES -> Stat.AGILITY
        null -> null
    }

    private companion object {
        /** Делитель формулы: 4 очка на первую единицу характеристики. */
        const val POINTS_PER_UNIT = 4L

        const val HABIT_POINTS = 2
        const val MINIMUM_POINTS = 1
        const val ROUTINE_POINTS = 3
        const val TASK_POINTS = 1
        const val TASK_ON_TIME_POINTS = 2
        const val FOCUS_POINTS = 3
        const val ABSTINENCE_POINTS = 2
        const val CRAVING_POINTS = 3
        const val STREAK_POINTS = 1
        const val LUCK_POINTS = 3
        const val RITUAL_POINTS = 2
    }
}
