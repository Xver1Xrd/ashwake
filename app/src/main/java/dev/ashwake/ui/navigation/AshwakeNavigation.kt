package dev.ashwake.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import dev.ashwake.ui.components.AshIcons

/**
 * Маршруты приложения. Экраны из п. 17 ТЗ.
 *
 * В панели вкладок ровно четыре пункта, как требует раздел 5 дизайн-системы.
 * Разделы, которые туда не поместились, живут на вкладке «Ещё» обычным
 * сгруппированным списком: семь иконок в панели — это уже не навигация,
 * а полка, по которой каждый раз ищешь глазами.
 */
enum class Destination(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val inBottomBar: Boolean = false
) {
    Today("today", "Сегодня", AshIcons.Sun, inBottomBar = true),
    Tasks("tasks", "Задачи", AshIcons.CheckCircle, inBottomBar = true),
    Habits("habits", "Привычки", AshIcons.Repeat, inBottomBar = true),
    More("more", "Ещё", AshIcons.DotsThree, inBottomBar = true),

    Abstinence("abstinence", "Отказы", AshIcons.Prohibit),
    Character("character", "Персонаж", AshIcons.Person),
    Timers("timers", "Таймеры", AshIcons.Timer),
    Stats("stats", "Статистика", AshIcons.BarChart),
    Settings("settings", "Настройки", AshIcons.Settings);

    companion object {
        val bottomBar: List<Destination> = entries.filter { it.inBottomBar }
    }
}
