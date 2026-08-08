package dev.ashwake.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.ui.graphics.vector.ImageVector

/** Маршруты приложения. Экраны из п. 17 ТЗ. */
enum class Destination(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val inBottomBar: Boolean = false
) {
    Home("home", "Главный", Icons.Filled.Home, inBottomBar = true),
    Today("today", "Сегодня", Icons.Filled.Today),
    Tasks("tasks", "Задачи", Icons.Filled.CheckCircle, inBottomBar = true),
    Habits("habits", "Привычки", Icons.Filled.Repeat, inBottomBar = true),
    Abstinence("abstinence", "Отказы", Icons.Filled.Block, inBottomBar = true),
    Character("character", "Персонаж", Icons.Filled.Person, inBottomBar = true),
    Stats("stats", "Статистика", Icons.Filled.BarChart),
    Settings("settings", "Настройки", Icons.Filled.Settings);

    companion object {
        val bottomBar: List<Destination> = entries.filter { it.inBottomBar }
    }
}
