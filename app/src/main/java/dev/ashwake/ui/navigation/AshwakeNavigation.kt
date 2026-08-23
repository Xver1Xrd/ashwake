package dev.ashwake.ui.navigation

import dev.ashwake.R
import androidx.annotation.StringRes
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
    @StringRes val titleRes: Int,
    val icon: ImageVector,
    val inBottomBar: Boolean = false
) {
    Today("today", R.string.nav_today, AshIcons.Sun, inBottomBar = true),
    Tasks("tasks", R.string.nav_tasks, AshIcons.CheckCircle, inBottomBar = true),
    Habits("habits", R.string.nav_habits, AshIcons.Repeat, inBottomBar = true),
    More("more", R.string.nav_more, AshIcons.DotsThree, inBottomBar = true),

    Abstinence("abstinence", R.string.nav_abstinence, AshIcons.Prohibit),
    Character("character", R.string.nav_character, AshIcons.Person),
    Timers("timers", R.string.nav_timers, AshIcons.Timer),
    Stats("stats", R.string.nav_stats, AshIcons.BarChart),
    Settings("settings", R.string.nav_settings, AshIcons.Settings);

    companion object {
        val bottomBar: List<Destination> = entries.filter { it.inBottomBar }
    }
}
