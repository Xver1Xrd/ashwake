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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import dev.ashwake.R
import dev.ashwake.ui.character.render.PixelCharacter
import dev.ashwake.ui.components.AshIcons
import dev.ashwake.ui.components.EmptyState
import dev.ashwake.ui.components.IconAction
import dev.ashwake.ui.components.ListGroup
import dev.ashwake.ui.components.ScreenPadding
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
                onClick = onOpenCharacter,
                modifier = Modifier.padding(horizontal = ScreenPadding)
            )
        }

        if (state.isEmpty) {
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
                ListGroup(
                    items = state.overdueTasks,
                    header = "Просрочено",
                    dividerInset = 62.dp
                ) { task ->
                    TaskTodayRow(
                        task = task,
                        today = state.today,
                        onToggle = { viewModel.toggleTask(task) },
                        onPostpone = { viewModel.postponeTask(task) },
                        onOpen = { onOpenTask(task.id) }
                    )
                }
            }
        }

        if (state.todayTasks.isNotEmpty()) {
            item {
                ListGroup(
                    items = state.todayTasks,
                    header = "Задачи · ${state.todayTasks.count { it.isDone }} из ${state.todayTasks.size}",
                    dividerInset = 62.dp
                ) { task ->
                    TaskTodayRow(
                        task = task,
                        today = state.today,
                        onToggle = { viewModel.toggleTask(task) },
                        onPostpone = { viewModel.postponeTask(task) },
                        onOpen = { onOpenTask(task.id) }
                    )
                }
            }
        }

        if (state.habits.isNotEmpty()) {
            item {
                ListGroup(
                    items = state.habits,
                    header = "Привычки · ${state.habits.count { it.doneToday }} из ${state.habits.size}",
                    dividerInset = 56.dp
                ) { progress ->
                    HabitTodayRow(
                        progress = progress,
                        onToggle = { viewModel.toggleHabit(progress) },
                        onOpen = { onOpenHabit(progress.habit.id) }
                    )
                }
            }
        }

        if (state.abstinences.isNotEmpty()) {
            item {
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AshTheme.colors
    val progress by animateFloatAsState(state.progress, label = "day-progress")

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
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            AshIcons.Coins,
                            contentDescription = null,
                            tint = colors.warm,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = state.character.wallet.coins.toString(),
                            style = AshTheme.type.headline,
                            color = colors.warm
                        )
                    }
                }

                Box(
                    Modifier
                        .size(CharacterSize)
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
