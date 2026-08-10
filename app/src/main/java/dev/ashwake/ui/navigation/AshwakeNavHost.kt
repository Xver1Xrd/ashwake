package dev.ashwake.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ashwake.platform.widget.AppRoutes
import dev.ashwake.ui.components.AshTabBar
import dev.ashwake.ui.components.LocalHazeState
import dev.ashwake.ui.components.TabItem
import dev.ashwake.ui.components.rememberHazeState
import dev.ashwake.ui.more.MoreScreen
import dev.ashwake.ui.theme.AshTheme
import dev.ashwake.ui.today.TodayScreen
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.ashwake.ui.abstinence.AbstinenceScreen
import dev.ashwake.ui.backup.BackupScreen
import dev.ashwake.ui.blocking.BlockingScreen
import dev.ashwake.ui.character.CharacterScreen
import dev.ashwake.ui.ritual.RitualScreen
import dev.ashwake.ui.settings.SettingsScreen
import dev.ashwake.ui.routines.RoutineRunScreen
import dev.ashwake.ui.stats.StatsScreen
import dev.ashwake.ui.timers.TimersScreen
import dev.ashwake.ui.abstinence.detail.AbstinenceDetailScreen
import dev.ashwake.ui.habits.HabitsScreen
import dev.ashwake.ui.habits.detail.HabitDetailScreen
import dev.ashwake.ui.habits.editor.HabitEditorScreen
import dev.ashwake.ui.tasks.TasksScreen
import dev.ashwake.ui.tasks.editor.TaskEditorScreen
import dev.ashwake.ui.tasks.trash.TrashScreen

@Composable
fun AshwakeRoot(pendingRoute: MutableStateFlow<String?> = MutableStateFlow(null)) {
    val navController = rememberNavController()
    val route by pendingRoute.collectAsStateWithLifecycle()

    // Маршрут из виджета, плитки или шортката: открываем нужный экран
    // и сбрасываем, чтобы повторная композиция не повторяла переход
    LaunchedEffect(route) {
        val target = route ?: return@LaunchedEffect
        navController.navigate(destinationFor(target)) { launchSingleTop = true }
        pendingRoute.value = null
    }

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val haze = rememberHazeState()

    // Панель вкладок размывает содержимое экрана, а живёт при этом в Scaffold.
    // Состояние размытия общее и раздаётся экранам через CompositionLocal.
    CompositionLocalProvider(LocalHazeState provides haze) {
        Scaffold(
            containerColor = AshTheme.colors.background,
            bottomBar = {
                // На экране редактора нижняя навигация только мешает — прячем
                val showBottomBar = FULLSCREEN_ROUTE_PREFIXES.none {
                    currentRoute?.startsWith(it) == true
                }
                if (showBottomBar) {
                    AshTabBar(
                        tabs = TABS,
                        selectedRoute = currentRoute,
                        onSelect = { tab ->
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Destination.Today.route,
                modifier = Modifier.padding(bottom = padding.calculateBottomPadding())
            ) {
            composable(Destination.Tasks.route) {
                TasksScreen(
                    onOpenTask = { id -> navController.navigate("task?taskId=$id") },
                    onOpenRitual = { navController.navigate("ritual") },
                    onOpenStats = { navController.navigate(Destination.Stats.route) }
                )
            }

            composable(
                route = "task?taskId={taskId}",
                arguments = listOf(
                    navArgument("taskId") { type = NavType.StringType; defaultValue = "0" }
                )
            ) {
                TaskEditorScreen(onDone = { navController.popBackStack() })
            }

            composable(Destination.Today.route) {
                TodayScreen(
                    onOpenHabit = { id -> navController.navigate("habit/$id") },
                    onOpenTask = { id -> navController.navigate("task?taskId=$id") },
                    onCreateTask = { navController.navigate("task?taskId=0") },
                    onOpenCharacter = { navController.navigate(Destination.Character.route) },
                    onOpenAbstinence = { id -> navController.navigate("abstinence/$id") }
                )
            }

            composable(Destination.More.route) {
                MoreScreen(
                    onOpenAbstinence = { navController.navigate(Destination.Abstinence.route) },
                    onOpenCharacter = { navController.navigate(Destination.Character.route) },
                    onOpenTimers = { navController.navigate(Destination.Timers.route) },
                    onOpenStats = { navController.navigate(Destination.Stats.route) },
                    onOpenRitual = { navController.navigate("ritual") },
                    onOpenSettings = { navController.navigate(Destination.Settings.route) },
                    onOpenTrash = { navController.navigate("trash") }
                )
            }

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
            composable(Destination.Character.route) {
                CharacterScreen(
                    onOpenSettings = { navController.navigate(Destination.Settings.route) }
                )
            }

            composable(Destination.Timers.route) {
                TimersScreen(onRunRoutine = { navController.navigate("routine-run") })
            }
            composable("routine-run") {
                RoutineRunScreen(onExit = { navController.popBackStack() })
            }
            composable(Destination.Stats.route) { StatsScreen() }

            composable("ritual") {
                RitualScreen(onDone = { navController.popBackStack() })
            }
            composable(Destination.Settings.route) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenBlocking = { navController.navigate("blocking") },
                    onOpenBackup = { navController.navigate("backup") }
                )
            }

            composable("blocking") {
                BlockingScreen(onBack = { navController.popBackStack() })
            }

                composable("trash") {
                    TrashScreen(onBack = { navController.popBackStack() })
                }

                composable("backup") {
                    BackupScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

/** Вкладки нижней панели. */
private val TABS: List<TabItem> = Destination.bottomBar.map {
    TabItem(route = it.route, title = it.title, icon = it.icon)
}

/** Маршрут из виджета в экран приложения. */
private fun destinationFor(route: String): String = when (route) {
    AppRoutes.NEW_TASK -> "task?taskId=0"
    AppRoutes.HABITS -> Destination.Habits.route
    AppRoutes.ABSTINENCE -> Destination.Abstinence.route
    AppRoutes.TIMERS, AppRoutes.FOCUS_START, AppRoutes.ROUTINE_START -> Destination.Timers.route
    AppRoutes.RITUAL -> "ritual"
    else -> Destination.Today.route
}

/** Экраны, на которых нижняя навигация только мешает. */
private val FULLSCREEN_ROUTE_PREFIXES =
    listOf("task?", "habit/", "habit-editor?", "abstinence/", "routine-run", "ritual", "blocking", "settings", "backup", "trash")
