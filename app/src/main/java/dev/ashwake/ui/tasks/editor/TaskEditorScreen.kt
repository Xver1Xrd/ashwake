package dev.ashwake.ui.tasks.editor

import androidx.compose.ui.res.stringArrayResource
import dev.ashwake.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ashwake.ui.components.TextAction
import dev.ashwake.domain.model.tasks.RecurrenceType
import dev.ashwake.ui.components.AshIcons
import dev.ashwake.ui.components.AshNavBar
import dev.ashwake.ui.components.AshTextField
import dev.ashwake.ui.components.ChipButton
import dev.ashwake.ui.components.IconButtonSlot
import dev.ashwake.ui.components.IconPicker
import dev.ashwake.ui.components.FieldLabel
import dev.ashwake.ui.components.IconAction
import dev.ashwake.ui.components.PrimaryButton
import dev.ashwake.ui.components.ScreenPadding
import dev.ashwake.ui.components.SecondaryButton
import dev.ashwake.ui.components.tappable
import dev.ashwake.ui.tasks.components.PriorityPicker
import dev.ashwake.ui.theme.AshShapes
import dev.ashwake.ui.theme.AshTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Редактор задачи.
 *
 * Экран собран на компонентах дизайн-системы: скруглённые поля, цветные
 * приоритеты, чипы-таблетки. Материаловские `OutlinedTextField` и
 * `FilterChip`, стоявшие здесь раньше, тянули за собой прямые углы и
 * собственную типографику — форма выглядела вырванной из другого приложения.
 *
 * Порядок полей — по частоте: значок и название, приоритет, срок. Всё, что
 * нужно реже (повтор, напоминание, подзадачи), лежит ниже и не мешает
 * быстрому вводу.
 */

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM")
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private val ESTIMATE_PRESETS = listOf(5, 15, 30, 45, 60, 90, 120)
private val REMINDER_PRESETS = listOf(5, 10, 15, 30, 60)
@Composable
fun TaskEditorScreen(
    onDone: () -> Unit,
    viewModel: TaskEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val projects by viewModel.projectList.collectAsStateWithLifecycle()
    val colors = AshTheme.colors

    // Закрываем экран только после того, как сохранение реально дошло до базы
    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var newSubtask by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        AshNavBar(
            title = if (state.isNew) stringResource(R.string.shortcut_new_task) else stringResource(R.string.tile_add_task),
            onBack = onDone,
            actions = {
                if (!state.isNew) {
                    IconAction(
                        icon = AshIcons.Trash,
                        contentDescription = stringResource(R.string.blocking_udalit),
                        tint = colors.danger,
                        onClick = viewModel::delete
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = ScreenPadding)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // --- значок и название ------------------------------------------
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButtonSlot(
                    emoji = state.emoji,
                    iconPath = state.iconPath,
                    expanded = showEmojiPicker,
                    onClick = { showEmojiPicker = !showEmojiPicker }
                )
                AshTextField(
                    value = state.title,
                    onValueChange = viewModel::setTitle,
                    placeholder = stringResource(R.string.editor_chto_nuzhno_sdelat),
                    textStyle = AshTheme.type.headline,
                    modifier = Modifier.weight(1f)
                )
            }

            AnimatedVisibility(
                visible = showEmojiPicker,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                IconPicker(
                    emoji = state.emoji,
                    iconPath = state.iconPath,
                    onEmoji = { viewModel.setEmoji(it) },
                    onIcon = { viewModel.setIconPath(it) }
                )
            }

            AshTextField(
                value = state.note,
                onValueChange = viewModel::setNote,
                placeholder = stringResource(R.string.detail_zametka),
                singleLine = false,
                minLines = 2
            )

            // --- приоритет ---------------------------------------------------
            Section(stringResource(R.string.editor_prioritet)) {
                PriorityPicker(
                    selected = state.priority,
                    onSelect = viewModel::setPriority
                )
            }

            // --- срок ---------------------------------------------------------
            Section(stringResource(R.string.editor_srok)) {
                DueDateRow(
                    state = state,
                    onPickDate = { showDatePicker = true },
                    onPickTime = { showTimePicker = true },
                    onSetDate = viewModel::setDate,
                    onClear = {
                        viewModel.setDate(null)
                        viewModel.setTime(null)
                    }
                )
            }

            // --- оценка -------------------------------------------------------
            Section(
                title = stringResource(R.string.editor_ocenka_vremeni),
                footer = stringResource(R.string.editor_bez_ocenki_zadacha_ne_popadet_v_avtoraskladk)
            ) {
                ChipRow {
                    ESTIMATE_PRESETS.forEach { minutes ->
                        ChipButton(
                            text = formatMinutes(minutes),
                            selected = state.estimateMinutes == minutes,
                            onClick = {
                                viewModel.setEstimate(
                                    if (state.estimateMinutes == minutes) null else minutes
                                )
                            }
                        )
                    }
                }
            }

            // --- проект и теги --------------------------------------------------
            Section(stringResource(R.string.editor_proekt)) {
                ChipRow {
                    ChipButton(
                        text = stringResource(R.string.editor_bez_proekta),
                        selected = state.projectId == null,
                        onClick = { viewModel.setProject(null) }
                    )
                    projects.forEach { project ->
                        ChipButton(
                            text = project.name,
                            selected = state.projectId == project.id,
                            onClick = { viewModel.setProject(project.id) }
                        )
                    }
                }
            }

            AshTextField(
                value = state.tagsInput,
                onValueChange = viewModel::setTagsInput,
                label = stringResource(R.string.editor_tegi),
                placeholder = stringResource(R.string.editor_dom_srochnoe)
            )

            // --- подзадачи ------------------------------------------------------
            Section(stringResource(R.string.editor_podzadachi)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.subtasks.forEachIndexed { index, subtask ->
                        SubtaskRow(
                            title = subtask.title,
                            done = subtask.done,
                            onToggle = { viewModel.toggleSubtask(index) },
                            onRemove = { viewModel.removeSubtask(index) }
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AshTextField(
                            value = newSubtask,
                            onValueChange = { newSubtask = it },
                            placeholder = stringResource(R.string.editor_esche_shag),
                            modifier = Modifier.weight(1f)
                        )
                        IconAction(
                            icon = AshIcons.Add,
                            contentDescription = stringResource(R.string.editor_dobavit_podzadachu),
                            enabled = newSubtask.isNotBlank(),
                            onClick = {
                                viewModel.addSubtask(newSubtask)
                                newSubtask = ""
                            }
                        )
                    }
                }
            }

            RecurrenceSection(state, viewModel)

            // --- напоминание ----------------------------------------------------
            Section(
                title = stringResource(R.string.editor_nastoychivoe_napominanie),
                footer = if (state.persistentReminderMinutes != null && state.dueTime == null) {
                    stringResource(R.string.editor_nuzhno_ukazat_vremya_bez_nego_napominat_ne_o)
                } else {
                    stringResource(R.string.editor_povtoryat_uvedomlenie_poka_zadacha_ne_zakryt)
                },
                footerDanger = state.persistentReminderMinutes != null && state.dueTime == null
            ) {
                ChipRow {
                    ChipButton(
                        text = stringResource(R.string.editor_vykl),
                        selected = state.persistentReminderMinutes == null,
                        onClick = { viewModel.setPersistentReminder(null) }
                    )
                    REMINDER_PRESETS.forEach { minutes ->
                        ChipButton(
                            text = stringResource(R.string.settings_1_s_min, minutes),
                            selected = state.persistentReminderMinutes == minutes,
                            onClick = { viewModel.setPersistentReminder(minutes) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            PrimaryButton(
                text = stringResource(R.string.detail_sohranit),
                enabled = state.canSave,
                onClick = viewModel::save
            )

            if (!state.isNew) {
                SecondaryButton(
                    text = if (state.isDone) stringResource(R.string.editor_vernut_v_rabotu) else stringResource(R.string.editor_vypolneno),
                    onClick = viewModel::toggleDone
                )
            }

            Spacer(Modifier.navigationBarsPadding())
        }
    }

    if (showDatePicker) {
        DatePickerSheet(
            initial = state.dueDate,
            onPick = { viewModel.setDate(it) },
            onDismiss = { showDatePicker = false }
        )
    }

    if (showTimePicker) {
        TimePickerSheet(
            initial = state.dueTime,
            onPick = { viewModel.setTime(it) },
            onDismiss = { showTimePicker = false }
        )
    }
}

// ---------------------------------------------------------------------------
// Блоки формы
// ---------------------------------------------------------------------------

/**
 * Секция формы: подпись, содержимое, необязательное пояснение снизу.
 * Пояснение краснеет, когда объясняет не «зачем это», а «почему не сработает».
 */
@Composable
private fun Section(
    title: String,
    modifier: Modifier = Modifier,
    footer: String? = null,
    footerDanger: Boolean = false,
    content: @Composable () -> Unit
) {
    Column(modifier.fillMaxWidth()) {
        FieldLabel(title)
        content()
        footer?.let {
            Text(
                text = it,
                style = AshTheme.type.footnote,
                color = if (footerDanger) AshTheme.colors.danger else AshTheme.colors.text2,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp)
            )
        }
    }
}

/** Ряд чипов с горизонтальной прокруткой: пресетов больше, чем влезает в ширину. */
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) { content() }
}

/**
 * Срок: быстрые «сегодня» и «завтра» рядом с точным выбором. Большая часть
 * задач ставится на эти два дня, и открывать ради них календарь незачем.
 */
@Composable
private fun DueDateRow(
    state: TaskEditorState,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
    onSetDate: (LocalDate?) -> Unit,
    onClear: () -> Unit
) {
    val today = LocalDate.now()
    val tomorrow = today.plusDays(1)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChipRow {
            ChipButton(
                text = stringResource(R.string.shortcut_today),
                selected = state.dueDate == today,
                onClick = { onSetDate(today) }
            )
            ChipButton(
                text = stringResource(R.string.editor_zavtra),
                selected = state.dueDate == tomorrow,
                onClick = { onSetDate(tomorrow) }
            )
            ChipButton(
                text = state.dueDate
                    ?.takeIf { it != today && it != tomorrow }
                    ?.format(DATE_FORMAT)
                    ?: stringResource(R.string.editor_data),
                icon = AshIcons.Calendar,
                selected = state.dueDate != null && state.dueDate != today && state.dueDate != tomorrow,
                onClick = onPickDate
            )
            ChipButton(
                text = state.dueTime?.format(TIME_FORMAT) ?: stringResource(R.string.editor_vremya),
                icon = AshIcons.Timer,
                selected = state.dueTime != null,
                onClick = onPickTime
            )
            if (state.dueDate != null || state.dueTime != null) {
                ChipButton(text = stringResource(R.string.editor_bez_sroka), icon = AshIcons.Close, onClick = onClear)
            }
        }
    }
}

@Composable
private fun SubtaskRow(
    title: String,
    done: Boolean,
    onToggle: () -> Unit,
    onRemove: () -> Unit
) {
    val colors = AshTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surface1, AshShapes.group)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .size(22.dp)
                .background(if (done) colors.accent else Color.Transparent, AshShapes.pill)
                .border(
                    width = if (done) 0.dp else 1.5.dp,
                    color = colors.text3,
                    shape = AshShapes.pill
                )
                .tappable(onClick = onToggle),
            contentAlignment = Alignment.Center
        ) {
            if (done) {
                Icon(
                    AshIcons.Check,
                    contentDescription = null,
                    tint = if (colors.isDark) Color.Black else Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
        Text(
            text = title,
            style = AshTheme.type.body,
            color = if (done) colors.text3 else colors.text,
            modifier = Modifier.weight(1f)
        )
        IconAction(
            icon = AshIcons.Close,
            contentDescription = stringResource(R.string.editor_ubrat_podzadachu),
            tint = colors.text3,
            onClick = onRemove
        )
    }
}

@Composable
private fun RecurrenceSection(state: TaskEditorState, viewModel: TaskEditorViewModel) {
    val colors = AshTheme.colors

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface1, AshShapes.group)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.editor_povtoryat), style = AshTheme.type.body, color = colors.text)
            Switch(
                checked = state.recurrenceEnabled,
                onCheckedChange = viewModel::setRecurrenceEnabled,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = colors.accent,
                    checkedThumbColor = if (colors.isDark) Color.Black else Color.White,
                    uncheckedTrackColor = colors.surface3,
                    uncheckedBorderColor = Color.Transparent,
                    uncheckedThumbColor = colors.text2
                )
            )
        }

        if (!state.recurrenceEnabled) return@Column

        ChipRow {
            RecurrenceType.entries.forEach { type ->
                ChipButton(
                    text = recurrenceLabel(type),
                    selected = state.recurrenceType == type,
                    onClick = { viewModel.setRecurrenceType(type) }
                )
            }
        }

        when (state.recurrenceType) {
            RecurrenceType.EVERY_N_DAYS -> AshTextField(
                value = state.recurrenceInterval.toString(),
                onValueChange = { it.toIntOrNull()?.let(viewModel::setRecurrenceInterval) },
                label = stringResource(R.string.editor_kazhdye_n_dney),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            RecurrenceType.WEEKDAYS -> Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DayOfWeek.entries.forEachIndexed { index, day ->
                    val selected = day in state.recurrenceWeekdays
                    Box(
                        Modifier
                            .weight(1f)
                            .background(
                                if (selected) colors.accent else colors.surface2,
                                AshShapes.pill
                            )
                            .tappable(onClick = { viewModel.toggleRecurrenceWeekday(day) })
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringArrayResource(R.array.weekday_short)[index],
                            style = AshTheme.type.footnote,
                            color = when {
                                selected && colors.isDark -> Color.Black
                                selected -> Color.White
                                else -> colors.text2
                            }
                        )
                    }
                }
            }

            RecurrenceType.DAY_OF_MONTH -> AshTextField(
                value = state.recurrenceDayOfMonth.toString(),
                onValueChange = { it.toIntOrNull()?.let(viewModel::setRecurrenceDayOfMonth) },
                label = stringResource(R.string.editor_chislo_mesyaca),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            RecurrenceType.DAILY -> Unit
        }

        Row(
            Modifier
                .fillMaxWidth()
                .tappable(
                    onClick = {
                        viewModel.setRecurrenceFromCompletion(!state.recurrenceFromCompletion)
                    }
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .size(20.dp)
                    .background(
                        if (state.recurrenceFromCompletion) colors.accent else Color.Transparent,
                        AshShapes.small
                    )
                    .border(
                        width = if (state.recurrenceFromCompletion) 0.dp else 1.5.dp,
                        color = colors.text3,
                        shape = AshShapes.small
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (state.recurrenceFromCompletion) {
                    Icon(
                        AshIcons.Check,
                        contentDescription = null,
                        tint = if (colors.isDark) Color.Black else Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
            Text(
                stringResource(R.string.editor_schitat_ot_fakta_vypolneniya_a_ne_ot_plana),
                style = AshTheme.type.subhead,
                color = colors.text2
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Пикеры даты и времени
//
// Материаловские пикеры оставлены: свой календарь с выбором года, локалью и
// доступностью — это отдельная работа, а форма у диалога теперь берётся из
// темы, так что углы у него общие с остальным приложением.
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(
    initial: LocalDate?,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial
            ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextAction(
                text = stringResource(R.string.editor_gotovo),
                onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        // Пикер отдаёт UTC-полночь: переводим её в дату без сдвига пояса
                        onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    onDismiss()
                }
            )
        },
        dismissButton = {
            TextAction(text = stringResource(R.string.detail_otmena), onClick = onDismiss)
        }
    ) { DatePicker(state = pickerState) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerSheet(
    initial: LocalTime?,
    onPick: (LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    val start = initial ?: LocalTime.of(9, 0)
    val pickerState = rememberTimePickerState(
        initialHour = start.hour,
        initialMinute = start.minute,
        is24Hour = true
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextAction(
                text = stringResource(R.string.editor_gotovo),
                onClick = {
                    onPick(LocalTime.of(pickerState.hour, pickerState.minute))
                    onDismiss()
                }
            )
        },
        dismissButton = {
            TextAction(text = stringResource(R.string.detail_otmena), onClick = onDismiss)
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) { TimePicker(state = pickerState) }
    }
}

@Composable
private fun recurrenceLabel(type: RecurrenceType): String = when (type) {
    RecurrenceType.DAILY -> stringResource(R.string.components_kazhdyy_den)
    RecurrenceType.EVERY_N_DAYS -> stringResource(R.string.editor_kazhdye_n_dney_2)
    RecurrenceType.WEEKDAYS -> stringResource(R.string.components_po_dnyam_nedeli)
    RecurrenceType.DAY_OF_MONTH -> stringResource(R.string.editor_n_e_chislo)
}

@Composable
private fun formatMinutes(minutes: Int): String = when {
    minutes < 60 -> stringResource(R.string.settings_1_s_min, minutes)
    minutes % 60 == 0 -> stringResource(R.string.editor_1_s_ch, minutes / 60)
    else -> stringResource(R.string.editor_1_s_ch_2_s, minutes / 60, minutes % 60)
}
