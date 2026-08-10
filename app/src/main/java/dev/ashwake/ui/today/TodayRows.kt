package dev.ashwake.ui.today

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ashwake.R
import dev.ashwake.domain.model.habits.HabitType
import dev.ashwake.domain.model.habits.HabitWithProgress
import dev.ashwake.domain.model.tasks.Task
import dev.ashwake.domain.repository.abstinence.AbstinenceWithStats
import dev.ashwake.ui.components.AshIcons
import dev.ashwake.ui.components.DrawnCheck
import dev.ashwake.ui.components.EntityIcon
import dev.ashwake.ui.components.QUICK_MS
import dev.ashwake.ui.components.motionTween
import dev.ashwake.ui.components.responseSpring
import dev.ashwake.ui.components.tappable
import dev.ashwake.ui.theme.AshShapes
import dev.ashwake.ui.theme.AshTheme
import dev.ashwake.ui.theme.hasMark
import dev.ashwake.ui.theme.meaning
import dev.ashwake.ui.theme.priorityColor
import java.time.format.DateTimeFormatter

/**
 * Строки привычки и задачи из раздела 5 дизайн-системы.
 *
 * Ключевая деталь: **строка заполняется прогрессом сама**. Отдельных
 * прогресс-баров в приложении нет — фон строки залит акцентом на 12%
 * по доле выполнения. Это второй, помельче, фирменный приём приложения.
 */

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val DateFormat = DateTimeFormatter.ofPattern("d MMM")

/** Доля заливки фона строки: 12% от акцента на полном выполнении. */
private const val FILL_ALPHA = 0.12f

@Composable
fun HabitTodayRow(
    progress: HabitWithProgress,
    onToggle: (from: Offset) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    var markCenter by remember { mutableStateOf(Offset.Zero) }
    val colors = AshTheme.colors
    val habit = progress.habit

    val share = when {
        progress.doneToday -> 1f
        habit.type == HabitType.COUNTER && habit.targetValue > 0f ->
            (progress.todayValue / habit.targetValue).coerceIn(0f, 1f)
        else -> 0f
    }
    // Заливка догоняет отметку, а не перескакивает вместе с ней (раздел 6)
    val fill by animateFloatAsState(targetValue = share, label = "row-fill")

    Box(
        modifier
            .fillMaxWidth()
            // Заливка расходится от отметки вправо, а не появляется всей
            // строкой сразу: видно, что закрасило её именно нажатие
            .background(
                Brush.horizontalGradient(
                    0f to colors.warm.copy(alpha = FILL_ALPHA * fill),
                    (fill * 1.15f).coerceIn(0.001f, 1f) to
                        colors.warm.copy(alpha = FILL_ALPHA * fill),
                    ((fill * 1.15f) + 0.12f).coerceIn(0.002f, 1f) to Color.Transparent
                )
            )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .tappable(onClick = onOpen)
                .defaultMinSize(minHeight = 60.dp)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HabitMark(
                done = progress.doneToday,
                share = fill,
                negative = habit.type == HabitType.NEGATIVE,
                onClick = { onToggle(markCenter) },
                onPositioned = { markCenter = it }
            )

            EntityIcon(
                emoji = habit.icon,
                iconPath = habit.iconPath,
                size = 30.dp
            )

            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Text(
                    text = habit.name,
                    style = AshTheme.type.headline,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val streak = progress.currentStreak
                if (streak > 0) {
                    Text(
                        text = pluralStringResource(R.plurals.streak_days, streak, streak),
                        style = AshTheme.type.subhead,
                        color = colors.text2
                    )
                }
            }

            if (habit.type == HabitType.COUNTER) {
                Text(
                    text = counterLabel(progress),
                    style = AshTheme.type.subhead,
                    color = colors.text2
                )
            }

            Icon(
                imageVector = AshIcons.ChevronRight,
                contentDescription = null,
                tint = colors.text3,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

private fun counterLabel(progress: HabitWithProgress): String {
    val habit = progress.habit
    val current = progress.todayValue
    val target = habit.targetValue
    val unit = habit.unitName?.let { " $it" }.orEmpty()
    return "${trimNumber(current)} / ${trimNumber(target)}$unit"
}

/** 1.0 показывается как «1», 1.25 — как «1.25»: хвост из нулей только мешает. */
private fun trimNumber(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else "%.2f".format(value).trimEnd('0').trimEnd('.')

/**
 * Круг отметки, 28dp.
 *
 * Пустой контур — не сделано, залитый тёплым с галочкой — сделано,
 * дуга по контуру — прогресс счётчика. У негативной привычки отметка
 * означает срыв, поэтому и цвет у неё тревожный, а не тёплый.
 */
@Composable
private fun HabitMark(
    done: Boolean,
    share: Float,
    negative: Boolean,
    onClick: () -> Unit,
    onPositioned: (Offset) -> Unit = {}
) {
    val colors = AshTheme.colors
    val accent = if (negative) colors.danger else colors.warm

    val fill by animateFloatAsState(
        targetValue = if (done) 1f else 0f,
        animationSpec = responseSpring(),
        label = "mark-fill"
    )
    val stroke by animateFloatAsState(
        targetValue = if (done) 1f else 0f,
        animationSpec = motionTween(QUICK_MS, delayMillis = if (done) 60 else 0),
        label = "mark-stroke"
    )

    Box(
        Modifier
            .size(28.dp)
            .onGloballyPositioned { onPositioned(it.boundsInRoot().center) }
            .tappable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(28.dp)) {
            val width = 2.dp.toPx()
            val inset = width / 2f
            val arcSize = Size(size.width - width, size.height - width)

            drawCircle(
                color = colors.text3,
                style = Stroke(width = width),
                alpha = 1f - fill
            )
            if (share > 0f && fill < 1f) {
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * share,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = width, cap = StrokeCap.Round),
                    alpha = 1f - fill
                )
            }
            if (fill > 0f) {
                drawCircle(color = accent, radius = size.minDimension / 2f * fill)
            }
        }
        DrawnCheck(
            progress = stroke,
            color = if (colors.isDark) Color.Black else Color.White,
            modifier = Modifier.size(28.dp),
            strokeWidth = 2.2.dp
        )
    }
}

/**
 * Строка задачи со свайпами: вправо — выполнено, влево — на завтра.
 *
 * Свайпы здесь те же, что на экране задач, и по той же причине: главный
 * экран это и есть список дня, и разбирать его чекбоксом по одной задаче
 * медленнее, чем провести пальцем. Строка отыгрывает свайп и возвращается
 * на место — задача из списка дня никуда не делась.
 */
@Composable
fun TaskTodayRow(
    task: Task,
    today: java.time.LocalDate,
    onToggle: (from: Offset) -> Unit,
    onPostpone: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Точка, из которой вылетает монета. Свайп тоже закрывает задачу,
    // поэтому запоминаем место чекбокса, а не место касания
    var markCenter by remember { mutableStateOf(Offset.Zero) }
    val complete by rememberUpdatedState { onToggle(markCenter) }
    val postpone by rememberUpdatedState(onPostpone)

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> complete()
                SwipeToDismissBoxValue.EndToStart -> postpone()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = { SwipeHint(dismissState.dismissDirection) },
        content = {
            TaskTodayRowContent(
                task = task,
                today = today,
                onToggle = { onToggle(markCenter) },
                onMarkPositioned = { markCenter = it },
                onOpen = onOpen
            )
        }
    )
}

/** Подложка под уезжающей строкой: цвет и значок того, что произойдёт. */
@Composable
private fun SwipeHint(direction: SwipeToDismissBoxValue) {
    val colors = AshTheme.colors
    val (color, icon, alignment) = when (direction) {
        SwipeToDismissBoxValue.StartToEnd ->
            Triple(colors.success, AshIcons.Check, Alignment.CenterStart)

        SwipeToDismissBoxValue.EndToStart ->
            Triple(colors.cold, AshIcons.EventRepeat, Alignment.CenterEnd)

        SwipeToDismissBoxValue.Settled ->
            Triple(Color.Transparent, AshIcons.Check, Alignment.Center)
    }

    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(color)
            .padding(horizontal = 24.dp),
        contentAlignment = alignment
    ) {
        if (direction != SwipeToDismissBoxValue.Settled) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (colors.isDark) Color.Black else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun TaskTodayRowContent(
    task: Task,
    today: java.time.LocalDate,
    onToggle: () -> Unit,
    onMarkPositioned: (Offset) -> Unit,
    onOpen: () -> Unit
) {
    val colors = AshTheme.colors
    val overdue = task.isOverdue(today)

    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surface1)
            .tappable(onClick = onOpen)
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 16.dp, vertical = AshTheme.density.rowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TaskCheckbox(
            done = task.isDone,
            onClick = onToggle,
            onPositioned = onMarkPositioned
        )

        EntityIcon(
            emoji = task.emoji,
            iconPath = task.iconPath,
            size = 30.dp,
            background = if (task.priority.hasMark) {
                colors.priorityColor(task.priority).copy(alpha = 0.16f)
            } else {
                colors.surface2
            }
        )

        androidx.compose.foundation.layout.Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Точка приоритета не нужна, когда цвет уже несёт подложка значка
                if (task.priority.hasMark && task.emoji == null && task.iconPath == null) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .background(
                                colors.priorityColor(task.priority),
                                androidx.compose.foundation.shape.CircleShape
                            )
                    )
                }
                Text(
                    text = task.title,
                    style = AshTheme.type.headline,
                    color = if (task.isDone) colors.text3 else colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            val meta = taskMeta(task, today)
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = AshTheme.type.subhead,
                    color = if (overdue) colors.danger else colors.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun taskMeta(task: Task, today: java.time.LocalDate): String = buildList {
    task.dueDate?.let { date ->
        if (date < today) add(if (date == today.minusDays(1)) "вчера" else date.format(DateFormat))
    }
    if (task.priority.hasMark) add(task.priority.meaning)
    task.dueTime?.let { add(it.format(TimeFormat)) }
    task.estimateMinutes?.let { add("${it} мин") }
    task.tags.take(2).forEach { add("#${it.name}") }
}.joinToString(" · ")

/**
 * Строка отказа: название, счётчик дней и рекорд.
 *
 * Крупная цифра здесь ни к чему — на главном экране это одна из четырёх
 * групп, а не отдельный экран. Цифра набрана тем же шрифтом счётчиков,
 * чтобы связь с экраном отказа читалась.
 */
@Composable
fun AbstinenceTodayRow(
    item: AbstinenceWithStats,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AshTheme.colors
    val days = item.stats.currentDays

    Row(
        modifier
            .fillMaxWidth()
            .tappable(onClick = onOpen)
            .defaultMinSize(minHeight = 60.dp)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EntityIcon(
            emoji = item.abstinence.icon,
            iconPath = item.abstinence.iconPath,
            size = 34.dp,
            background = colors.cold.copy(alpha = 0.16f),
            fallback = {
                Box(
                    Modifier
                        .size(34.dp)
                        .background(colors.cold.copy(alpha = 0.16f), AshShapes.squircle(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AshIcons.Prohibit,
                        contentDescription = null,
                        tint = colors.cold,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        )

        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(
                text = item.abstinence.name,
                style = AshTheme.type.headline,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = pluralStringResource(R.plurals.streak_days, days.toInt(), days.toInt()) +
                    " · рекорд ${item.stats.record.toDays()}",
                style = AshTheme.type.subhead,
                color = colors.text2
            )
        }

        Icon(
            imageVector = AshIcons.ChevronRight,
            contentDescription = null,
            tint = colors.text3,
            modifier = Modifier.size(14.dp)
        )
    }
}

/**
 * Чекбокс задачи.
 *
 * Круг заливается из центра, галочка прочерчивается штрихом следом. Пустой
 * контур сжимается — так отметка выглядит одним движением, а не сменой
 * двух картинок.
 */
@Composable
private fun TaskCheckbox(
    done: Boolean,
    onClick: () -> Unit,
    onPositioned: (Offset) -> Unit = {}
) {
    val colors = AshTheme.colors
    val fill by animateFloatAsState(
        targetValue = if (done) 1f else 0f,
        animationSpec = responseSpring(),
        label = "check-fill"
    )
    // Галочка идёт следом за заливкой, а не вместе с ней: сначала круг
    // становится своим цветом, потом по нему пишут
    val stroke by animateFloatAsState(
        targetValue = if (done) 1f else 0f,
        animationSpec = motionTween(QUICK_MS, delayMillis = if (done) 60 else 0),
        label = "check-stroke"
    )

    Box(
        Modifier
            .size(24.dp)
            .onGloballyPositioned { onPositioned(it.boundsInRoot().center) }
            .tappable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(24.dp)) {
            val strokeWidth = 2.dp.toPx()
            drawCircle(
                color = colors.text3,
                style = Stroke(width = strokeWidth),
                alpha = 1f - fill
            )
            if (fill > 0f) {
                drawCircle(color = colors.accent, radius = size.minDimension / 2f * fill)
            }
        }
        DrawnCheck(
            progress = stroke,
            color = if (colors.isDark) Color.Black else Color.White,
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp
        )
    }
}
