package dev.ashwake.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ashwake.ui.tasks.calendar.TaskCalendar
import dev.ashwake.ui.tasks.components.ProjectsDialog
import dev.ashwake.ui.tasks.components.QuickAddBar
import dev.ashwake.ui.tasks.components.StaleTaskDialog
import dev.ashwake.ui.tasks.components.SubtaskList
import dev.ashwake.ui.tasks.components.TaskFilterRow
import dev.ashwake.ui.tasks.components.TaskRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    onOpenTask: (Long) -> Unit,
    viewModel: TasksViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val calendar by viewModel.calendarState.collectAsStateWithLifecycle()
    var showProjects by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Задачи") },
                actions = {
                    IconButton(onClick = viewModel::toggleStaleFilter) {
                        Icon(
                            Icons.Filled.HourglassBottom,
                            contentDescription = "Залежавшиеся",
                            tint = if (state.filter.onlyStale) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { viewModel.setViewMode(nextMode(state.viewMode)) }) {
                        Icon(
                            when (state.viewMode) {
                                TasksViewMode.LIST -> Icons.Filled.GridView
                                TasksViewMode.MATRIX -> Icons.Filled.CalendarMonth
                                TasksViewMode.CALENDAR -> Icons.AutoMirrored.Filled.List
                            },
                            contentDescription = "Режим отображения"
                        )
                    }
                }
            )
        },
        bottomBar = {
            QuickAddBar(
                value = state.quickInput,
                parsed = state.parsed,
                onValueChange = viewModel::onQuickInputChange,
                onSubmit = viewModel::submitQuickInput,
                // Голосовой ввод подключается на этапе 8 вместе с SpeechRecognizer
                onVoiceClick = {}
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.viewMode != TasksViewMode.CALENDAR) {
                TaskFilterRow(
                    filter = state.filter,
                    projects = state.projects,
                    tags = state.tags,
                    onToggleDone = viewModel::toggleShowDone,
                    onProjectSelected = viewModel::setProjectFilter,
                    onTagSelected = viewModel::setTagFilter,
                    onManageProjects = { showProjects = true },
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            Box(Modifier.fillMaxSize()) {
                when {
                    state.viewMode == TasksViewMode.CALENDAR -> TaskCalendar(
                        state = calendar,
                        today = state.today,
                        onScaleChange = viewModel::setCalendarScale,
                        onDateSelected = viewModel::selectDate,
                        onShift = viewModel::shiftCalendar,
                        onToday = viewModel::goToToday,
                        onTaskClick = { onOpenTask(it.id) }
                    )

                    state.tasks.isEmpty() -> EmptyState(onlyStale = state.filter.onlyStale)

                    state.viewMode == TasksViewMode.LIST -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.tasks, key = { it.id }) { task ->
                            Column {
                                TaskRow(
                                    task = task,
                                    today = state.today,
                                    onComplete = { viewModel.complete(task) },
                                    onPostpone = { viewModel.postponeToTomorrow(task) },
                                    onClick = { onOpenTask(task.id) },
                                    expanded = task.id in state.expandedTaskIds,
                                    onExpandToggle = { viewModel.toggleExpanded(task.id) }
                                )
                                if (task.id in state.expandedTaskIds) {
                                    SubtaskList(
                                        task = task,
                                        onToggle = viewModel::complete
                                    )
                                }
                            }
                        }
                    }

                    else -> EisenhowerMatrix(
                        tasks = state.tasks,
                        today = state.today,
                        quadrantOf = { viewModel.quadrantOf(it, state.today) },
                        onMove = viewModel::moveToQuadrant,
                        onTaskClick = { onOpenTask(it.id) }
                    )
                }

                state.staleDialogTask?.let { task ->
                    StaleTaskDialog(
                        task = task,
                        onResolve = { resolution, payload ->
                            viewModel.resolveStale(task, resolution, payload)
                        },
                        onDismiss = viewModel::dismissStaleDialog
                    )
                }

                if (showProjects) {
                    ProjectsDialog(
                        projects = state.projects,
                        onCreate = viewModel::createProject,
                        onArchive = viewModel::archiveProject,
                        onDismiss = { showProjects = false }
                    )
                }
            }
        }
    }
}

private fun nextMode(mode: TasksViewMode): TasksViewMode = when (mode) {
    TasksViewMode.LIST -> TasksViewMode.MATRIX
    TasksViewMode.MATRIX -> TasksViewMode.CALENDAR
    TasksViewMode.CALENDAR -> TasksViewMode.LIST
}

@Composable
private fun EmptyState(onlyStale: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (onlyStale) "Залежавшихся задач нет" else "Пусто",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            if (onlyStale) "Сюда попадают задачи после трёх переносов"
            else "Добавьте задачу строкой снизу — дата, время, приоритет и теги разберутся сами",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
