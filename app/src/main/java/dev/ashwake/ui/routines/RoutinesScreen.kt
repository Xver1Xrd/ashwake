package dev.ashwake.ui.routines

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ashwake.platform.service.formatTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutinesScreen(
    onRun: () -> Unit,
    viewModel: RoutinesViewModel = hiltViewModel()
) {
    val routines by viewModel.list.collectAsStateWithLifecycle()
    val run by viewModel.run.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    var showPresets by remember { mutableStateOf(false) }

    // Прогон открывается на весь экран, как только начался
    LaunchedEffect(run.active) { if (run.active) onRun() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Рутины") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showPresets = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Добавить рутину")
            }
        }
    ) { padding ->
        if (routines.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Рутин пока нет", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Рутина — это список шагов с таймером: запустил и не думаешь, " +
                        "что дальше",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(routines, key = { it.id }) { routine ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { viewModel.start(routine) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(routine.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${routine.steps.size} шагов · ${formatTime(routine.plannedSeconds)}" +
                                    (routine.startTime?.let { " · в $it" } ?: ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { viewModel.start(routine) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Запустить")
                        }
                    }
                }
            }
        }

        if (showPresets) {
            AlertDialog(
                onDismissRequest = { showPresets = false },
                title = { Text("Шаблоны рутин") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        presets.forEach { preset ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.createFromPreset(preset)
                                        showPresets = false
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(preset.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${preset.steps.size} шагов · ${formatTime(preset.totalSeconds)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPresets = false }) { Text("Закрыть") }
                }
            )
        }
    }
}

/** Пустой контейнер под будущий редактор рутины: правка шагов — следующий шаг этапа. */
@Composable
internal fun RoutinePlaceholder(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
