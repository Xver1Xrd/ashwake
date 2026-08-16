package dev.ashwake.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ashwake.domain.model.habits.HabitWithProgress
import dev.ashwake.domain.model.tasks.Task
import dev.ashwake.ui.theme.Moss

/**
 * «Сегодня»: план дня, задачи на сегодня и привычки.
 *
 * Не дублирует списки задач и привычек, а собирает день: что запланировано,
 * что сделано, что осталось — и позволяет закрыть всё отсюда, не уходя
 * на другие экраны.
 */
@Composable
fun TodayScreen(
    viewModel: TodayViewModel = hiltViewModel()
) {
    val tasks by viewModel.tasksToday.collectAsStateWithLifecycle()
    val habits by viewModel.habitsToday.collectAsStateWithLifecycle()
    val dayPlan by viewModel.dayPlan.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "План дня",
                    style = MaterialTheme.typography.titleMedium
                )
                val plan = dayPlan
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                plan == null -> "Плана нет"
                                plan.hasPlan -> "${plan.blocks.size} блоков · ${plan.deficitMinutes} мин не влезло"
                                else -> "Не разложен"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Задачи без оценки не попадают в автораскладку",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = viewModel::planDay) { Text("Разложить") }
                }
            }

            item {
                HorizontalDivider()
                Text(
                    "Задачи на сегодня (${tasks.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (tasks.isEmpty()) {
                item {
                    Text(
                        "На сегодня ничего не запланировано",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(tasks, key = { it.id }) { task ->
                TaskRow(
                    task = task,
                    onComplete = { viewModel.complete(task) },
                    onPostpone = { viewModel.postpone(task) }
                )
            }

            item {
                HorizontalDivider()
                Text(
                    "Привычки (${habits.count { it.doneToday }}/${habits.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (habits.isEmpty()) {
                item {
                    Text(
                        "Привычек на сегодня нет",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(habits, key = { it.habit.id }) { progress ->
                HabitRow(
                    progress = progress,
                    onToggle = { viewModel.toggleHabit(progress) }
                )
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: Task,
    onComplete: () -> Unit,
    onPostpone: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = "Закрыть",
            modifier = Modifier.clickable(onClick = onComplete)
        )
        Column(
            Modifier.weight(1f).padding(horizontal = 12.dp)
        ) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val meta = buildList {
                task.dueTime?.let { add(it.toString()) }
                task.estimateMinutes?.let { add("$it мин") }
            }
            if (meta.isNotEmpty()) {
                Text(
                    meta.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        TextButton(onClick = onPostpone) { Text("Позже") }
    }
}

@Composable
private fun HabitRow(progress: HabitWithProgress, onToggle: () -> Unit) {
    val done = progress.doneToday
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (done) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onToggle)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = if (done) Moss else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(progress.habit.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                buildString {
                    append("Серия ${progress.currentStreak}")
                    if (progress.dueToday) append(" · план на сегодня")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            if (done) "Сделано" else "Отметить",
            style = MaterialTheme.typography.labelMedium,
            color = if (done) Moss else MaterialTheme.colorScheme.primary
        )
    }
}
