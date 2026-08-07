package dev.ashwake.core.model

import java.time.DayOfWeek

/** Приоритет задачи. P1 — самый высокий. */
enum class Priority { P1, P2, P3, P4 }

/** Сфера привычки. Используется характеристиками персонажа (п. 16.5.1). */
enum class Sphere { HEALTH, SPORT, STUDY, CHORES, MENTAL }

/** Характеристики персонажа. Растут от поведения, не за монеты. */
enum class Stat { STRENGTH, AGILITY, ENDURANCE, INTELLECT, WILL, LUCK }

/**
 * Битовая маска дней недели: понедельник — бит 0.
 * Хранится в БД одним Int, чтобы не заводить таблицу на каждое расписание.
 */
@JvmInline
value class WeekdayMask(val bits: Int) {

    operator fun contains(day: DayOfWeek): Boolean =
        bits and (1 shl (day.value - 1)) != 0

    fun with(day: DayOfWeek): WeekdayMask =
        WeekdayMask(bits or (1 shl (day.value - 1)))

    fun without(day: DayOfWeek): WeekdayMask =
        WeekdayMask(bits and (1 shl (day.value - 1)).inv())

    fun toggle(day: DayOfWeek): WeekdayMask =
        if (contains(day)) without(day) else with(day)

    val isEmpty: Boolean get() = bits == 0
    val count: Int get() = Integer.bitCount(bits)

    fun days(): List<DayOfWeek> = DayOfWeek.entries.filter { contains(it) }

    companion object {
        val NONE = WeekdayMask(0)
        val EVERY_DAY = WeekdayMask(0b1111111)
        val WEEKDAYS = WeekdayMask(0b0011111)
        val WEEKEND = WeekdayMask(0b1100000)

        fun of(vararg days: DayOfWeek): WeekdayMask =
            days.fold(NONE) { mask, day -> mask.with(day) }
    }
}
