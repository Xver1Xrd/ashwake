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
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import dev.ashwake.ui.components.AshTextField
import dev.ashwake.ui.components.PrimaryButton
import dev.ashwake.ui.components.ChipButton
import dev.ashwake.ui.components.TextAction
import dev.ashwake.ui.theme.AshTheme
import dev.ashwake.domain.engine.routines.SessionSummary
import dev.ashwake.platform.service.formatTime
import dev.ashwake.ui.theme.CounterLarge
import dev.ashwake.ui.theme.Ember
import dev.ashwake.ui.theme.Gold
import dev.ashwake.ui.theme.Moss
import androidx.compose.ui.res.stringResource
import dev.ashwake.R

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
            .background(AshTheme.colors.background)
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
                style = AshTheme.type.title3,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { viewModel.finish() }) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.routines_zakonchit))
            }
        }

        Text(
            "Шаг ${run.stepIndex + 1} из ${run.steps.size}",
            style = AshTheme.type.footnote,
            color = AshTheme.colors.text2
        )

        Box(contentAlignment = Alignment.Center) {
            StepRing(progress = run.progress.stepProgress)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatTime(run.progress.stepRemainingSeconds), style = CounterLarge)
                Text(
                    run.currentStep?.title.orEmpty(),
                    style = AshTheme.type.title3,
                    textAlign = TextAlign.Center
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChipButton(text = "−1", onClick = { viewModel.adjust(-1) })
            ChipButton(text = "−10", onClick = { viewModel.adjust(-10) })
            ChipButton(text = "+1", onClick = { viewModel.adjust(1) })
            ChipButton(text = "+10", onClick = { viewModel.adjust(10) })
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
            ChipButton(
                text = stringResource(R.string.routines_propustit),
                icon = Icons.Filled.SkipNext,
                onClick = { viewModel.skip() }
            )
        }

        TextAction(
            text = stringResource(R.string.routines_dobavit_shag),
            onClick = { showAddStep = true }
        )

        HorizontalDivider()

        // Превью следующего шага снизу — чтобы переход не был неожиданностью
        run.nextStep?.let { next ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AshTheme.colors.surface1)
                    .padding(12.dp)
            ) {
                Text(
                    "Дальше",
                    style = AshTheme.type.footnote,
                    color = AshTheme.colors.text2
                )
                Text(next.title, style = AshTheme.type.body)
                Text(
                    formatTime(next.plannedSeconds),
                    style = AshTheme.type.footnote,
                    color = AshTheme.colors.text2
                )
            }
        }

        Text(
            "Осталось всего: ${formatTime(run.progress.totalRemainingSeconds)}",
            style = AshTheme.type.footnote,
            color = AshTheme.colors.text2
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
    val track = AshTheme.colors.surface2
    val accent = AshTheme.colors.accent

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
        title = { Text(stringResource(R.string.routines_novyy_shag)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AshTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = stringResource(R.string.editor_nazvanie),
                    singleLine = true
                )
                AshTextField(
                    value = minutes,
                    onValueChange = { minutes = it },
                    label = stringResource(R.string.routines_minut),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextAction(
                text = stringResource(R.string.routines_dobavit),
                enabled = title.isNotBlank(),
                onClick = { onAdd(title, minutes.toIntOrNull() ?: 5) }
            )
        },
        dismissButton = { TextAction(text = stringResource(R.string.detail_otmena), onClick = onDismiss) }
    )
}

/** Итог сессии: план против факта по каждому заметному шагу (п. 6). */
@Composable
private fun SummaryDialog(summary: SessionSummary, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.routines_rutina_zakonchena)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "План ${formatTime(summary.plannedSeconds)} · факт ${formatTime(summary.actualSeconds)}",
                    style = AshTheme.type.callout
                )
                Text(
                    if (summary.overran)
                        "Дольше плана на ${formatTime(summary.deviationSeconds)}"
                    else "Быстрее плана на ${formatTime(-summary.deviationSeconds)}",
                    style = AshTheme.type.footnote,
                    color = if (summary.overran) Ember else Moss
                )
                if (summary.skippedSteps > 0) {
                    Text(
                        "Пропущено шагов: ${summary.skippedSteps}",
                        style = AshTheme.type.footnote,
                        color = AshTheme.colors.text2
                    )
                } else {
                    Text(
                        "Без пропусков",
                        style = AshTheme.type.footnote,
                        color = Gold
                    )
                }

                if (summary.notableSteps.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.routines_zametnye_otkloneniya), style = AshTheme.type.subhead)
                    summary.notableSteps.forEach { step ->
                        Text(
                            "${step.title}: план ${formatTime(step.plannedSeconds)}, факт ${formatTime(step.actualSeconds)}",
                            style = AshTheme.type.footnote,
                            color = AshTheme.colors.text2
                        )
                    }
                }
            }
        },
        confirmButton = { PrimaryButton(text = stringResource(R.string.editor_gotovo), onClick = onDismiss) }
    )
}
