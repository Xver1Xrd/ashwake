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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ashwake.R
import dev.ashwake.domain.model.habits.HabitType
import dev.ashwake.domain.model.habits.HabitWithProgress
import dev.ashwake.core.model.Priority
import dev.ashwake.domain.model.tasks.Task
import dev.ashwake.ui.components.AshIcons
import dev.ashwake.ui.components.tappable
import dev.ashwake.ui.theme.AshTheme
import dev.ashwake.ui.theme.priorityColors
import java.time.format.DateTimeFormatter

/**
 * Строки привычки и задачи из раздела 5 дизайн-системы.
 *
 * Ключевая деталь: **строка заполняется прогрессом сама**. Отдельных
 * прогресс-баров в приложении нет — фон строки залит акцентом на 12%
 * по доле выполнения. Это второй, помельче, фирменный приём приложения.
 */

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")

/** Доля заливки фона строки: 12% от акцента на полном выполнении. */
private const val FILL_ALPHA = 0.12f

@Composable
fun HabitTodayRow(
    progress: HabitWithProgress,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            .background(colors.warm.copy(alpha = FILL_ALPHA * fill))
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
                onClick = onToggle
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
    onClick: () -> Unit
) {
    val colors = AshTheme.colors
    val accent = if (negative) colors.danger else colors.warm

    Box(
        Modifier
            .size(28.dp)
            .tappable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxWidth().size(28.dp)) {
            val stroke = 2.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)

            if (done) {
                drawCircle(color = accent)
            } else {
                drawCircle(
                    color = colors.text3,
                    style = Stroke(width = stroke)
                )
                if (share > 0f) {
                    drawArc(
                        color = accent,
                        startAngle = -90f,
                        sweepAngle = 360f * share,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
            }
        }
        if (done) {
            Icon(
                imageVector = AshIcons.Check,
                contentDescription = null,
                tint = if (colors.isDark) Color.Black else Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Строка задачи: круглый чекбокс 24dp, название, метаданные одной строкой.
 * Приоритет дублируется словом «Важно» для P1 — ни одно состояние
 * не передаётся только цветом (раздел 9).
 */
@Composable
fun TaskTodayRow(
    task: Task,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AshTheme.colors

    Row(
        modifier
            .fillMaxWidth()
            .tappable(onClick = onOpen)
            .defaultMinSize(minHeight = 44.dp)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TaskCheckbox(done = task.isDone, onClick = onToggle)

        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (task.priority != Priority.P4) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .background(
                                colors.priorityColors[task.priority.ordinal],
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
            val meta = taskMeta(task)
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = AshTheme.type.subhead,
                    color = colors.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun taskMeta(task: Task): String = buildList {
    if (task.priority == Priority.P1) add("Важно")
    task.dueTime?.let { add(it.format(TimeFormat)) }
    task.estimateMinutes?.let { add("${it}м") }
    task.tags.take(2).forEach { add("#${it.name}") }
}.joinToString(" · ")

@Composable
private fun TaskCheckbox(done: Boolean, onClick: () -> Unit) {
    val colors = AshTheme.colors
    Box(
        Modifier
            .size(24.dp)
            .tappable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(24.dp)) {
            val stroke = 2.dp.toPx()
            if (done) drawCircle(color = colors.accent)
            else drawCircle(color = colors.text3, style = Stroke(width = stroke))
        }
        if (done) {
            Icon(
                imageVector = AshIcons.Check,
                contentDescription = null,
                tint = if (colors.isDark) Color.Black else Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
