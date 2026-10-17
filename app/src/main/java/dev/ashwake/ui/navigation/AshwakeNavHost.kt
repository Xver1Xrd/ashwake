package dev.ashwake.ui.navigation

import dev.ashwake.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.ashwake.platform.widget.AppRoutes
import dev.ashwake.ui.abstinence.AbstinenceScreen
import dev.ashwake.ui.abstinence.detail.AbstinenceDetailScreen
import dev.ashwake.ui.backup.BackupScreen
import dev.ashwake.ui.blocking.BlockingScreen
import dev.ashwake.ui.character.CharacterScreen
import dev.ashwake.ui.components.AshIcons
import dev.ashwake.ui.components.AshTabBar
import androidx.compose.ui.graphics.vector.ImageVector
import dev.ashwake.ui.components.NORMAL_MS
import dev.ashwake.ui.components.LocalHazeState
import dev.ashwake.ui.components.TabItem
import dev.ashwake.ui.components.rememberHazeState
import dev.ashwake.ui.habits.HabitsScreen
import dev.ashwake.ui.habits.detail.HabitDetailScreen
import dev.ashwake.ui.habits.editor.HabitEditorScreen
import dev.ashwake.ui.more.MoreScreen
import dev.ashwake.ui.online.OnlineScreen
import dev.ashwake.ui.onboarding.OnboardingScreen
import dev.ashwake.ui.ritual.RitualScreen
import dev.ashwake.ui.routines.RoutineEditorScreen
import dev.ashwake.ui.routines.RoutineRunScreen
import dev.ashwake.ui.settings.SettingsScreen
import dev.ashwake.ui.settings.theme.ThemeEditorScreen
import dev.ashwake.ui.stats.StatsScreen
import dev.ashwake.ui.tasks.TasksScreen
import dev.ashwake.ui.tasks.editor.TaskEditorScreen
import dev.ashwake.ui.tasks.detail.TaskDetailScreen
import dev.ashwake.ui.tasks.trash.TrashScreen
import dev.ashwake.ui.theme.AshTheme
import dev.ashwake.ui.timers.TimersScreen
import dev.ashwake.ui.today.TodayScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Корень приложения.
 *
 * Четыре вкладки живут не отдельными местами графа, а страницами пейджера
 * на одном маршруте. Так они листаются пальцем и, главное, перестают быть
 * ловушкой: раньше вкладки переключались через `popUpTo(saveState)` с
 * `restoreState`, и открытый поверх вкладки экран сохранялся вместе с ней —
 * нажатие на вкладку восстанавливало стек целиком и возвращало ровно туда,
 * откуда человек пытался уйти.
 *
 * Всё, что открывается поверх вкладок (персонаж, отказы, настройки), кладётся
 * обычным переходом сверху. Нажатие любой вкладки сначала снимает эту стопку,
 * а потом уводит на нужную страницу.
 */
const val TABS_ROUTE = "tabs"

/** Знакомство. Стартовый экран, пока его не прошли. */
const val ONBOARDING_ROUTE = "onboarding"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AshwakeRoot(
    pendingRoute: MutableStateFlow<String?> = MutableStateFlow(null),
    showOnboarding: Boolean = false
) {
    val navController = rememberNavController()
    val route by pendingRoute.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val pagerState = rememberPagerState(
        initialPage = Destination.bottomBar.indexOf(Destination.Today).coerceAtLeast(0),
        pageCount = { Destination.bottomBar.size }
    )

    val reduceMotion = AshTheme.reduceMotion
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val onTabs = currentRoute == null || currentRoute == TABS_ROUTE
    val haze = rememberHazeState()

    /** Уйти на вкладку: снять всё, что открыто поверх, и пролистать пейджер. */
    fun openTab(index: Int) {
        if (!onTabs) navController.popBackStack(TABS_ROUTE, inclusive = false)
        scope.launch { pagerState.animateScrollToPage(index) }
    }

    // Маршрут из виджета, плитки или шортката: открываем нужный экран
    // и сбрасываем, чтобы повторная композиция не повторяла переход
    LaunchedEffect(route) {
        val target = route ?: return@LaunchedEffect
        pendingRoute.value = null

        val dest = destinationFor(target)
        val tabIndex = Destination.bottomBar.indexOfFirst { it.route == dest }
        if (tabIndex >= 0) {
            openTab(tabIndex)
        } else {
            navController.navigate(dest) { launchSingleTop = true }
        }
    }

    // Панель вкладок размывает содержимое экрана, а живёт при этом в Scaffold.
    // Состояние размытия общее и раздаётся экранам через CompositionLocal.
    CompositionLocalProvider(LocalHazeState provides haze) {
        Scaffold(
            containerColor = AshTheme.colors.background,
            bottomBar = {
                // На экранах-формах и при открытой клавиатуре нижняя навигация прячется
                val isImeVisible = WindowInsets.isImeVisible
                val showBottomBar = FULLSCREEN_ROUTE_PREFIXES.none {
                    currentRoute?.startsWith(it) == true
                } && !isImeVisible
                if (showBottomBar) {
                    val tabItems = tabs()
                    AshTabBar(
                        tabs = tabItems,
                        // Поверх вкладок открыт другой экран — ни одна вкладка
                        // не активна, и это честно: человек не «внутри» вкладки
                        selectedRoute = if (onTabs) {
                            Destination.bottomBar[pagerState.currentPage].route
                        } else {
                            null
                        },
                        onSelect = { tab ->
                            openTab(tabItems.indexOfFirst { it.route == tab.route })
                        }
                    )
                }
            }
        ) { _ ->
            // Контент идёт до нижнего края, а остров лежит поверх него.
            // Иначе Scaffold резервирует под меню прямоугольную полосу,
            // которую видно за капсулой на экранах с градиентом.
            NavHost(
                navController = navController,
                startDestination = if (showOnboarding) ONBOARDING_ROUTE else TABS_ROUTE,
                modifier = Modifier,
                // Экран поверх вкладок вырастает из содержимого и слегка
                // отодвигает то, что под ним: видно, что он лёг сверху,
                // а не заменил собой всё. Стандартный сдвиг сбоку тут врёт —
                // сбоку в приложении лежат соседние вкладки
                enterTransition = { screenEnter(reduceMotion) },
                exitTransition = { screenExit(reduceMotion) },
                popEnterTransition = { screenPopEnter(reduceMotion) },
                popExitTransition = { screenPopExit(reduceMotion) }
            ) {
                composable(ONBOARDING_ROUTE) {
                    OnboardingScreen(
                        onDone = {
                            // Знакомство не должно оставаться в истории:
                            // «назад» с главного экрана обязан закрывать
                            // приложение, а не возвращать в приветствие
                            navController.navigate(TABS_ROUTE) {
                                popUpTo(ONBOARDING_ROUTE) { inclusive = true }
                            }
                        }
                    )
                }

                composable(TABS_ROUTE) {
                    HorizontalPager(
                        // Не держим соседние тяжёлые экраны в композиции:
                        // это заметно улучшает плавность на телефонах.
                        state = pagerState
                    ) { page ->
                        TabPage(
                            destination = Destination.bottomBar[page],
                            navController = navController
                        )
                    }
                }

                composable(
                    route = "task/{taskId}",
                    arguments = listOf(
                        navArgument("taskId") { type = NavType.StringType }
                    )
                ) { entry ->
                    val taskId = entry.arguments?.getString("taskId") ?: return@composable
                    TaskDetailScreen(
                        onBack = { navController.popBackStack() },
                        onEdit = {
                            navController.navigate("task-edit?taskId=$taskId")
                        }
                    )
                }

                composable(
                    route = "task-edit?taskId={taskId}",
                    arguments = listOf(
                        navArgument("taskId") { type = NavType.StringType; defaultValue = "0" }
                    )
                ) { TaskEditorScreen(onDone = { navController.popBackStack() }) }

                composable(
                    route = "task-editor?taskId={taskId}",
                    arguments = listOf(navArgument("taskId") { type = NavType.StringType; defaultValue = "0" })
                ) { TaskEditorScreen(onDone = { navController.popBackStack() }) }

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
                        onBack = { navController.popBackStack() },
                        onOpenSettings = { navController.navigate(Destination.Settings.route) }
                    )
                }

                composable(Destination.Timers.route) {
                    TimersScreen(
                        onRunRoutine = { navController.navigate("routine-run") },
                        onEditRoutine = { id ->
                            navController.navigate("routine-editor?routineId=$id")
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable("routine-run") {
                    RoutineRunScreen(onExit = { navController.popBackStack() })
                }

                composable(
                    route = "routine-editor?routineId={routineId}",
                    arguments = listOf(
                        navArgument("routineId") { type = NavType.StringType; defaultValue = "0" }
                    )
                ) {
                    RoutineEditorScreen(onDone = { navController.popBackStack() })
                }

                composable(Destination.Stats.route) {
                    StatsScreen(onBack = { navController.popBackStack() })
                }

                composable("ritual") {
                    RitualScreen(onDone = { navController.popBackStack() })
                }

                composable(Destination.Settings.route) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenBlocking = { navController.navigate("blocking") },
                        onOpenBackup = { navController.navigate("backup") },
                        onOpenOnline = { navController.navigate("online") },
                        onOpenThemeEditor = { navController.navigate("theme") }
                    )
                }

                composable("online") {
                    OnlineScreen(onBack = { navController.popBackStack() })
                }

                composable("theme") {
                    ThemeEditorScreen(onBack = { navController.popBackStack() })
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

/**
 * Содержимое одной вкладки.
 *
 * Вынесено из графа: страницы пейджера — это не маршруты, а именно
 * страницы, и переход между ними не должен попадать в историю переходов.
 */
@Composable
private fun TabPage(
    destination: Destination,
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
    when (destination) {
        Destination.Today -> TodayScreen(
            onOpenHabit = { id -> navController.navigate("habit/$id") },
            onOpenTask = { id -> navController.navigate("task/$id") },
            onCreateTask = { navController.navigate("task-edit?taskId=0") },
            onOpenAbstinence = { id -> navController.navigate("abstinence/$id") }
        )

        Destination.Tasks -> TasksScreen(
            onOpenTask = { id -> navController.navigate("task/$id") },
            onOpenRitual = { navController.navigate("ritual") },
            onOpenStats = { navController.navigate(Destination.Stats.route) }
        )

        Destination.Habits -> HabitsScreen(
            onOpenHabit = { id -> navController.navigate("habit/$id") },
            onCreateHabit = { navController.navigate("habit-editor?habitId=0") }
        )

        Destination.More -> MoreScreen(
            onOpenAbstinence = { navController.navigate(Destination.Abstinence.route) },
            onOpenTimers = { navController.navigate(Destination.Timers.route) },
            onOpenStats = { navController.navigate(Destination.Stats.route) },
            onOpenRitual = { navController.navigate("ritual") },
            onOpenSettings = { navController.navigate(Destination.Settings.route) },
            onOpenTrash = { navController.navigate("trash") },
            onOpenTask = { id -> navController.navigate("task/$id") }
        )

        else -> Unit
    }
    }
}

/**
 * Насколько страница смещена относительно текущей: 0 — по центру,
 * ±1 — соседняя. Считается вручную, потому что `currentPageOffsetFraction`
 * знает только про текущую страницу.
 */
/**
 * Переходы между экранами.
 *
 * Открытие — рост с прозрачностью, закрытие — обратный. При выключенном
 * движении длительность нулевая: экран просто оказывается на месте, и это
 * штатный путь, а не поломка.
 */
private fun screenDuration(reduceMotion: Boolean) = if (reduceMotion) 0 else NORMAL_MS

private fun screenEnter(reduceMotion: Boolean) =
    scaleIn(tween(screenDuration(reduceMotion)), initialScale = 0.97f) +
        fadeIn(tween(screenDuration(reduceMotion)))

private fun screenExit(reduceMotion: Boolean) =
    scaleOut(tween(screenDuration(reduceMotion)), targetScale = 1.01f) +
        fadeOut(tween(screenDuration(reduceMotion)))

private fun screenPopEnter(reduceMotion: Boolean) =
    scaleIn(tween(screenDuration(reduceMotion)), initialScale = 1.025f) +
        fadeIn(tween(screenDuration(reduceMotion)))

private fun screenPopExit(reduceMotion: Boolean) =
    scaleOut(tween(screenDuration(reduceMotion)), targetScale = 0.97f) +
        fadeOut(tween(screenDuration(reduceMotion)))

/**
 * Динамическая иконка первой вкладки («Сегодня») в зависимости от времени суток:
 * - ночью: полумесяц (Moon)
 * - утром: восход солнца (Sunrise)
 * - днем: солнце (Sun)
 * - вечером: заход солнца (Sunset)
 */
fun todayAtmosphereIcon(hour: Int = java.time.LocalTime.now().hour): ImageVector = when (hour) {
    in 5..8 -> AshIcons.Sunrise
    in 9..17 -> AshIcons.Sun
    in 18..21 -> AshIcons.Sunset
    else -> AshIcons.Moon
}

/**
 * Вкладки нижней панели.
 *
 * Собираются в композиции, а не в статическом списке: подписи теперь живут
 * в ресурсах, а `stringResource` без композиции не прочитать.
 */
@Composable
private fun tabs(): List<TabItem> = Destination.bottomBar.map {
    TabItem(route = it.route, title = stringResource(it.titleRes), icon = it.icon)
}

/** Маршрут из виджета в экран приложения. */
private fun destinationFor(route: String): String = when (route) {
    AppRoutes.TASKS -> Destination.Tasks.route
    AppRoutes.NEW_TASK -> "task-edit?taskId=0"
    AppRoutes.HABITS -> Destination.Habits.route
    AppRoutes.ABSTINENCE -> Destination.Abstinence.route
    AppRoutes.TIMERS, AppRoutes.FOCUS_START, AppRoutes.ROUTINE_START -> Destination.Timers.route
    AppRoutes.RITUAL -> "ritual"
    else -> route
}

/**
 * Экраны, на которых нижняя навигация только мешает.
 *
 * Список намеренно короткий: везде, где панель можно оставить, её оставляют —
 * именно её отсутствие заставляло искать системную кнопку «назад».
 */
private val FULLSCREEN_ROUTE_PREFIXES =
    listOf(
        "task/", "task-edit?", "habit-editor?", "routine-run", "routine-editor?", "ritual",
        "blocking", "backup", "theme", ONBOARDING_ROUTE
    )
