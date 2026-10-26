package dev.ashwake.ui.tasks

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import dev.ashwake.ui.components.ChipButton
import dev.ashwake.ui.theme.AshShapes
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
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
import dev.ashwake.ui.tasks.components.TaskDayHeader
import dev.ashwake.ui.tasks.components.TaskFilterRow
import dev.ashwake.ui.tasks.components.TaskRow
import androidx.compose.foundation.layout.navigationBarsPadding
import dev.ashwake.ui.components.PrimaryButton
import dev.ashwake.ui.components.SecondaryButton
import dev.ashwake.ui.components.TextAction
import androidx.compose.material3.AlertDialog
import java.time.format.DateTimeFormatter
import java.util.Locale
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
    val colors = AshTheme.colors
    val haptics = dev.ashwake.ui.theme.rememberHaptics()
    var showProjects by remember { mutableStateOf(false) }
    val toast = rememberToastState()
    val postponedText = stringResource(R.string.toast_postponed)
    val undoText = stringResource(R.string.toast_undo)
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedTaskIds by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteSelectedConfirm by remember { mutableStateOf(false) }
    var viewBeforeSelection by remember { mutableStateOf<TasksViewMode?>(null) }
    var showHeaderMenu by remember { mutableStateOf(false) }
    val compactHeader = LocalConfiguration.current.screenWidthDp < 400 || LocalDensity.current.fontScale > 1.3f
    val visibleTaskIds = state.tasks.map { it.id }.toSet()

    fun startSelection(ids: Set<Long> = emptySet()) {
        viewBeforeSelection = state.viewMode.takeIf { it != TasksViewMode.LIST }
        if (state.viewMode != TasksViewMode.LIST) viewModel.setViewMode(TasksViewMode.LIST)
        selectedTaskIds = ids
        isSelectionMode = true
    }

    fun finishSelection() {
        isSelectionMode = false
        selectedTaskIds = emptySet()
        viewBeforeSelection?.let(viewModel::setViewMode)
        viewBeforeSelection = null
    }

    // Не выполнять массовые действия над задачами, скрытыми новым фильтром.
    LaunchedEffect(visibleTaskIds) {
        selectedTaskIds = selectedTaskIds.intersect(visibleTaskIds)
    }

    androidx.activity.compose.BackHandler(enabled = isSelectionMode) {
        finishSelection()
    }

    // Микрофон работает только с разрешением: спрашиваем в момент нажатия,
    // а не при запуске приложения
    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.startVoiceInput() }
    val context = LocalContext.current

    Scaffold(
        containerColor = AshTheme.colors.background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            if (isSelectionMode) {
                AshLargeTitle(
                    title = "Выбрано: ${selectedTaskIds.size}",
                    actions = {
                        TextAction(
                            text = if (visibleTaskIds.isNotEmpty() && selectedTaskIds.containsAll(visibleTaskIds)) "Снять" else "Все",
                            onClick = {
                                selectedTaskIds = toggleVisibleSelection(selectedTaskIds, visibleTaskIds)
                            }
                        )
                        TextAction(
                            text = "Готово",
                            onClick = {
                                finishSelection()
                            }
                        )
                    }
                )
            } else {
                AshLargeTitle(
                    title = stringResource(R.string.tasks_zadachi),
                    actions = {
                        if (compactHeader) {
                            Box {
                                IconAction(
                                    icon = Icons.Filled.MoreVert,
                                    contentDescription = "Дополнительные действия",
                                    tint = AshTheme.colors.text2,
                                    onClick = { showHeaderMenu = true }
                                )
                                DropdownMenu(expanded = showHeaderMenu, onDismissRequest = { showHeaderMenu = false }) {
                                    DropdownMenuItem(text = { Text("Выбрать и поделиться") }, onClick = {
                                        showHeaderMenu = false; startSelection()
                                    })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.ritual_vecherniy_ritual)) }, onClick = {
                                        showHeaderMenu = false; onOpenRitual()
                                    })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.stats_statistika)) }, onClick = {
                                        showHeaderMenu = false; onOpenStats()
                                    })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.tasks_zalezhavshiesya)) }, onClick = {
                                        showHeaderMenu = false; viewModel.toggleStaleFilter()
                                    })
                                }
                            }
                        } else {
                        IconAction(
                            icon = AshIcons.Export,
                            contentDescription = "Выбрать и поделиться",
                            tint = AshTheme.colors.text2,
                            onClick = { startSelection() }
                        )
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
                        }
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
            }
        },
        bottomBar = {
            if (isSelectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AshTheme.colors.surface1)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SecondaryButton(
                        text = "Удалить (${selectedTaskIds.size})",
                        enabled = selectedTaskIds.isNotEmpty(),
                        onClick = { showDeleteSelectedConfirm = true },
                        modifier = Modifier.weight(1f)
                    )
                    PrimaryButton(
                        text = "Поделиться (${selectedTaskIds.size})",
                        enabled = selectedTaskIds.isNotEmpty(),
                        onClick = {
                            val selectedTasks = state.tasks.filter { it.id in selectedTaskIds }
                            val dateText = state.today.format(DateTimeFormatter.ofPattern("d MMMM", Locale("ru")))
                            viewModel.shareTasksAsImage(selectedTasks, "Задачи · $dateText") { shareIntent ->
                                context.startActivity(
                                    android.content.Intent.createChooser(shareIntent, "Поделиться задачами")
                                )
                                finishSelection()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
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
                    },
                    // Быстрый ввод располагается над глобальным островом.
                    // Поэтому его фон не оказывается серой полосой под ним.
                    modifier = Modifier.padding(bottom = 84.dp)
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
        Column(Modifier.fillMaxSize()) {
            if (state.viewMode == TasksViewMode.LIST || state.viewMode == TasksViewMode.MATRIX) {
                TaskFilterRow(
                    filter = state.filter,
                    tasks = state.tasks,
                    tags = state.tags,
                    onToggleDone = viewModel::toggleShowDone,
                    onTagSelected = viewModel::setTagFilter,
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
                        state.dayGroups.forEach { group ->
                            item(key = "header-${group.title}") {
                                TaskDayHeader(
                                    group = group,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                                )
                            }

                            items(group.tasks, key = { it.id }) { task ->
                                Column {
                                    TaskRow(
                                        task = task,
                                        today = state.today,
                                        onComplete = {
                                            if (!task.isDone) haptics.play(dev.ashwake.ui.theme.HapticKind.TASK_COMPLETE)
                                            viewModel.complete(task)
                                        },
                                        onPostpone = {
                                            viewModel.postponeToTomorrow(task)
                                            toast.show(postponedText, undoText) {
                                                viewModel.undoPostpone(task.id)
                                            }
                                        },
                                        onClick = { onOpenTask(task.id) },
                                        expanded = task.id in state.expandedTaskIds,
                                        onExpandToggle = if (isSelectionMode) null else { { viewModel.toggleExpanded(task.id) } },
                                        selectionMode = isSelectionMode,
                                        selected = task.id in selectedTaskIds,
                                        onSelectToggle = {
                                            selectedTaskIds = if (task.id in selectedTaskIds) {
                                                selectedTaskIds - task.id
                                            } else {
                                                selectedTaskIds + task.id
                                            }
                                        },
                                        onLongClick = {
                                            if (!isSelectionMode) {
                                                startSelection(setOf(task.id))
                                            }
                                        },
                                        onDelete = { viewModel.delete(task) }
                                    )
                                    if (!isSelectionMode && task.id in state.expandedTaskIds) {
                                        val subtasks by viewModel.observeSubtasks(task.id)
                                            .collectAsStateWithLifecycle(initialValue = emptyList())
                                        SubtaskList(
                                            subtasks = subtasks,
                                            onToggle = viewModel::complete
                                        )
                                    }
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

                if (showDeleteSelectedConfirm) {
                    AlertDialog(
                        onDismissRequest = { showDeleteSelectedConfirm = false },
                        title = { Text("Удалить выбранные задачи?") },
                        text = { Text("Будет удалено задач: ${selectedTaskIds.size}. Вы уверены?") },
                        confirmButton = {
                            TextAction(
                                text = stringResource(R.string.blocking_udalit),
                                color = AshTheme.colors.danger,
                                onClick = {
                                    val idsToDelete = selectedTaskIds.intersect(visibleTaskIds)
                                    val count = idsToDelete.size
                                    viewModel.deleteTasks(idsToDelete)
                                    finishSelection()
                                    showDeleteSelectedConfirm = false
                                    toast.show("Удалено задач: $count")
                                }
                            )
                        },
                        dismissButton = {
                            TextAction(
                                text = stringResource(R.string.detail_otmena),
                                onClick = { showDeleteSelectedConfirm = false }
                            )
                        }
                    )
                }
            }
        }
            ToastHost(toast)
        }
    }
}

internal fun toggleVisibleSelection(selected: Set<Long>, visible: Set<Long>): Set<Long> =
    if (visible.isNotEmpty() && selected.containsAll(visible)) selected - visible else selected + visible

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
            if (onlyStale) stringResource(R.string.tasks_zalezhavshihsya_zadach_net) else stringResource(R.string.tasks_pusto),
            style = AshTheme.type.title3
        )
        Text(
            if (onlyStale) stringResource(R.string.tasks_syuda_popadayut_zadachi_posle_treh_perenosov)
            else stringResource(R.string.tasks_dobavte_zadachu_strokoy_snizu_data_vremya_pr),
            style = AshTheme.type.callout,
            color = AshTheme.colors.text2,
            textAlign = TextAlign.Center
        )
    }
}
