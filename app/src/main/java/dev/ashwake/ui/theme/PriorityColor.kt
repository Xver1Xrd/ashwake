package dev.ashwake.ui.theme

import androidx.compose.ui.graphics.Color
import dev.ashwake.core.model.Priority

/**
 * Приоритет в интерфейсе — это цвет, а не номер.
 *
 * Хранится он по-прежнему как P1..P4: имена enum лежат в базе строками и
 * ими же сортируются, а импорт из Todoist и Tick-Tick мапится на те же
 * четыре ступени. Но человеку «P2» ничего не говорит, поэтому наружу
 * приоритет выходит цветом и словом: красный — срочно, оранжевый — важно,
 * зелёный — обычная, серый — когда-нибудь.
 *
 * Слово рядом с цветом обязательно: ни одно состояние в приложении не
 * передаётся только цветом.
 */

/** Название цвета метки. Оно же подпись кнопки в редакторе. */
val Priority.colorTitle: String
    get() = when (this) {
        Priority.P1 -> "Красный"
        Priority.P2 -> "Оранжевый"
        Priority.P3 -> "Зелёный"
        Priority.P4 -> "Без метки"
    }

/** Что метка означает. Короткое слово для строки списка и подсказки. */
val Priority.meaning: String
    get() = when (this) {
        Priority.P1 -> "срочно"
        Priority.P2 -> "важно"
        Priority.P3 -> "обычная"
        Priority.P4 -> "потом"
    }

/** Показывать ли метку в списке. У «без метки» точки нет. */
val Priority.hasMark: Boolean
    get() = this != Priority.P4

fun AshColors.priorityColor(priority: Priority): Color = when (priority) {
    Priority.P1 -> danger
    Priority.P2 -> warm
    Priority.P3 -> success
    Priority.P4 -> text3
}

/** Порядок совпадает с [Priority.entries]: индексируется ординалом. */
val AshColors.priorityColors: List<Color>
    get() = Priority.entries.map { priorityColor(it) }

/**
 * Те же цвета константами — для виджетов Glance и экранов, ещё не
 * переписанных на токены. Значения тёмной темы, как и остальные псевдонимы
 * в [Color.kt].
 */
val PriorityColors: List<Color> = listOf(
    Color(0xFFFF6B6B),
    Color(0xFFFFB84D),
    Color(0xFF3FDC9A),
    Color(0x59E4E4F2)
)
