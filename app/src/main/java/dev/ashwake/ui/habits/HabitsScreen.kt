package dev.ashwake.ui.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ashwake.domain.model.habits.HabitWithProgress
import dev.ashwake.ui.habits.components.HabitActionsSheet
import dev.ashwake.ui.habits.components.HabitCard
import dev.ashwake.ui.habits.components.HabitCatalogDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(
    onOpenHabit: (Long) -> Unit,
    onCreateHabit: () -> Unit,
    viewModel: HabitsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var sheetTarget by remember { mutableStateOf<HabitWithProgress?>(null) }
    var showCatalog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Привычки") },
                actions = {
                    IconButton(onClick = viewModel::toggleVacation) {
                        Icon(
                            Icons.Filled.BeachAccess,
                            contentDescription = "Режим отпуска",
                            tint = if (state.vacationMode) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCatalog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Добавить привычку")
            }
        }
    ) { padding ->
        if (state.habits.isEmpty()) {
            EmptyState(Modifier.padding(padding))
            if (showCatalog) {
                HabitCatalogDialog(
                    categories = presets,
                    onPick = { viewModel.addFromPreset(it); showCatalog = false },
                    onCreateOwn = { showCatalog = false; onCreateHabit() },
                    onDismiss = { showCatalog = false }
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.vacationMode) {
                item {
                    Text(
                        "Режим отпуска: дни не считаются пропусками",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            items(state.dueToday, key = { it.habit.id }) { progress ->
                HabitCard(
                    progress = progress,
                    onPrimaryAction = { viewModel.onPrimaryAction(progress) },
                    onMinimum = { viewModel.markMinimum(progress) },
                    onLongClick = { sheetTarget = progress },
                    onOpenDetail = { onOpenHabit(progress.habit.id) }
                )
            }

            if (state.restToday.isNotEmpty()) {
                item {
                    Text(
                        "Не на сегодня",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                items(state.restToday, key = { "rest-${it.habit.id}" }) { progress ->
                    HabitCard(
                        progress = progress,
                        onPrimaryAction = { viewModel.onPrimaryAction(progress) },
                        onMinimum = { viewModel.markMinimum(progress) },
                        onLongClick = { sheetTarget = progress },
                        onOpenDetail = { onOpenHabit(progress.habit.id) }
                    )
                }
            }
        }

        sheetTarget?.let { target ->
            HabitActionsSheet(
                progress = target,
                skipReasons = state.skipReasons,
                onSetCounter = { viewModel.setCounterValue(target, it) },
                onNote = { viewModel.setNote(target, it); sheetTarget = null },
                onSkip = { viewModel.markSkipped(target, it); sheetTarget = null },
                onFreeze = {
                    scope.launch {
                        val ok = viewModel.freezeToday(target)
                        sheetTarget = null
                        if (!ok) snackbar.showSnackbar("Заморозки на этот месяц закончились")
                    }
                },
                onPause = { viewModel.pauseHabit(target, null); sheetTarget = null },
                onResume = { viewModel.resumeHabit(target); sheetTarget = null },
                onEdit = { sheetTarget = null; onOpenHabit(target.habit.id) },
                onArchive = { viewModel.archive(target); sheetTarget = null },
                onClear = { viewModel.clearMark(target); sheetTarget = null },
                onDismiss = { sheetTarget = null }
            )
        }

        if (showCatalog) {
            HabitCatalogDialog(
                categories = presets,
                onPick = { viewModel.addFromPreset(it); showCatalog = false },
                onCreateOwn = { showCatalog = false; onCreateHabit() },
                onDismiss = { showCatalog = false }
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Привычек пока нет", style = MaterialTheme.typography.titleMedium)
        Text(
            "Возьмите готовую из каталога или заведите свою. " +
                "Score растёт постепенно и не обнуляется от пары пропусков",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
