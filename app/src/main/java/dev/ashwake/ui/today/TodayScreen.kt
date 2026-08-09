package dev.ashwake.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ashwake.ui.character.render.PixelCharacter
import dev.ashwake.ui.components.AshIcons
import dev.ashwake.ui.components.EmptyState
import dev.ashwake.ui.components.IconAction
import dev.ashwake.ui.components.ListGroup
import dev.ashwake.ui.components.ScreenPadding
import dev.ashwake.ui.components.glass
import dev.ashwake.ui.components.hazeSource
import dev.ashwake.ui.components.rememberHazeState
import dev.ashwake.ui.theme.AshTheme
import androidx.compose.ui.res.stringResource
import dev.ashwake.R

/**
 * Главный экран — подпись приложения (раздел 1 дизайн-системы).
 *
 * Диорама с персонажем занимает верхнюю треть и при прокрутке сжимается
 * вместе с крупным заголовком: персонаж уменьшается и в конце превращается
 * в круглый аватар слева в компактной панели, с кольцом прогресса дня вокруг.
 *
 * Схлопывание привязано к прокрутке **напрямую**, а не запускается анимацией
 * по событию: доля схлопывания считается из `firstVisibleItemScrollOffset`,
 * поэтому обложка движется ровно за пальцем и в обратную сторону тоже.
 *
 * Один непрерывный жест связывает «смотрю на персонажа» и «работаю со
 * списком», без переключения экранов.
 */

/** Высота развёрнутой обложки. Примерно верхняя треть экрана. */
private val CoverExpandedHeight = 260.dp

/** Высота компактной панели, в которую обложка схлопывается. */
private val CompactBarHeight = 52.dp

private val AvatarSize = 32.dp
private val CharacterExpandedSize = 190.dp

@Composable
fun TodayScreen(
    onOpenHabit: (Long) -> Unit,
    onOpenTask: (Long) -> Unit,
    onCreateTask: () -> Unit,
    viewModel: TodayViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AshTheme.colors
    val haze = rememberHazeState()
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    // Расстояние, на котором обложка полностью схлопывается
    val collapseDistancePx = remember(density) {
        with(density) { (CoverExpandedHeight - CompactBarHeight).toPx() }
    }

    val collapse by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / collapseDistancePx).coerceIn(0f, 1f)
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        val screenWidth = maxWidth

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(haze),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Место под обложку: сама обложка лежит поверх и не прокручивается,
            // поэтому здесь именно распорка, а не контент
            item { Spacer(Modifier.height(CoverExpandedHeight)) }

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenPadding),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.calendar_segodnya),
                        style = AshTheme.type.largeTitle,
                        color = colors.text,
                        modifier = Modifier.weight(1f)
                    )
                    IconAction(
                        icon = AshIcons.Add,
                        contentDescription = stringResource(R.string.today_novaya_zadacha),
                        onClick = onCreateTask
                    )
                }
            }

            if (state.totalCount == 0) {
                item {
                    EmptyState(
                        icon = AshIcons.Sun,
                        title = stringResource(R.string.today_na_segodnya_pusto),
                        description = stringResource(R.string.today_zavedite_privychku_ili_zadachu_oni_poyavyats),
                        actionText = stringResource(R.string.today_sozdat_zadachu),
                        onAction = onCreateTask
                    )
                }
            }

            if (state.habits.isNotEmpty()) {
                item {
                    ListGroup(
                        items = state.habits,
                        header = "Сегодня, ${state.doneCount} из ${state.totalCount}",
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

            if (state.tasks.isNotEmpty()) {
                item {
                    ListGroup(
                        items = state.tasks,
                        header = stringResource(R.string.tasks_zadachi),
                        dividerInset = 52.dp
                    ) { task ->
                        TaskTodayRow(
                            task = task,
                            onToggle = { viewModel.toggleTask(task) },
                            onOpen = { onOpenTask(task.id) }
                        )
                    }
                }
            }
        }

        Cover(
            collapse = collapse,
            screenWidth = screenWidth,
            state = state,
            haze = haze,
            onCreateTask = onCreateTask
        )
    }
}

/**
 * Обложка: диорама, которая на глазах превращается в панель.
 *
 * Все величины интерполируются одной долей [collapse], поэтому промежуточные
 * состояния выглядят как одно движение, а не как несколько независимых
 * анимаций, каждая со своей скоростью.
 */
@Composable
private fun Cover(
    collapse: Float,
    screenWidth: androidx.compose.ui.unit.Dp,
    state: TodayUiState,
    haze: dev.chrisbanes.haze.HazeState,
    onCreateTask: () -> Unit
) {
    val colors = AshTheme.colors
    val height = lerp(CoverExpandedHeight, CompactBarHeight, collapse)
    val characterSize = lerp(CharacterExpandedSize, AvatarSize, collapse)

    // Персонаж едет из центра диорамы в левый край панели одним движением
    val startX = (screenWidth - CharacterExpandedSize) / 2
    val endX = ScreenPadding
    val characterX = lerp(startX, endX, collapse)
    val characterY = lerp(28.dp, (CompactBarHeight - AvatarSize) / 2, collapse)

    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            // Панель становится стеклом только когда действительно схлопнулась:
            // размывать диораму на весь экран незачем и дорого
            .then(
                if (collapse > 0.99f) Modifier.glass(haze)
                else Modifier.background(
                    Brush.verticalGradient(
                        // Диорама плавно уходит в фон, а не обрывается кромкой
                        0f to colors.surface1,
                        1f to colors.background
                    )
                )
            )
    ) {
        Box(Modifier.statusBarsPadding().fillMaxWidth().height(height)) {
            Box(
                Modifier
                    .offset(x = characterX, y = characterY)
                    .size(characterSize)
                    .semantics {
                        contentDescription =
                            "Персонаж, выполнено ${state.doneCount} из ${state.totalCount} дел"
                    }
            ) {
                PixelCharacter(
                    layers = state.layers,
                    reduceMotion = AshTheme.reduceMotion || state.character.profile.reduceMotion,
                    modifier = Modifier.fillMaxSize()
                )
                // Кольцо прогресса появляется только вокруг аватара:
                // на большой диораме оно смотрелось бы мишенью
                if (collapse > 0f) {
                    ProgressRing(
                        progress = state.progress,
                        alpha = collapse,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Заголовок и «+» в компактной панели проявляются по мере схлопывания
            if (collapse > 0f) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(CompactBarHeight)
                        .alpha(collapse)
                        .padding(horizontal = ScreenPadding),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.size(AvatarSize))
                    Text(
                        text = stringResource(R.string.calendar_segodnya),
                        style = AshTheme.type.headline,
                        color = colors.text,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    IconAction(
                        icon = AshIcons.Add,
                        contentDescription = null,
                        onClick = onCreateTask
                    )
                }
            }
        }
    }
}

/** Кольцо прогресса дня вокруг аватара. Тонкое, без подложки-«пилюли». */
@Composable
private fun ProgressRing(
    progress: Float,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    val colors = AshTheme.colors
    Canvas(modifier) {
        val stroke = 2.5.dp.toPx()
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)

        drawArc(
            color = colors.surface3.copy(alpha = alpha),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawArc(
            color = colors.warm.copy(alpha = alpha),
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}
