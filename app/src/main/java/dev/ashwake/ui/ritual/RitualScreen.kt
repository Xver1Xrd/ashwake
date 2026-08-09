package dev.ashwake.ui.ritual

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ashwake.domain.model.habits.HabitWithProgress
import dev.ashwake.domain.model.tasks.Task
import dev.ashwake.ui.theme.Moss
import java.time.format.DateTimeFormatter
import androidx.compose.ui.res.stringResource
import dev.ashwake.R

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM")

/**
 * Вечерний ритуал: пять шагов примерно за две минуты (п. 9).
 *
 * Каждый шаг помещается на экран целиком и имеет кнопку «дальше» —
 * ритуал должен закрываться быстрее, чем надоедает.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RitualScreen(
    onDone: () -> Unit,
    viewModel: RitualViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val step by viewModel.step.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val date by viewModel.date.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()

    LaunchedEffect(finished) {
        if (finished) {
            viewModel.reset()
            onDone()
        }
    }

    val stepIndex = RitualStep.entries.indexOf(step)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.ritual_vecherniy_ritual))
                        Text(
                            if (state.isCatchUp) "за ${date.format(DATE_FORMAT)}"
                            else date.format(DATE_FORMAT),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (stepIndex == 0) onDone() else viewModel.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.detail_nazad))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LinearProgressIndicator(
                progress = { (stepIndex + 1f) / RitualStep.entries.size },
                modifier = Modifier.fillMaxWidth()
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (step) {
                    RitualStep.SCALES -> ScalesStep(form, viewModel)
                    RitualStep.TASKS -> TasksStep(state.openTasks, viewModel)
                    RitualStep.HABITS -> HabitsStep(state.unmarkedHabits, viewModel)
                    RitualStep.TOMORROW -> TomorrowStep(state.tomorrowCandidates, form, viewModel)
                    RitualStep.NOTE -> NoteStep(form, viewModel)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (stepIndex > 0) {
                    OutlinedButton(onClick = viewModel::back) { Text(stringResource(R.string.detail_nazad)) }
                }
                Button(onClick = viewModel::next, modifier = Modifier.weight(1f)) {
                    Text(if (step == RitualStep.NOTE) "Закончить" else "Дальше")
                }
            }
        }
    }
}

@Composable
private fun ScalesStep(form: RitualForm, viewModel: RitualViewModel) {
    Text(stringResource(R.string.ritual_kak_proshel_den), style = MaterialTheme.typography.titleMedium)
    ScaleRow("Оценка дня", form.dayRating, viewModel::setDayRating)

    HorizontalDivider()
    Text(
        "Настроение и энергия — отдельно: связи с привычками ищутся по ним",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    ScaleRow("Настроение", form.mood, viewModel::setMood)
    ScaleRow("Энергия", form.energy, viewModel::setEnergy)
}

@Composable
private fun ScaleRow(label: String, value: Int?, onSelect: (Int) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { level ->
                FilterChip(
                    selected = value == level,
                    onClick = { onSelect(level) },
                    label = { Text("$level") }
                )
            }
        }
    }
}

@Composable
private fun TasksStep(tasks: List<Task>, viewModel: RitualViewModel) {
    Text(stringResource(R.string.ritual_nezakrytye_zadachi), style = MaterialTheme.typography.titleMedium)

    if (tasks.isEmpty()) {
        EmptyHint("Всё закрыто")
        return
    }

    TextButton(onClick = { viewModel.postponeAll(tasks) }) {
        Text(stringResource(R.string.ritual_perenesti_vse_na_zavtra))
    }

    tasks.take(12).forEach { task ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                task.title,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = { viewModel.postponeToTomorrow(task) }) { Text(stringResource(R.string.ritual_zavtra)) }
            TextButton(onClick = { viewModel.drop(task) }) { Text(stringResource(R.string.ritual_udalit)) }
        }
    }
}

@Composable
private fun HabitsStep(habits: List<HabitWithProgress>, viewModel: RitualViewModel) {
    Text(stringResource(R.string.ritual_neprostavlennye_privychki), style = MaterialTheme.typography.titleMedium)

    if (habits.isEmpty()) {
        EmptyHint("Все привычки отмечены")
        return
    }

    habits.forEach { progress ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                progress.habit.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = { viewModel.markDone(progress) }) { Text(stringResource(R.string.ritual_sdelal)) }
            TextButton(onClick = { viewModel.markSkipped(progress) }) { Text(stringResource(R.string.ritual_propustil)) }
        }
    }
}

@Composable
private fun TomorrowStep(
    candidates: List<Task>,
    form: RitualForm,
    viewModel: RitualViewModel
) {
    Text(stringResource(R.string.ritual_tri_glavnye_zadachi_na_zavtra), style = MaterialTheme.typography.titleMedium)
    Text(
        "Выбрано ${form.topTaskIds.size} из 3",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    candidates.take(15).forEach { task ->
        val selected = task.id in form.topTaskIds
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface
                )
                .clickable { viewModel.toggleTopTask(task) }
                .padding(10.dp)
        ) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    HorizontalDivider()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.ritual_razlozhit_zavtrashniy_den), style = MaterialTheme.typography.bodyMedium)
            Text(
                "Сразу после ритуала расставит задачи по слотам",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = form.planTomorrow, onCheckedChange = viewModel::setPlanTomorrow)
    }
}

@Composable
private fun NoteStep(form: RitualForm, viewModel: RitualViewModel) {
    Text(stringResource(R.string.ritual_zametka_dnya), style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = form.note,
        onValueChange = viewModel::setNote,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.ritual_chto_zapomnilos)) },
        minLines = 4
    )
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        Modifier.fillMaxWidth().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = Moss,
            textAlign = TextAlign.Center
        )
    }
}
