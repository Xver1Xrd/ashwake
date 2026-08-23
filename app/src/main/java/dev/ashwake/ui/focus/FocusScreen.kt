package dev.ashwake.ui.focus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ashwake.ui.components.PrimaryButton
import dev.ashwake.ui.components.ChipButton
import dev.ashwake.ui.theme.AshTheme
import dev.ashwake.domain.engine.focus.phaseTitle
import dev.ashwake.domain.model.focus.FocusMode
import dev.ashwake.domain.model.focus.FocusPhase
import dev.ashwake.platform.service.formatTime
import dev.ashwake.ui.theme.CounterLarge
import dev.ashwake.ui.theme.Gold
import dev.ashwake.ui.theme.Moss
import androidx.compose.ui.res.stringResource
import dev.ashwake.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FocusScreen(viewModel: FocusViewModel = hiltViewModel()) {
    val run by viewModel.run.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()

    Scaffold(containerColor = AshTheme.colors.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (run.active) {
                Text(
                    phaseTitle(run.phase) +
                        if (run.mode == FocusMode.POMODORO) stringResource(R.string.focus_pomidor_1_s, run.pomodoroNumber)
                        else "",
                    style = AshTheme.type.title3,
                    color = if (run.phase == FocusPhase.WORK) Moss else Gold
                )

                Box(contentAlignment = Alignment.Center) {
                    TimerRing(progress = run.progress)
                    Text(
                        formatTime(
                            if (run.mode == FocusMode.STOPWATCH) run.elapsedSeconds
                            else run.remainingSeconds
                        ),
                        style = CounterLarge
                    )
                }

                run.taskTitle?.let { title ->
                    Text(
                        title,
                        style = AshTheme.type.callout,
                        color = AshTheme.colors.text2
                    )
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
                            contentDescription = if (run.running) stringResource(R.string.detail_pauza) else stringResource(R.string.focus_prodolzhit)
                        )
                    }
                    if (run.mode == FocusMode.POMODORO) {
                        ChipButton(
                            text = stringResource(R.string.focus_dalshe),
                            icon = Icons.Filled.SkipNext,
                            onClick = viewModel::skipPhase
                        )
                    }
                    ChipButton(
                        text = stringResource(R.string.focus_stop),
                        icon = Icons.Filled.Stop,
                        onClick = viewModel::stop
                    )
                }

                if (run.completedPomodoros > 0) {
                    Text(
                        stringResource(R.string.focus_pomidorov_za_sessiyu_1_s, run.completedPomodoros),
                        style = AshTheme.type.footnote,
                        color = AshTheme.colors.text2
                    )
                }
            } else {
                Text(
                    "${config.workMinutes} / ${config.breakMinutes}",
                    style = AshTheme.type.title1
                )
                Text(
                    stringResource(R.string.focus_dlinnyy_pereryv_1_s_min_kazhdye_2_s_pomidora, config.longBreakMinutes, config.longBreakEvery),
                    style = AshTheme.type.footnote,
                    color = AshTheme.colors.text2
                )

                Text(stringResource(R.string.focus_dlitelnost_raboty), style = AshTheme.type.subhead)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(15, 25, 30, 45, 50, 90).forEach { minutes ->
                        ChipButton(
                        text = "$minutes",
                        selected = config.workMinutes == minutes,
                        onClick = { viewModel.setWorkMinutes(minutes) }
                    )
                    }
                }

                if (tasks.isNotEmpty()) {
                    Text(stringResource(R.string.focus_privyazat_k_zadache), style = AshTheme.type.subhead)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ChipButton(
                        text = stringResource(R.string.focus_bez_zadachi),
                        selected = viewModel.selectedTaskId == null,
                        onClick = { viewModel.selectTask(null) }
                    )
                        tasks.take(8).forEach { task ->
                            ChipButton(
                        text = task.title.take(24),
                        selected = viewModel.selectedTaskId == task.id,
                        onClick = { viewModel.selectTask(task.id) }
                    )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrimaryButton(
                        text = stringResource(R.string.focus_pomodoro),
                        onClick = { viewModel.start(FocusMode.POMODORO) }
                    )
                    ChipButton(
                        text = stringResource(R.string.focus_sekundomer),
                        onClick = { viewModel.start(FocusMode.STOPWATCH) }
                    )
                }
            }

            HorizontalDivider()

            Text(stringResource(R.string.focus_statistika_za_30_dney), style = AshTheme.type.headline)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Metric(formatTime(stats.totalSeconds.toInt()), stringResource(R.string.focus_vsego))
                Metric("${stats.sessions}", stringResource(R.string.focus_sessiy))
                Metric(
                    if (stats.sessions > 0)
                        formatTime((stats.totalSeconds / stats.sessions).toInt())
                    else "—",
                    stringResource(R.string.focus_v_srednem)
                )
            }

            WeekdayChart(stats.byWeekday)
        }
    }
}

@Composable
private fun TimerRing(progress: Float) {
    val track = AshTheme.colors.surface2
    val accent = AshTheme.colors.accent

    Canvas(modifier = Modifier.size(240.dp)) {
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
private fun Metric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = AshTheme.type.title3)
        Text(
            label,
            style = AshTheme.type.footnote,
            color = AshTheme.colors.text2
        )
    }
}

/** Столбики по дням недели: где фокус проседает, видно сразу. */
@Composable
private fun WeekdayChart(byWeekday: Map<Int, Long>) {
    val labels = listOf(stringResource(R.string.components_pn), stringResource(R.string.components_vt), stringResource(R.string.components_sr), stringResource(R.string.components_cht), stringResource(R.string.components_pt), stringResource(R.string.components_sb), stringResource(R.string.components_vs))
    val max = byWeekday.values.maxOrNull() ?: 0L
    if (max == 0L) return
    val accent = AshTheme.colors.accent
    val track = AshTheme.colors.surface2

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        (1..7).forEach { day ->
            val seconds = byWeekday[day] ?: 0L
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    labels[day - 1],
                    style = AshTheme.type.footnote,
                    color = AshTheme.colors.text2,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Canvas(modifier = Modifier.weight(1f).size(height = 12.dp, width = 1.dp)) {
                    drawRect(color = track, size = Size(size.width, size.height))
                    drawRect(
                        color = accent,
                        size = Size(size.width * (seconds.toFloat() / max), size.height)
                    )
                }
                Text(
                    "  ${formatTime(seconds.toInt())}",
                    style = AshTheme.type.footnote,
                    color = AshTheme.colors.text2
                )
            }
        }
    }
}
