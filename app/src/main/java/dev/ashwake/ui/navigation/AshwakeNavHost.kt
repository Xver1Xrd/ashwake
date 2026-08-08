package dev.ashwake.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.ashwake.ui.abstinence.AbstinenceScreen
import dev.ashwake.ui.character.CharacterScreen
import dev.ashwake.ui.routines.RoutineRunScreen
import dev.ashwake.ui.timers.TimersScreen
import dev.ashwake.ui.abstinence.detail.AbstinenceDetailScreen
import dev.ashwake.ui.habits.HabitsScreen
import dev.ashwake.ui.habits.detail.HabitDetailScreen
import dev.ashwake.ui.habits.editor.HabitEditorScreen
import dev.ashwake.ui.tasks.TasksScreen
import dev.ashwake.ui.tasks.editor.TaskEditorScreen

@Composable
fun AshwakeRoot() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            // На экране редактора нижняя навигация только мешает — прячем
            val showBottomBar = FULLSCREEN_ROUTE_PREFIXES.none {
                currentRoute?.startsWith(it) == true
            }
            if (showBottomBar) NavigationBar {
                Destination.bottomBar.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.title) },
                        label = { Text(destination.title) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Tasks.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Destination.Tasks.route) {
                TasksScreen(onOpenTask = { id -> navController.navigate("task?taskId=$id") })
            }

            composable(
                route = "task?taskId={taskId}",
                arguments = listOf(
                    navArgument("taskId") { type = NavType.StringType; defaultValue = "0" }
                )
            ) {
                TaskEditorScreen(onDone = { navController.popBackStack() })
            }

            // Экраны следующих этапов: заглушки, чтобы навигация была целой с самого начала
            composable(Destination.Home.route) { StageStub("Главный экран", 4) }
            composable(Destination.Today.route) { StageStub("Сегодня", 2) }
            composable(Destination.Habits.route) {
                HabitsScreen(
                    onOpenHabit = { id -> navController.navigate("habit/$id") },
                    onCreateHabit = { navController.navigate("habit-editor?habitId=0") }
                )
            }

            composable(
                route = "habit/{habitId}",
                arguments = listOf(navArgument("habitId") { type = NavType.StringType })
            ) {
                HabitDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate("habit-editor?habitId=$id") }
                )
            }

            composable(
                route = "habit-editor?habitId={habitId}",
                arguments = listOf(
                    navArgument("habitId") { type = NavType.StringType; defaultValue = "0" }
                )
            ) {
                HabitEditorScreen(onDone = { navController.popBackStack() })
            }
            composable(Destination.Abstinence.route) {
                AbstinenceScreen(
                    onOpen = { id -> navController.navigate("abstinence/$id") }
                )
            }

            composable(
                route = "abstinence/{abstinenceId}",
                arguments = listOf(navArgument("abstinenceId") { type = NavType.StringType })
            ) {
                AbstinenceDetailScreen(onBack = { navController.popBackStack() })
            }
            composable(Destination.Character.route) { CharacterScreen() }

            composable(Destination.Timers.route) {
                TimersScreen(onRunRoutine = { navController.navigate("routine-run") })
            }
            composable("routine-run") {
                RoutineRunScreen(onExit = { navController.popBackStack() })
            }
            composable(Destination.Stats.route) { StageStub("Статистика", 7) }
            composable(Destination.Settings.route) { StageStub("Настройки", 0) }
        }
    }
}

/** Экраны, на которых нижняя навигация только мешает. */
private val FULLSCREEN_ROUTE_PREFIXES =
    listOf("task?", "habit/", "habit-editor?", "abstinence/", "routine-run")

/** Честная заглушка: показывает, на каком этапе плана появится экран. */
@Composable
private fun StageStub(title: String, stage: Int) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(
            "Этап $stage по docs/03-plan.md",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
