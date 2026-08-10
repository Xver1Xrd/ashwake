package dev.ashwake.ui.tasks

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.ashwake.platform.speech.VoiceResult
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ashwake.ui.tasks.calendar.TaskCalendar
import dev.ashwake.ui.timebox.TimeboxScreen
import dev.ashwake.ui.tasks.components.ProjectsDialog
import dev.ashwake.ui.components.AshIcons
import dev.ashwake.ui.components.AshLargeTitle
import dev.ashwake.ui.components.IconAction
import dev.ashwake.ui.components.ToastHost
import dev.ashwake.ui.components.rememberToastState
import dev.ashwake.ui.theme.AshTheme
import dev.ashwake.ui.tasks.components.QuickAddBar
import dev.ashwake.ui.tasks.components.StaleTaskDialog
import dev.ashwake.ui.tasks.components.SubtaskList
import dev.ashwake.ui.tasks.components.TaskFilterRow
import dev.ashwake.ui.tasks.components.TaskRow
import androidx.compose.ui.res.stringResource
import dev.ashwake.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    onOpenTask: (Long) -> Unit,
    onOpenRitual: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    viewModel: TasksViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val calendar by viewModel.calendarState.collectAsStateWithLifecycle()
    val voice by viewModel.voiceState.collectAsStateWithLifecycle()
    var showProjects by remember { mutableStateOf(false) }
    val toast = rememberToastState()

    // Микрофон работает только с разрешением: спрашиваем в момент нажатия,
    // а не при запуске приложения
    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.startVoiceInput() }
    val context = LocalContext.current

    Scaffold(
        containerColor = AshTheme.colors.background,
        topBar = {
            AshLargeTitle(
                title = stringResource(R.string.tasks_zadachi),
                actions = {
                    IconAction(
                        icon = AshIcons.Moon,
                        contentDescription = stringResource(R.string.ritual_vecherniy_ritual),
                        tint = AshTheme.colors.text2,
                        onClick = onOpenRitual
                    )
                    IconAction(
                        icon = AshIcons.BarChart,
                        contentDescription = stringResource(R.string.stats_statistika),
                        tint = AshTheme.colors.text2,
                        onClick = onOpenStats
                    )
                    IconAction(
                        icon = AshIcons.Hourglass,
                        contentDescription = stringResource(R.string.tasks_zalezhavshiesya),
                        tint = if (state.filter.onlyStale) AshTheme.colors.accent
                        else AshTheme.colors.text2,
                        onClick = viewModel::toggleStaleFilter
                    )
                    IconAction(
                        icon = when (state.viewMode) {
                            TasksViewMode.LIST -> AshIcons.GridView
                            TasksViewMode.MATRIX -> AshIcons.CalendarMonth
                            TasksViewMode.CALENDAR -> AshIcons.ViewTimeline
                            TasksViewMode.TIMEBOX -> AshIcons.ListIcon
                        },
                        contentDescription = stringResource(R.string.tasks_rezhim_otobrazheniya),
                        tint = AshTheme.colors.text2,
                        onClick = { viewModel.setViewMode(nextMode(state.viewMode)) }
                    )
                }
            )
        },
        bottomBar = {
            QuickAddBar(
                value = state.quickInput,
                parsed = state.parsed,
                onValueChange = viewModel::onQuickInputChange,
                onSubmit = viewModel::submitQuickInput,
                listening = voice is VoiceResult.Listening || voice is VoiceResult.Partial,
                onVoiceClick = {
                    val granted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) viewModel.startVoiceInput()
                    else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
        Column(Modifier.fillMaxSize()) {
            if (state.viewMode == TasksViewMode.LIST || state.viewMode == TasksViewMode.MATRIX) {
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
                    state.viewMode == TasksViewMode.TIMEBOX -> TimeboxScreen()

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
                                    onPostpone = {
                                        viewModel.postponeToTomorrow(task)
                                        toast.show("Перенесено на завтра", "Отменить") {
                                            viewModel.undoPostpone(task.id)
                                        }
                                    },
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
            ToastHost(toast)
        }
    }
}

private fun nextMode(mode: TasksViewMode): TasksViewMode = when (mode) {
    TasksViewMode.LIST -> TasksViewMode.MATRIX
    TasksViewMode.MATRIX -> TasksViewMode.CALENDAR
    TasksViewMode.CALENDAR -> TasksViewMode.TIMEBOX
    TasksViewMode.TIMEBOX -> TasksViewMode.LIST
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
            style = AshTheme.type.title3
        )
        Text(
            if (onlyStale) "Сюда попадают задачи после трёх переносов"
            else "Добавьте задачу строкой снизу — дата, время, приоритет и теги разберутся сами",
            style = AshTheme.type.callout,
            color = AshTheme.colors.text2,
            textAlign = TextAlign.Center
        )
    }
}
