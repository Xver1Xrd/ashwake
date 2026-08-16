package dev.ashwake.core.ext

import dev.ashwake.core.time.AppClock
import java.time.LocalDate
import java.time.LocalTime

/** epochDay ↔ LocalDate: короткая форма для ключей-дат в БД и сундуке. */
fun LocalDate.toEpochDayInt(): Int = toEpochDay().toInt()
fun Int.toLocalDate(): LocalDate = LocalDate.ofEpochDay(toLong())

/** «Сейчас» локальным временем — один вызов вместо пары (now + zone). */
fun AppClock.nowLocalTime(): LocalTime = now().atZone(zone()).toLocalTime()

/** Текст или прочерк: для таблиц и CSV, где пустая ячейка читается как «нет». */
fun String?.orDash(): String = this?.takeIf { it.isNotBlank() } ?: "—"

/** «да/нет» для boolean: короткий русский вывод. */
fun Boolean?.toYesNo(): String = when (this) {
    true -> "да"
    false -> "нет"
    null -> "—"
}
