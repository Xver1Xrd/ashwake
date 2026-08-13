package dev.ashwake.ui.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import dev.ashwake.ui.components.AshIcons
import dev.ashwake.ui.components.AshLargeTitle
import dev.ashwake.ui.components.IconAction
import dev.ashwake.ui.theme.AshTheme
import dev.ashwake.ui.habits.components.HabitCard
import dev.ashwake.ui.habits.components.HabitCatalogDialog
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import dev.ashwake.R

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
    // Текст читается в композиции: показывают его из корутины,
    // а туда `stringResource` не дотянется
    val freezesSpent = stringResource(R.string.habits_freezes_spent)
    val scope = rememberCoroutineScope()

    var sheetTarget by remember { mutableStateOf<HabitWithProgress?>(null) }
    var showCatalog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = AshTheme.colors.background,
        topBar = {
            AshLargeTitle(
                title = stringResource(R.string.habits_privychki),
                actions = {
                    IconAction(
                        icon = AshIcons.BeachAccess,
                        contentDescription = stringResource(R.string.habits_rezhim_otpuska),
                        tint = if (state.vacationMode) AshTheme.colors.accent
                        else AshTheme.colors.text2,
                        onClick = viewModel::toggleVacation
                    )
                    IconAction(
                        icon = AshIcons.Add,
                        contentDescription = stringResource(R.string.habits_dobavit_privychku),
                        onClick = { showCatalog = true }
                    )
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
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
                        stringResource(R.string.habits_rezhim_otpuska_dni_ne_schitayutsya_propuskam),
                        style = AshTheme.type.footnote,
                        color = AshTheme.colors.warm
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
                        stringResource(R.string.habits_ne_na_segodnya),
                        style = AshTheme.type.subhead,
                        color = AshTheme.colors.text2,
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
                        if (!ok) snackbar.showSnackbar(freezesSpent)
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
        Text(stringResource(R.string.habits_privychek_poka_net), style = AshTheme.type.title3)
        Text(
            stringResource(R.string.habits_vozmite_gotovuyu_iz_kataloga_ili_zavedite_sv),
            style = AshTheme.type.callout,
            color = AshTheme.colors.text2,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
