package dev.ashwake.ui.today

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import dev.ashwake.R
import dev.ashwake.ui.character.render.PixelCharacter
import dev.ashwake.ui.components.AshIcons
import dev.ashwake.ui.components.CELEBRATION_MS
import dev.ashwake.ui.components.CoinFlightHost
import dev.ashwake.ui.components.CoinFlightState
import dev.ashwake.ui.components.NORMAL_MS
import dev.ashwake.ui.components.RollingNumber
import dev.ashwake.ui.components.coinFlightTarget
import dev.ashwake.ui.components.motionTween
import dev.ashwake.ui.components.rememberCoinFlightState
import dev.ashwake.ui.components.responseSpring
import dev.ashwake.ui.components.EmptyState
import dev.ashwake.ui.components.IconAction
import dev.ashwake.ui.components.ListGroup
import dev.ashwake.ui.components.ScreenPadding
import dev.ashwake.ui.components.SkeletonList
import dev.ashwake.ui.components.appHazeSource
import dev.ashwake.ui.components.tappable
import dev.ashwake.ui.theme.AshShapes
import dev.ashwake.ui.theme.AshTheme
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Главный экран.
 *
 * Порядок сверху вниз повторяет то, ради чего экран открывают: сначала
 * персонаж — он показывает, что накопилось за все дни, — потом задачи и
 * привычки на сегодня, внизу отказы с их счётчиками.
 *
 * Персонаж не уезжает в маленький аватар при прокрутке, как было раньше:
 * схлопывание отнимало у списков верх экрана и требовало держать обложку
 * поверх содержимого. Теперь герой-блок прокручивается вместе со всем
 * остальным, и списки видно сразу — ради них экран и открывают.
 */
private val CharacterSize = 132.dp

@Composable
fun TodayScreen(
    onOpenHabit: (Long) -> Unit,
    onOpenTask: (Long) -> Unit,
    onCreateTask: () -> Unit,
    onOpenCharacter: () -> Unit,
    onOpenAbstinence: (Long) -> Unit,
    viewModel: TodayViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AshTheme.colors
    val coins = rememberCoinFlightState()

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .appHazeSource(),
        contentPadding = PaddingValues(bottom = 24.dp),
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
                        style = AshTheme.type.largeTitle,
                        color = colors.text
                    )
                    Text(
                        text = state.today.format(DateFormat)
                            .replaceFirstChar { it.titlecase(Locale.getDefault()) },
                        style = AshTheme.type.subhead,
                        color = colors.text2
                    )
                }
                IconAction(
                    icon = AshIcons.Add,
                    contentDescription = stringResource(R.string.today_novaya_zadacha),
                    onClick = onCreateTask
                )
            }
        }

        item {
            HeroCard(
                state = state,
                coins = coins,
                onClick = onOpenCharacter,
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

        // Просрочка стоит выше всего остального: это то, о чём человек уже
        // один раз забыл, и в общем списке она затеряется второй раз
        if (state.overdueTasks.isNotEmpty()) {
            item {
                AppearingGroup(order = 0) {
                    ListGroup(
                        items = state.overdueTasks,
                        header = "Просрочено",
                        dividerInset = 62.dp
                    ) { task ->
                        TaskTodayRow(
                            task = task,
                            today = state.today,
                            onToggle = { from ->
                                viewModel.toggleTask(task)
                                // Монета летит только при закрытии задачи:
                                // возврат в работу её и так забирает обратно
                                if (!task.isDone) coins.launch(from)
                            },
                            onPostpone = { viewModel.postponeTask(task) },
                            onOpen = { onOpenTask(task.id) }
                        )
                    }
                }
            }
        }

        if (state.todayTasks.isNotEmpty()) {
            item {
                AppearingGroup(order = 1) {
                    ListGroup(
                        items = state.todayTasks,
                        header = "Задачи · ${state.todayTasks.count { it.isDone }} из ${state.todayTasks.size}",
                        dividerInset = 62.dp
                    ) { task ->
                        TaskTodayRow(
                            task = task,
                            today = state.today,
                            onToggle = { from ->
                                viewModel.toggleTask(task)
                                // Монета летит только при закрытии задачи:
                                // возврат в работу её и так забирает обратно
                                if (!task.isDone) coins.launch(from)
                            },
                            onPostpone = { viewModel.postponeTask(task) },
                            onOpen = { onOpenTask(task.id) }
                        )
                    }
                }
            }
        }

        if (state.habits.isNotEmpty()) {
            item {
                AppearingGroup(order = 2) {
                    ListGroup(
                        items = state.habits,
                        header = "Привычки · ${state.habits.count { it.doneToday }} из ${state.habits.size}",
                        dividerInset = 56.dp
                    ) { progress ->
                        HabitTodayRow(
                            progress = progress,
                            onToggle = { from ->
                                viewModel.toggleHabit(progress)
                                if (!progress.doneToday) coins.launch(from)
                            },
                            onOpen = { onOpenHabit(progress.habit.id) }
                        )
                    }
                }
            }
        }

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
                            onOpen = { onOpenAbstinence(item.abstinence.id) }
                        )
                    }
                }
            }
        }
    }

        CoinFlightHost(coins)
    }
}

/**
 * Каскад появления групп.
 *
 * Группы проявляются друг за другом с задержкой в [STAGGER_MS]: экран
 * собирается на глазах, а не возникает целиком. Только при первом входе —
 * повторять это при каждой перерисовке значило бы мигать списком на каждую
 * отметку.
 */
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

/** Задержка между соседними группами. Больше — и каскад читается как тормоза. */
private const val STAGGER_MS = 55

/**
 * Герой-блок с персонажем.
 *
 * Единственное место в приложении с градиентной заливкой: этим он и
 * отличается от всех прочих карточек, поэтому глаз находит его первым,
 * не разбирая экран по частям.
 */
@Composable
private fun HeroCard(
    state: TodayUiState,
    coins: CoinFlightState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AshTheme.colors
    val progress by animateFloatAsState(state.progress, label = "day-progress")

    // Кошелёк дёргается, когда в него прилетает монета: без этого полёт
    // заканчивается в никуда и не читается как зачисление
    val walletBump by animateFloatAsState(
        targetValue = if (coins.landing) 1.25f else 1f,
        animationSpec = responseSpring(),
        label = "wallet-bump"
    )

    Box(
        modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    listOf(
                        colors.accent.copy(alpha = 0.22f),
                        colors.accentAlt.copy(alpha = 0.10f),
                        colors.surface1
                    )
                ),
                AshShapes.sheet
            )
            .tappable(onClick = onClick)
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = state.character.profile.name,
                        style = AshTheme.type.title2,
                        color = colors.text
                    )
                    Text(
                        text = "Уровень ${state.character.level}",
                        style = AshTheme.type.subhead,
                        color = colors.text2
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .coinFlightTarget(coins)
                            .graphicsLayer {
                                scaleX = walletBump
                                scaleY = walletBump
                                transformOrigin = TransformOrigin(0f, 0.5f)
                            }
                    ) {
                        Icon(
                            AshIcons.Coins,
                            contentDescription = null,
                            tint = colors.warm,
                            modifier = Modifier.size(16.dp)
                        )
                        RollingNumber(
                            value = state.character.wallet.coins,
                            style = AshTheme.type.headline,
                            color = colors.warm
                        )
                    }
                }

                // Осанка по прогрессу дня: при нуле фигура чуть осела и
                // наклонена, к сотне выпрямляется. Это не отдельная анимация,
                // а связь картинки с тем, что человек сделал за день
                val posture by animateFloatAsState(
                    targetValue = state.progress,
                    animationSpec = motionTween(CELEBRATION_MS),
                    label = "posture"
                )
                val nod by animateFloatAsState(
                    targetValue = if (coins.landing) 1f else 0f,
                    animationSpec = responseSpring(),
                    label = "nod"
                )

                Box(
                    Modifier
                        .size(CharacterSize)
                        .graphicsLayer {
                            val slouch = (1f - posture) * 4.dp.toPx()
                            translationY = slouch - nod * 8.dp.toPx()
                            rotationZ = (1f - posture) * -2.5f
                            scaleX = 1f + nod * 0.04f
                            scaleY = 1f + nod * 0.04f
                        }
                        .semantics {
                            contentDescription =
                                "Персонаж, выполнено ${state.doneCount} из ${state.totalCount} дел"
                        }
                ) {
                    PixelCharacter(
                        layers = state.layers,
                        reduceMotion = AshTheme.reduceMotion ||
                            state.character.profile.reduceMotion,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            DayProgress(
                progress = progress,
                done = state.doneCount,
                total = state.totalCount
            )
        }
    }
}

/**
 * Полоса дня. Именно полоса, а не кольцо: у кольца читается только
 * заполненность «на глаз», а здесь рядом стоят два числа, и полоса
 * работает их иллюстрацией.
 */
@Composable
private fun DayProgress(progress: Float, done: Int, total: Int) {
    val colors = AshTheme.colors

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (total == 0) "На сегодня ничего не запланировано"
                else "Сделано $done из $total",
                style = AshTheme.type.footnote,
                color = colors.text2,
                modifier = Modifier.weight(1f)
            )
            if (total > 0) {
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = AshTheme.type.footnote,
                    color = colors.text2
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(colors.surface3, AshShapes.pill)
        ) {
            if (progress > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(6.dp)
                        .background(colors.accentGradient, AshShapes.pill)
                )
            }
        }
    }
}

private val DateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM")
