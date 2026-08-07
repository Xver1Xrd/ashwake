package dev.ashwake.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ashwake.ui.tasks.components.QuickAddBar
import dev.ashwake.ui.tasks.components.StaleTaskDialog
import dev.ashwake.ui.tasks.components.TaskRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(viewModel: TasksViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Задачи") },
                actions = {
                    IconButton(onClick = { viewModel.toggleStaleFilter() }) {
                        Icon(
                            Icons.Filled.HourglassBottom,
                            contentDescription = "Залежавшиеся",
                            tint = if (state.filter.onlyStale) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel.setViewMode(
                                if (state.viewMode == TasksViewMode.LIST) TasksViewMode.MATRIX
                                else TasksViewMode.LIST
                            )
                        }
                    ) {
                        Icon(
                            if (state.viewMode == TasksViewMode.LIST) Icons.Filled.GridView
                            else Icons.AutoMirrored.Filled.List,
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
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.tasks.isEmpty() -> EmptyState(onlyStale = state.filter.onlyStale)

                state.viewMode == TasksViewMode.LIST -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.tasks, key = { it.id }) { task ->
                        TaskRow(
                            task = task,
                            today = state.today,
                            onComplete = { viewModel.complete(task) },
                            onPostpone = { viewModel.postponeToTomorrow(task) },
                            onClick = { /* редактор задачи — следующий шаг этапа 1 */ }
                        )
                    }
                }

                else -> EisenhowerMatrix(
                    tasks = state.tasks,
                    today = state.today,
                    quadrantOf = { viewModel.quadrantOf(it, state.today) },
                    onMove = viewModel::moveToQuadrant,
                    onTaskClick = { }
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
        }
    }
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
