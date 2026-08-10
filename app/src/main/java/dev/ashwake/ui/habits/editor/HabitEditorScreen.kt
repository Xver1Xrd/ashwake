package dev.ashwake.ui.habits.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import dev.ashwake.ui.components.AshNavBar
import dev.ashwake.ui.theme.AshTheme
import dev.ashwake.ui.components.IconButtonSlot
import dev.ashwake.ui.components.IconPicker
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ashwake.core.model.Sphere
import dev.ashwake.domain.model.habits.AnchorType
import dev.ashwake.domain.model.habits.HabitScheduleType
import dev.ashwake.domain.model.habits.HabitType
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import androidx.compose.ui.res.stringResource
import dev.ashwake.R

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val WEEKDAY_LABELS = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HabitEditorScreen(
    onDone: () -> Unit,
    viewModel: HabitEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val anchorCandidates by viewModel.anchorCandidates.collectAsStateWithLifecycle()
    var showTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    Scaffold(
        containerColor = AshTheme.colors.background,
        topBar = {
            AshNavBar(
                title = if (state.isNew) "Новая привычка" else "Привычка",
                onBack = onDone,
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = viewModel::delete) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.blocking_udalit))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            var showIconPicker by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButtonSlot(
                    emoji = state.icon,
                    iconPath = state.iconPath,
                    expanded = showIconPicker,
                    onClick = { showIconPicker = !showIconPicker }
                )
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    label = { Text(stringResource(R.string.editor_nazvanie)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            if (showIconPicker) {
                IconPicker(
                    emoji = state.icon,
                    iconPath = state.iconPath,
                    onEmoji = viewModel::setIcon,
                    onIcon = viewModel::setIconPath
                )
            }

            Text(stringResource(R.string.editor_tip), style = AshTheme.type.headline)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HabitType.entries.forEach { type ->
                    FilterChip(
                        selected = state.type == type,
                        onClick = { viewModel.setType(type) },
                        label = { Text(typeLabel(type)) }
                    )
                }
            }
            if (state.type == HabitType.NEGATIVE) {
                Text(
                    "Успех — это отсутствие отметки. Отмечать нужно срывы, а не выполнение",
                    style = AshTheme.type.footnote,
                    color = AshTheme.colors.text2
                )
            }

            if (state.type == HabitType.COUNTER) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.targetValue.toInt().toString(),
                        onValueChange = { it.toFloatOrNull()?.let(viewModel::setTarget) },
                        label = { Text(stringResource(R.string.editor_cel)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = state.unitName,
                        onValueChange = viewModel::setUnit,
                        label = { Text(stringResource(R.string.editor_edinica)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
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
                    Text(stringResource(R.string.editor_minimalnaya_planka), style = AshTheme.type.headline)
                    Text(
                        "Цель на плохой день. Держит серию и даёт половину вклада в score",
                        style = AshTheme.type.footnote,
                        color = AshTheme.colors.text2
                    )
                }
                Switch(
                    checked = state.minimumEnabled,
                    onCheckedChange = viewModel::setMinimumEnabled
                )
            }
            if (state.minimumEnabled) {
                OutlinedTextField(
                    value = state.minimumValue.toInt().toString(),
                    onValueChange = { it.toFloatOrNull()?.let(viewModel::setMinimum) },
                    label = { Text(stringResource(R.string.editor_minimum)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            HorizontalDivider()

            Text(stringResource(R.string.editor_raspisanie), style = AshTheme.type.headline)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HabitScheduleType.entries.forEach { type ->
                    FilterChip(
                        selected = state.scheduleType == type,
                        onClick = { viewModel.setScheduleType(type) },
                        label = { Text(scheduleLabel(type)) }
                    )
                }
            }

            when (state.scheduleType) {
                HabitScheduleType.TIMES_PER_WEEK -> Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (1..7).forEach { times ->
                        FilterChip(
                            selected = state.timesPerWeek == times,
                            onClick = { viewModel.setTimesPerWeek(times) },
                            label = { Text("$times") }
                        )
                    }
                }

                HabitScheduleType.WEEKDAYS -> Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DayOfWeek.entries.forEachIndexed { index, day ->
                        FilterChip(
                            selected = day in state.weekdays,
                            onClick = { viewModel.toggleWeekday(day) },
                            label = { Text(WEEKDAY_LABELS[index]) }
                        )
                    }
                }

                else -> Unit
            }

            HorizontalDivider()

            Text(stringResource(R.string.editor_sfera), style = AshTheme.type.headline)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Sphere.entries.forEach { sphere ->
                    FilterChip(
                        selected = state.sphere == sphere,
                        onClick = { viewModel.setSphere(sphere) },
                        label = { Text(sphereLabel(sphere)) }
                    )
                }
            }
            Text(
                "Сфера определяет, какая характеристика персонажа растёт от этой привычки",
                style = AshTheme.type.footnote,
                color = AshTheme.colors.text2
            )

            HorizontalDivider()

            Text(stringResource(R.string.editor_napominanie), style = AshTheme.type.headline)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.reminderTime != null,
                    onClick = { showTimePicker = true },
                    label = { Text(state.reminderTime?.format(TIME_FORMAT) ?: "Время") }
                )
                if (state.reminderTime != null) {
                    TextButton(onClick = { viewModel.setReminder(null) }) { Text(stringResource(R.string.editor_ubrat)) }
                }
            }

            Text(stringResource(R.string.editor_zamorozok_v_mesyac), style = AshTheme.type.headline)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0, 1, 3, 5, 10).forEach { quota ->
                    FilterChip(
                        selected = state.freezeQuota == quota,
                        onClick = { viewModel.setFreezeQuota(quota) },
                        label = { Text("$quota") }
                    )
                }
            }

            HorizontalDivider()

            Text(stringResource(R.string.editor_yakor), style = AshTheme.type.headline)
            Text(
                "Привязка к событию вместо часов: цепочка привычек одна за другой",
                style = AshTheme.type.footnote,
                color = AshTheme.colors.text2
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = state.anchorType == null,
                    onClick = { viewModel.setAnchorType(null) },
                    label = { Text(stringResource(R.string.editor_net)) }
                )
                listOf(AnchorType.HABIT_DONE, AnchorType.FIRST_UNLOCK).forEach { type ->
                    FilterChip(
                        selected = state.anchorType == type,
                        onClick = { viewModel.setAnchorType(type) },
                        label = { Text(anchorLabel(type)) }
                    )
                }
            }
            if (state.anchorType == AnchorType.HABIT_DONE) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    anchorCandidates.forEach { candidate ->
                        FilterChip(
                            selected = state.anchorHabitId == candidate.id,
                            onClick = { viewModel.setAnchorHabit(candidate.id) },
                            label = { Text(candidate.name) }
                        )
                    }
                }
            }

            Button(
                onClick = viewModel::save,
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.detail_sohranit)) }

            if (!state.isNew) {
                TextButton(onClick = viewModel::archive, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.editor_v_arhiv))
                }
            }
        }
    }

    if (showTimePicker) {
        val initial = state.reminderTime ?: LocalTime.of(9, 0)
        val pickerState = rememberTimePickerState(
            initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true
        )
        DatePickerDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setReminder(LocalTime.of(pickerState.hour, pickerState.minute))
                    showTimePicker = false
                }) { Text(stringResource(R.string.editor_gotovo)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.detail_otmena)) }
            }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) { TimePicker(state = pickerState) }
        }
    }
}

private fun typeLabel(type: HabitType): String = when (type) {
    HabitType.CHECK -> "чекбокс"
    HabitType.COUNTER -> "счётчик"
    HabitType.NEGATIVE -> "негативная"
}

private fun scheduleLabel(type: HabitScheduleType): String = when (type) {
    HabitScheduleType.DAILY -> "каждый день"
    HabitScheduleType.TIMES_PER_WEEK -> "N раз в неделю"
    HabitScheduleType.EVERY_OTHER_DAY -> "через день"
    HabitScheduleType.WEEKDAYS -> "по дням"
    HabitScheduleType.BIWEEKLY -> "раз в 2 недели"
}

private fun sphereLabel(sphere: Sphere): String = when (sphere) {
    Sphere.HEALTH -> "здоровье"
    Sphere.SPORT -> "спорт"
    Sphere.STUDY -> "учёба"
    Sphere.CHORES -> "быт"
    Sphere.MENTAL -> "ментальное"
}

private fun anchorLabel(type: AnchorType): String = when (type) {
    AnchorType.HABIT_DONE -> "после привычки"
    AnchorType.ROUTINE_DONE -> "после рутины"
    AnchorType.FIRST_UNLOCK -> "первая разблокировка"
    AnchorType.TASK_TAG_DONE -> "после задачи с тегом"
    AnchorType.TIME -> "по времени"
}
