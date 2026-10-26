package dev.ashwake.ui.today

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ashwake.R
import androidx.compose.ui.draw.clip
import dev.ashwake.ui.components.parallaxTilt
import dev.ashwake.ui.components.ActivityFlame
import dev.ashwake.ui.components.AshIcons
import dev.ashwake.ui.components.EmptyState
import dev.ashwake.ui.components.IconAction
import dev.ashwake.ui.components.ListDivider
import dev.ashwake.ui.components.ListGroup
import dev.ashwake.ui.components.NORMAL_MS
import dev.ashwake.ui.components.ScreenPadding
import dev.ashwake.ui.components.SkeletonList
import dev.ashwake.ui.components.ToastHost
import dev.ashwake.ui.components.appHazeSource
import dev.ashwake.ui.components.rememberToastState
import dev.ashwake.ui.theme.AshShapes
import dev.ashwake.ui.theme.AshTheme
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Экран «Сегодня» — первая и главная вкладка.
 * Чистый, продуктивный фокус на задачах, привычках и прогрессе дня.
 */
@Composable
fun TodayScreen(
    isActive: Boolean,
    onOpenHabit: (Long) -> Unit,
    onOpenTask: (Long) -> Unit,
    onCreateTask: () -> Unit,
    onOpenAbstinence: (Long) -> Unit,
    viewModel: TodayViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AshTheme.colors
    val toast = rememberToastState()
    val haptics = dev.ashwake.ui.theme.rememberHaptics()
    var counterDialogTarget by remember { mutableStateOf<dev.ashwake.domain.model.habits.HabitWithProgress?>(null) }
    val postponedText = stringResource(R.string.toast_postponed)
    val undoText = stringResource(R.string.toast_undo)

    LaunchedEffect(isActive) { viewModel.setScreenActive(isActive) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .appHazeSource(),
            // Нижний остров лежит поверх контента. Этот запас не даёт ему
            // перекрывать последнюю карточку, в частности «Отказы».
            contentPadding = PaddingValues(bottom = 116.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            item {
                Row(
                    Modifier
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .padding(start = ScreenPadding, end = ScreenPadding, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.calendar_segodnya),
                            style = if (
                                androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 360 ||
                                androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
                            ) AshTheme.type.headline else AshTheme.type.largeTitle,
                            color = colors.text
                        )
                        Text(
                            text = state.today.format(DateFormat)
                                .replaceFirstChar { it.titlecase(Locale.getDefault()) },
                            style = AshTheme.type.subhead,
                            color = colors.text2
                        )
                    }
                ActivityFlame(
                    level = state.flameLevel,
                    animate = isActive,
                    modifier = Modifier.padding(end = 6.dp)
                )
                IconAction(
                    icon = AshIcons.Add,
                    contentDescription = stringResource(R.string.today_novaya_zadacha),
                    onClick = onCreateTask
                )
            }
        }

        item {
            DateStrip(
                today = state.today,
                selectedDate = state.selectedDate,
                onSelectDate = viewModel::selectDate
            )
        }

        if (!state.isSelectedToday) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenPadding),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Просмотр даты: ${state.selectedDate.format(DateFormat)}",
                        style = AshTheme.type.footnote,
                        color = colors.text2
                    )
                    dev.ashwake.ui.components.TextAction(
                        text = "Вернуться к сегодня",
                        onClick = viewModel::goToToday
                    )
                }
            }
        }

        item {
            TodaySummaryCard(
                state = state,
                modifier = Modifier.padding(horizontal = ScreenPadding)
            )
        }

            if (state.loading) {
                item { SkeletonList(count = 4) }
            }

            if (!state.loading && state.isEmpty) {
                item {
                    EmptyState(
                        icon = AshIcons.Sun,
                        title = stringResource(R.string.today_na_segodnya_pusto),
                        description = stringResource(
                            R.string.today_zavedite_privychku_ili_zadachu_oni_poyavyats
                        ),
                        actionText = stringResource(R.string.today_sozdat_zadachu),
                        onAction = onCreateTask
                    )
                }
            }

            // Просрочка
            if (state.overdueTasks.isNotEmpty()) {
                item {
                    AppearingGroup(order = 0) {
                        ListGroup(
                            items = state.overdueTasks,
                            header = stringResource(R.string.today_prosrocheno),
                            dividerInset = 62.dp
                        ) { task ->
                            TaskTodayRow(
                                task = task,
                                today = state.today,
                                onToggle = { _ ->
                                    if (!task.isDone) {
                                        haptics.play(dev.ashwake.ui.theme.HapticKind.TASK_COMPLETE)
                                    }
                                    viewModel.toggleTask(task)
                                },
                                onPostpone = {
                                    viewModel.postponeTask(task)
                                    toast.show(postponedText, undoText) {
                                        viewModel.undoPostpone(task.id)
                                    }
                                },
                                onOpen = { onOpenTask(task.id) }
                            )
                        }
                    }
                }
            }

            // Задачи на сегодня
            if (state.todayTasks.isNotEmpty()) {
                item {
                    AppearingGroup(order = 1) {
                        ListGroup(
                            items = state.todayTasks,
                            header = stringResource(R.string.today_zadachi_1_s_iz_2_s, state.todayTasks.count { it.isDone }, state.todayTasks.size),
                            dividerInset = 62.dp
                        ) { task ->
                            TaskTodayRow(
                                task = task,
                                today = state.today,
                                onToggle = { _ ->
                                    if (!task.isDone) {
                                        haptics.play(dev.ashwake.ui.theme.HapticKind.TASK_COMPLETE)
                                    }
                                    viewModel.toggleTask(task)
                                },
                                onPostpone = {
                                    viewModel.postponeTask(task)
                                    toast.show(postponedText, undoText) {
                                        viewModel.undoPostpone(task.id)
                                    }
                                },
                                onOpen = { onOpenTask(task.id) },
                                onDelete = { viewModel.deleteTask(task.id) }
                            )
                        }
                    }
                }
            }

            // Привычки
            if (state.habits.isNotEmpty()) {
                item {
                    AppearingGroup(order = 2) {
                        ListGroup(
                            header = stringResource(R.string.today_privychki_1_s_iz_2_s, state.habits.count { it.doneToday }, state.habits.size)
                        ) {
                            state.habits.forEachIndexed { index, progress ->
                                HabitTodayRow(
                                    progress = progress,
                                    onToggle = { _ ->
                                        viewModel.toggleHabit(progress)
                                    },
                                    onOpen = { onOpenHabit(progress.habit.id) },
                                    onCounterClick = { counterDialogTarget = progress },
                                    onMoveUp = if (index > 0) { { viewModel.reorderHabits(index, index - 1) } } else null,
                                    onMoveDown = if (index < state.habits.size - 1) { { viewModel.reorderHabits(index, index + 1) } } else null,
                                    onDelete = { viewModel.deleteHabit(progress.habit.id) }
                                )
                                if (index != state.habits.lastIndex) {
                                    ListDivider(56.dp)
                                }
                            }
                        }
                    }
                }
            }

            // Отказы
            if (state.abstinences.isNotEmpty()) {
                item {
                    AppearingGroup(order = 3) {
                        ListGroup(
                            items = state.abstinences,
                            header = stringResource(R.string.abstinence_otkazy),
                            dividerInset = 16.dp
                        ) { item ->
                            AbstinenceTodayRow(
                                item = item,
                                onOpen = { onOpenAbstinence(item.abstinence.id) },
                                onDelete = { viewModel.deleteAbstinence(item.abstinence.id) }
                            )
                        }
                    }
                }
            }
        }

        ToastHost(toast)

        counterDialogTarget?.let { target ->
            dev.ashwake.ui.habits.components.CounterProgressDialog(
                progress = target,
                onSetProgress = { value ->
                    viewModel.setHabitProgress(target, value)
                },
                onDismiss = { counterDialogTarget = null }
            )
        }
    }
}

/**
 * Карточка сводки дня: лаконичный обзор прогресса с динамической атмосферой (Ambient Gradient)
 * и частицами празднования при 100% выполнении.
 */
@Composable
private fun TodaySummaryCard(
    state: TodayUiState,
    modifier: Modifier = Modifier
) {
    val colors = AshTheme.colors
    val progress by animateFloatAsState(state.progress, label = "day-progress")
    val animatedPercent by animateFloatAsState(state.progress * 100f, label = "day-percent")
    val atmosphere = remember { currentAtmosphere() }
    val gradient = rememberAtmosphereGradient(colors, atmosphere)
    val isCompleted = state.totalCount > 0 && state.doneCount == state.totalCount
    val percentInt = animatedPercent.roundToInt().coerceIn(0, 100)

    Box(
        modifier
            .fillMaxWidth()
            .clip(AshShapes.sheet)
            .background(gradient)
            .border(
                width = if (colors.isDark) 0.dp else 0.75.dp,
                color = if (colors.isDark) androidx.compose.ui.graphics.Color.Transparent else colors.surface3,
                shape = AshShapes.sheet
            )
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Прогресс дня",
                            style = AshTheme.type.title3,
                            color = colors.text,
                            maxLines = 1
                        )
                        Text(
                            text = "${atmosphere.icon} ${atmosphere.title}",
                            style = AshTheme.type.caption,
                            color = colors.text2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = if (state.totalCount == 0) stringResource(R.string.today_na_segodnya_nichego_ne_zaplanirovano)
                        else if (isCompleted) "Все дела выполнены!"
                        else "Выполнено ${state.doneCount} из ${state.totalCount}",
                        style = AshTheme.type.footnote,
                        color = colors.text2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier.defaultMinSize(minWidth = 76.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = "$percentInt%",
                        style = AshTheme.type.title1,
                        fontWeight = FontWeight.Bold,
                        color = if (isCompleted) colors.success else colors.accent,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.End
                    )
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(colors.accent.copy(alpha = 0.14f), AshShapes.pill)
            ) {
                if (progress > 0f) {
                    val baseColor = if (isCompleted) colors.success else colors.accent
                    Box(
                        Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(6.dp)
                            .clip(AshShapes.pill)
                            .background(baseColor, AshShapes.pill)
                    )
                }
            }
        }
    }
}

@Composable
private fun AppearingGroup(order: Int, content: @Composable () -> Unit) {
    val reduceMotion = AshTheme.reduceMotion
    var shown by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { shown = true }

    val appear by animateFloatAsState(
        targetValue = if (shown || reduceMotion) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (reduceMotion) 0 else NORMAL_MS,
            delayMillis = if (reduceMotion) 0 else order * STAGGER_MS
        ),
        label = "group-appear"
    )

    Box(
        Modifier.graphicsLayer {
            alpha = appear
            translationY = (1f - appear) * 24.dp.toPx()
        }
    ) { content() }
}

private const val STAGGER_MS = 55
private val DateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("ru"))
