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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import dev.ashwake.ui.components.AshTextField
import dev.ashwake.ui.components.PrimaryButton
import dev.ashwake.ui.components.TextAction
import dev.ashwake.ui.components.ChipButton
import dev.ashwake.ui.components.AshNavBar
import dev.ashwake.ui.theme.AshTheme
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
        containerColor = AshTheme.colors.background,
        topBar = {
            AshNavBar(
                title = stringResource(R.string.ritual_vecherniy_ritual),
                subtitle = if (state.isCatchUp) stringResource(R.string.ritual_za_1_s, date.format(DATE_FORMAT))
                else date.format(DATE_FORMAT),
                onBack = { if (stepIndex == 0) onDone() else viewModel.back() }
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
                    ChipButton(text = stringResource(R.string.detail_nazad), onClick = viewModel::back)
                }
                PrimaryButton(
                    text = if (step == RitualStep.NOTE) stringResource(R.string.routines_zakonchit) else stringResource(R.string.onboarding_dalshe),
                    modifier = Modifier.weight(1f),
                    onClick = viewModel::next
                )
            }
        }
    }
}

@Composable
private fun ScalesStep(form: RitualForm, viewModel: RitualViewModel) {
    Text(stringResource(R.string.ritual_kak_proshel_den), style = AshTheme.type.title3)
    ScaleRow(stringResource(R.string.ritual_ocenka_dnya), form.dayRating, viewModel::setDayRating)

    HorizontalDivider()
    Text(
        stringResource(R.string.ritual_nastroenie_i_energiya_otdelno_svyazi_s_privy),
        style = AshTheme.type.footnote,
        color = AshTheme.colors.text2
    )
    ScaleRow(stringResource(R.string.ritual_nastroenie), form.mood, viewModel::setMood)
    ScaleRow(stringResource(R.string.ritual_energiya), form.energy, viewModel::setEnergy)
}

@Composable
private fun ScaleRow(label: String, value: Int?, onSelect: (Int) -> Unit) {
    Column {
        Text(label, style = AshTheme.type.subhead)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { level ->
                ChipButton(
                        text = "$level",
                        selected = value == level,
                        onClick = { onSelect(level) }
                    )
            }
        }
    }
}

@Composable
private fun TasksStep(tasks: List<Task>, viewModel: RitualViewModel) {
    Text(stringResource(R.string.ritual_nezakrytye_zadachi), style = AshTheme.type.title3)

    if (tasks.isEmpty()) {
        EmptyHint(stringResource(R.string.ritual_vse_zakryto))
        return
    }

    TextAction(
        text = stringResource(R.string.ritual_perenesti_vse_na_zavtra),
        onClick = { viewModel.postponeAll(tasks) }
    )

    tasks.take(12).forEach { task ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(AshTheme.colors.surface1)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                task.title,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = AshTheme.type.callout
            )
            TextAction(
                text = stringResource(R.string.ritual_zavtra),
                onClick = { viewModel.postponeToTomorrow(task) }
            )
            TextAction(
                text = stringResource(R.string.ritual_udalit),
                onClick = { viewModel.drop(task) }
            )
        }
    }
}

@Composable
private fun HabitsStep(habits: List<HabitWithProgress>, viewModel: RitualViewModel) {
    Text(stringResource(R.string.ritual_neprostavlennye_privychki), style = AshTheme.type.title3)

    if (habits.isEmpty()) {
        EmptyHint(stringResource(R.string.ritual_vse_privychki_otmecheny))
        return
    }

    habits.forEach { progress ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(AshTheme.colors.surface1)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                progress.habit.name,
                modifier = Modifier.weight(1f),
                style = AshTheme.type.callout
            )
            TextAction(
                text = stringResource(R.string.ritual_sdelal),
                onClick = { viewModel.markDone(progress) }
            )
            TextAction(
                text = stringResource(R.string.ritual_propustil),
                onClick = { viewModel.markSkipped(progress) }
            )
        }
    }
}

@Composable
private fun TomorrowStep(
    candidates: List<Task>,
    form: RitualForm,
    viewModel: RitualViewModel
) {
    Text(stringResource(R.string.ritual_tri_glavnye_zadachi_na_zavtra), style = AshTheme.type.title3)
    Text(
        stringResource(R.string.ritual_vybrano_1_s_iz_3, form.topTaskIds.size),
        style = AshTheme.type.footnote,
        color = AshTheme.colors.text2
    )

    candidates.take(15).forEach { task ->
        val selected = task.id in form.topTaskIds
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (selected) AshTheme.colors.surface2
                    else AshTheme.colors.surface1
                )
                .clickable { viewModel.toggleTopTask(task) }
                .padding(10.dp)
        ) {
            Text(
                task.title,
                style = AshTheme.type.callout,
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
            Text(stringResource(R.string.ritual_razlozhit_zavtrashniy_den), style = AshTheme.type.callout)
            Text(
                stringResource(R.string.ritual_srazu_posle_rituala_rasstavit_zadachi_po_slo),
                style = AshTheme.type.footnote,
                color = AshTheme.colors.text2
            )
        }
        Switch(checked = form.planTomorrow, onCheckedChange = viewModel::setPlanTomorrow)
    }
}

@Composable
private fun NoteStep(form: RitualForm, viewModel: RitualViewModel) {
    Text(stringResource(R.string.ritual_zametka_dnya), style = AshTheme.type.title3)
    AshTextField(
        value = form.note,
        onValueChange = viewModel::setNote,
        modifier = Modifier.fillMaxWidth(),
        placeholder = stringResource(R.string.ritual_chto_zapomnilos),
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
            style = AshTheme.type.callout,
            color = Moss,
            textAlign = TextAlign.Center
        )
    }
}
