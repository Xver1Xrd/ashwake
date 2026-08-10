package dev.ashwake.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.abs
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
import dev.ashwake.ui.components.AshTabBar
import dev.ashwake.ui.components.NORMAL_MS
import dev.ashwake.ui.components.LocalHazeState
import dev.ashwake.ui.components.TabItem
import dev.ashwake.ui.components.rememberHazeState
import dev.ashwake.ui.habits.HabitsScreen
import dev.ashwake.ui.habits.detail.HabitDetailScreen
import dev.ashwake.ui.habits.editor.HabitEditorScreen
import dev.ashwake.ui.more.MoreScreen
import dev.ashwake.ui.onboarding.OnboardingScreen
import dev.ashwake.ui.ritual.RitualScreen
import dev.ashwake.ui.routines.RoutineRunScreen
import dev.ashwake.ui.settings.SettingsScreen
import dev.ashwake.ui.settings.theme.ThemeEditorScreen
import dev.ashwake.ui.stats.StatsScreen
import dev.ashwake.ui.tasks.TasksScreen
import dev.ashwake.ui.tasks.editor.TaskEditorScreen
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

        val tabIndex = Destination.bottomBar.indexOfFirst { it.route == destinationFor(target) }
        if (tabIndex >= 0) {
            openTab(tabIndex)
        } else {
            navController.navigate(destinationFor(target)) { launchSingleTop = true }
        }
    }

    // Панель вкладок размывает содержимое экрана, а живёт при этом в Scaffold.
    // Состояние размытия общее и раздаётся экранам через CompositionLocal.
    CompositionLocalProvider(LocalHazeState provides haze) {
        Scaffold(
            containerColor = AshTheme.colors.background,
            bottomBar = {
                // На экранах-формах нижняя навигация только мешает — прячем
                val showBottomBar = FULLSCREEN_ROUTE_PREFIXES.none {
                    currentRoute?.startsWith(it) == true
                }
                if (showBottomBar) {
                    AshTabBar(
                        tabs = TABS,
                        // Поверх вкладок открыт другой экран — ни одна вкладка
                        // не активна, и это честно: человек не «внутри» вкладки
                        selectedRoute = if (onTabs) {
                            Destination.bottomBar[pagerState.currentPage].route
                        } else {
                            null
                        },
                        onSelect = { tab ->
                            openTab(TABS.indexOfFirst { it.route == tab.route })
                        }
                    )
                }
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = if (showOnboarding) ONBOARDING_ROUTE else TABS_ROUTE,
                modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
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
                        state = pagerState,
                        // Соседняя страница готовится заранее: иначе переход
                        // пальцем открывает пустоту и достраивается на ходу
                        beyondViewportPageCount = 1
                    ) { page ->
                        // Уезжающая страница отстаёт и мельчает. Без этого
                        // переключение вкладок — подмена картинки; с ним видно,
                        // что страницы лежат рядом, а не поверх друг друга
                        val offset = pagerState.pageOffset(page)
                        TabPage(
                            destination = Destination.bottomBar[page],
                            navController = navController,
                            modifier = Modifier.graphicsLayer {
                                if (reduceMotion) return@graphicsLayer
                                translationX = -offset * size.width * PARALLAX_SHARE
                                val scale = 1f - PARALLAX_SCALE * minOf(abs(offset), 1f)
                                scaleX = scale
                                scaleY = scale
                                alpha = 1f - 0.25f * minOf(abs(offset), 1f)
                            }
                        )
                    }
                }

                composable(
                    route = "task?taskId={taskId}",
                    arguments = listOf(
                        navArgument("taskId") { type = NavType.StringType; defaultValue = "0" }
                    )
                ) {
                    TaskEditorScreen(onDone = { navController.popBackStack() })
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
                        onBack = { navController.popBackStack() },
                        onOpenSettings = { navController.navigate(Destination.Settings.route) }
                    )
                }

                composable(Destination.Timers.route) {
                    TimersScreen(
                        onRunRoutine = { navController.navigate("routine-run") },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable("routine-run") {
                    RoutineRunScreen(onExit = { navController.popBackStack() })
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
                        onOpenThemeEditor = { navController.navigate("theme") }
                    )
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
            onOpenTask = { id -> navController.navigate("task?taskId=$id") },
            onCreateTask = { navController.navigate("task?taskId=0") },
            onOpenCharacter = { navController.navigate(Destination.Character.route) },
            onOpenAbstinence = { id -> navController.navigate("abstinence/$id") }
        )

        Destination.Tasks -> TasksScreen(
            onOpenTask = { id -> navController.navigate("task?taskId=$id") },
            onOpenRitual = { navController.navigate("ritual") },
            onOpenStats = { navController.navigate(Destination.Stats.route) }
        )

        Destination.Habits -> HabitsScreen(
            onOpenHabit = { id -> navController.navigate("habit/$id") },
            onCreateHabit = { navController.navigate("habit-editor?habitId=0") }
        )

        Destination.More -> MoreScreen(
            onOpenAbstinence = { navController.navigate(Destination.Abstinence.route) },
            onOpenCharacter = { navController.navigate(Destination.Character.route) },
            onOpenTimers = { navController.navigate(Destination.Timers.route) },
            onOpenStats = { navController.navigate(Destination.Stats.route) },
            onOpenRitual = { navController.navigate("ritual") },
            onOpenSettings = { navController.navigate(Destination.Settings.route) },
            onOpenTrash = { navController.navigate("trash") }
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
private fun androidx.compose.foundation.pager.PagerState.pageOffset(page: Int): Float =
    (currentPage - page) + currentPageOffsetFraction

/** Доля ширины, на которую отстаёт уезжающая страница. */
private const val PARALLAX_SHARE = 0.25f
private const val PARALLAX_SCALE = 0.06f

/**
 * Переходы между экранами.
 *
 * Открытие — рост с прозрачностью, закрытие — обратный. При выключенном
 * движении длительность нулевая: экран просто оказывается на месте, и это
 * штатный путь, а не поломка.
 */
private fun screenDuration(reduceMotion: Boolean) = if (reduceMotion) 0 else NORMAL_MS

private fun screenEnter(reduceMotion: Boolean) =
    scaleIn(tween(screenDuration(reduceMotion)), initialScale = 0.94f) +
        fadeIn(tween(screenDuration(reduceMotion)))

private fun screenExit(reduceMotion: Boolean) =
    scaleOut(tween(screenDuration(reduceMotion)), targetScale = 1.03f) +
        fadeOut(tween(screenDuration(reduceMotion)))

private fun screenPopEnter(reduceMotion: Boolean) =
    scaleIn(tween(screenDuration(reduceMotion)), initialScale = 1.03f) +
        fadeIn(tween(screenDuration(reduceMotion)))

private fun screenPopExit(reduceMotion: Boolean) =
    scaleOut(tween(screenDuration(reduceMotion)), targetScale = 0.94f) +
        fadeOut(tween(screenDuration(reduceMotion)))

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

/**
 * Экраны, на которых нижняя навигация только мешает.
 *
 * Список намеренно короткий: везде, где панель можно оставить, её оставляют —
 * именно её отсутствие заставляло искать системную кнопку «назад».
 */
private val FULLSCREEN_ROUTE_PREFIXES =
    listOf(
        "task?", "habit-editor?", "routine-run", "ritual",
        "blocking", "backup", "theme", ONBOARDING_ROUTE
    )
