package dev.ashwake.ui.tasks.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ashwake.domain.model.tasks.StaleLevel
import dev.ashwake.domain.model.tasks.Task
import dev.ashwake.ui.theme.Blood
import dev.ashwake.ui.theme.Gold
import dev.ashwake.ui.theme.Moss
import dev.ashwake.ui.theme.PriorityColors
import dev.ashwake.ui.theme.Steel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Строка задачи со свайпами: вправо — выполнено, влево — на завтра (п. 1).
 *
 * `confirmValueChange` намеренно возвращает false: строка отыгрывает свайп и
 * возвращается на место, а действие выполняется отдельно. Иначе перенесённая
 * задача осталась бы «уехавшей» — она ведь никуда из списка не делась.
 */
@Composable
fun TaskRow(
    task: Task,
    today: LocalDate,
    onComplete: () -> Unit,
    onPostpone: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    onExpandToggle: (() -> Unit)? = null
) {
    val complete by rememberUpdatedState(onComplete)
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
        backgroundContent = { SwipeBackground(dismissState.dismissDirection) },
        content = { TaskRowContent(task, today, onClick, expanded, onExpandToggle) }
    )
}

@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue) {
    val (color, icon, alignment) = when (direction) {
        SwipeToDismissBoxValue.StartToEnd ->
            Triple(Moss, Icons.Filled.Check, Alignment.CenterStart)
        SwipeToDismissBoxValue.EndToStart ->
            Triple(Steel, Icons.Filled.EventRepeat, Alignment.CenterEnd)
        SwipeToDismissBoxValue.Settled ->
            Triple(Color.Transparent, Icons.Filled.Check, Alignment.Center)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(color)
            .padding(horizontal = 20.dp),
        contentAlignment = alignment
    ) {
        if (direction != SwipeToDismissBoxValue.Settled) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
private fun TaskRowContent(
    task: Task,
    today: LocalDate,
    onClick: () -> Unit,
    expanded: Boolean,
    onExpandToggle: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Цвет по приоритету — тот же, что в матрице Эйзенхауэра
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 36.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(PriorityColors[task.priority.ordinal])
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
                color = if (task.isDone) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface
            )
            val meta = buildMeta(task, today)
            if (meta.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (task.isOverdue(today)) Blood
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (task.tags.isNotEmpty()) {
                Text(
                    text = task.tags.joinToString(" ") { "#${it.name}" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (task.recurrence != null) {
            Icon(
                Icons.Filled.Repeat,
                contentDescription = "Повторяется",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (task.sourceLink != null) {
            Icon(
                Icons.Filled.Link,
                contentDescription = "Есть ссылка",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        StaleBadge(task)

        if (task.subtasks.isNotEmpty() && onExpandToggle != null) {
            IconButton(onClick = onExpandToggle, modifier = Modifier.size(24.dp)) {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Свернуть подзадачи"
                    else "Показать подзадачи",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Подзадачи под строкой. Отдельно от [TaskRow], чтобы свайп не задевал их. */
@Composable
fun SubtaskList(
    task: Task,
    onToggle: (Task) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(start = 28.dp, top = 2.dp)) {
        task.subtasks.forEach { subtask ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggle(subtask) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (subtask.isDone) Icons.Filled.CheckCircle
                    else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (subtask.isDone) Moss else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "  ${subtask.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = if (subtask.isDone) TextDecoration.LineThrough else null,
                    color = if (subtask.isDone) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Жёлтая метка с 3 переносов, красная с 5 (п. 1). */
@Composable
private fun StaleBadge(task: Task) {
    val color = when (task.staleLevel) {
        StaleLevel.NONE -> return
        StaleLevel.WARNING -> Gold
        StaleLevel.CRITICAL -> Blood
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = "×${task.postponeCount}",
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

private fun buildMeta(task: Task, today: LocalDate): String {
    val parts = mutableListOf<String>()
    task.dueDate?.let { date ->
        parts += when (date) {
            today -> "сегодня"
            today.plusDays(1) -> "завтра"
            today.minusDays(1) -> "вчера"
            else -> date.format(DATE_FORMAT)
        }
    }
    task.dueTime?.let { parts += it.format(TIME_FORMAT) }
    task.estimateMinutes?.let { parts += formatEstimate(it) }
    if (task.subtasks.isNotEmpty()) {
        parts += "${task.subtasks.count { it.isDone }}/${task.subtasks.size}"
    }
    task.delegatedTo?.let { parts += "→ $it" }
    return parts.joinToString(" · ")
}

private fun formatEstimate(minutes: Int): String = when {
    minutes < 60 -> "${minutes}м"
    minutes % 60 == 0 -> "${minutes / 60}ч"
    else -> "${minutes / 60}ч ${minutes % 60}м"
}
