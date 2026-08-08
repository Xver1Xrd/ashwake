package dev.ashwake.ui.routines

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ashwake.domain.engine.routines.SessionSummary
import dev.ashwake.platform.service.formatTime
import dev.ashwake.ui.theme.CounterLarge
import dev.ashwake.ui.theme.Ember
import dev.ashwake.ui.theme.Gold
import dev.ashwake.ui.theme.Moss

/**
 * Полноэкранный режим выполнения рутины (п. 6).
 *
 * Обратный отсчёт по текущему шагу, превью следующего снизу, автопереход.
 * Крупная цифра и минимум прочего: экран смотрят краем глаза во время дела.
 */
@Composable
fun RoutineRunScreen(
    onExit: () -> Unit,
    viewModel: RoutinesViewModel = hiltViewModel()
) {
    val run by viewModel.run.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    var showAddStep by remember { mutableStateOf(false) }

    LaunchedEffect(run.finished) {
        if (run.finished && summary == null) viewModel.onRunFinished()
    }

    summary?.let { data ->
        SummaryDialog(
            summary = data,
            onDismiss = {
                viewModel.dismissSummary()
                onExit()
            }
        )
        return
    }

    if (run.routine == null) {
        LaunchedEffect(Unit) { onExit() }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                run.routine?.name.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { viewModel.finish() }) {
                Icon(Icons.Filled.Close, contentDescription = "Закончить")
            }
        }

        Text(
            "Шаг ${run.stepIndex + 1} из ${run.steps.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Box(contentAlignment = Alignment.Center) {
            StepRing(progress = run.progress.stepProgress)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatTime(run.progress.stepRemainingSeconds), style = CounterLarge)
                Text(
                    run.currentStep?.title.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { viewModel.adjust(-1) }) { Text("−1") }
            OutlinedButton(onClick = { viewModel.adjust(-10) }) { Text("−10") }
            OutlinedButton(onClick = { viewModel.adjust(1) }) { Text("+1") }
            OutlinedButton(onClick = { viewModel.adjust(10) }) { Text("+10") }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = { if (run.running) viewModel.pause() else viewModel.resume() },
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    if (run.running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (run.running) "Пауза" else "Продолжить"
                )
            }
            OutlinedButton(onClick = { viewModel.skip() }) {
                Icon(Icons.Filled.SkipNext, contentDescription = null)
                Text("  Пропустить")
            }
        }

        TextButton(onClick = { showAddStep = true }) { Text("Добавить шаг") }

        HorizontalDivider()

        // Превью следующего шага снизу — чтобы переход не был неожиданностью
        run.nextStep?.let { next ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp)
            ) {
                Text(
                    "Дальше",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(next.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    formatTime(next.plannedSeconds),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            "Осталось всего: ${formatTime(run.progress.totalRemainingSeconds)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showAddStep) {
        AddStepDialog(
            onAdd = { title, minutes ->
                viewModel.addStep(title, minutes)
                showAddStep = false
            },
            onDismiss = { showAddStep = false }
        )
    }
}

@Composable
private fun StepRing(progress: Float) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val accent = MaterialTheme.colorScheme.primary

    Canvas(modifier = Modifier.size(260.dp)) {
        val stroke = 12.dp.toPx()
        val diameter = size.minDimension - stroke
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)

        drawArc(
            color = track, startAngle = -90f, sweepAngle = 360f, useCenter = false,
            topLeft = topLeft, size = Size(diameter, diameter), style = Stroke(width = stroke)
        )
        drawArc(
            color = accent, startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
            topLeft = topLeft, size = Size(diameter, diameter), style = Stroke(width = stroke)
        )
    }
}

@Composable
private fun AddStepDialog(onAdd: (String, Int) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("5") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый шаг") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it },
                    label = { Text("Минут") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = { onAdd(title, minutes.toIntOrNull() ?: 5) }
            ) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

/** Итог сессии: план против факта по каждому заметному шагу (п. 6). */
@Composable
private fun SummaryDialog(summary: SessionSummary, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Рутина закончена") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "План ${formatTime(summary.plannedSeconds)} · " +
                        "факт ${formatTime(summary.actualSeconds)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    if (summary.overran)
                        "Дольше плана на ${formatTime(summary.deviationSeconds)}"
                    else "Быстрее плана на ${formatTime(-summary.deviationSeconds)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (summary.overran) Ember else Moss
                )
                if (summary.skippedSteps > 0) {
                    Text(
                        "Пропущено шагов: ${summary.skippedSteps}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "Без пропусков",
                        style = MaterialTheme.typography.labelMedium,
                        color = Gold
                    )
                }

                if (summary.notableSteps.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text("Заметные отклонения", style = MaterialTheme.typography.labelLarge)
                    summary.notableSteps.forEach { step ->
                        Text(
                            "${step.title}: план ${formatTime(step.plannedSeconds)}, " +
                                "факт ${formatTime(step.actualSeconds)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Готово") } }
    )
}
