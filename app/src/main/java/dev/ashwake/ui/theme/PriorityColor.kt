package dev.ashwake.ui.theme

import dev.ashwake.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
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
    @Composable get() = when (this) {
        Priority.P1 -> stringResource(R.string.theme_krasnyy)
        Priority.P2 -> stringResource(R.string.theme_oranzhevyy)
        Priority.P3 -> stringResource(R.string.theme_zelenyy)
        Priority.P4 -> stringResource(R.string.theme_bez_metki)
    }

/** Что метка означает. Короткое слово для строки списка и подсказки. */
val Priority.meaning: String
    @Composable get() = when (this) {
        Priority.P1 -> stringResource(R.string.theme_srochno)
        Priority.P2 -> stringResource(R.string.theme_vazhno)
        Priority.P3 -> stringResource(R.string.theme_obychnaya)
        Priority.P4 -> stringResource(R.string.theme_potom)
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
