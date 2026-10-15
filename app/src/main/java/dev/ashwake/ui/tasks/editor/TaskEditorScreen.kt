package dev.ashwake.ui.tasks.editor

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ashwake.ui.components.TextAction
import dev.ashwake.domain.model.tasks.RecurrenceType
import dev.ashwake.domain.model.tasks.AttachmentKind
import dev.ashwake.domain.model.tasks.TaskAttachment
import dev.ashwake.ui.components.AshIcons
import dev.ashwake.ui.components.AshNavBar
import dev.ashwake.ui.components.AshTextField
import dev.ashwake.ui.components.ChipButton
import dev.ashwake.ui.components.IconButtonSlot
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import dev.ashwake.ui.tasks.components.difficultyColor
import dev.ashwake.ui.tasks.components.difficultyDescription
import dev.ashwake.ui.tasks.components.formatEstimateRu
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

private val REMINDER_PRESETS = listOf(5, 10, 15, 30, 60)
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TaskEditorScreen(
    onDone: () -> Unit,
    viewModel: TaskEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val projects by viewModel.projectList.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val userTemplates by viewModel.userTemplates.collectAsStateWithLifecycle()
    val attachments by viewModel.attachments.collectAsStateWithLifecycle()
    val colors = AshTheme.colors
    val context = LocalContext.current

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        viewModel.addAttachments(uris.map(Uri::toString), AttachmentKind.PHOTO)
    }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        viewModel.addAttachments(uris.map(Uri::toString), AttachmentKind.FILE)
    }

    // Закрываем экран только после того, как сохранение реально дошло до базы
    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showTemplates by remember { mutableStateOf(false) }
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
                if (state.isNew && (presets.isNotEmpty() || userTemplates.isNotEmpty())) {
                    TextAction(
                        text = "Шаблоны",
                        onClick = { showTemplates = true }
                    )
                }
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
                .imePadding()
                .verticalScroll(rememberScrollState())
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
                    singleLine = false,
                    maxLines = 4,
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
                minLines = 2,
                maxLines = 10
            )

            // --- приоритет ---------------------------------------------------
            Section(stringResource(R.string.editor_prioritet)) {
                PriorityPicker(
                    selected = state.priority,
                    onSelect = viewModel::setPriority
                )
            }

            // --- сложность (1..5) ---------------------------------------------
            Section(
                title = "Сложность",
                footer = difficultyDescription(state.difficulty)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    val activeColor = difficultyColor(state.difficulty, colors)
                    (1..5).forEach { level ->
                        val isSelected = state.difficulty >= level
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) activeColor else colors.surface2)
                                .border(
                                    width = if (state.difficulty == level) 2.dp else 0.dp,
                                    color = if (state.difficulty == level) colors.text else Color.Transparent,
                                    shape = CircleShape
                                )
                                .tappable { viewModel.setDifficulty(level) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$level",
                                style = AshTheme.type.headline,
                                color = if (isSelected) Color.White else colors.text3
                            )
                        }
                    }
                }
            }

            // --- срок ---------------------------------------------------------
            Section(stringResource(R.string.editor_srok)) {
                DueDateRow(
                    state = state,
                    onPickDate = { showDatePicker = true },
                    onPickTime = { showTimePicker = true },
                    onPickEndTime = { showEndTimePicker = true },
                    onSetDate = viewModel::setDate,
                    onClear = {
                        viewModel.setDate(null)
                        viewModel.setTime(null)
                    }
                )
            }

            // --- оценка времени (ввод) ----------------------------------------
            val est = state.estimateMinutes
            Section(
                title = stringResource(R.string.editor_ocenka_vremeni),
                footer = if (est != null && est > 0) {
                    "Итого: ${formatEstimateRu(est)}"
                } else {
                    stringResource(R.string.editor_bez_ocenki_zadacha_ne_popadet_v_avtoraskladk)
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AshTextField(
                        value = state.estimateHours,
                        onValueChange = viewModel::setEstimateHours,
                        label = "Часы",
                        placeholder = "0",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    AshTextField(
                        value = state.estimateMinutesText,
                        onValueChange = viewModel::setEstimateMinutesText,
                        label = "Минуты",
                        placeholder = "0",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    if (state.estimateMinutes != null) {
                        IconAction(
                            icon = AshIcons.Close,
                            contentDescription = "Очистить время",
                            tint = colors.text3,
                            onClick = viewModel::clearEstimate
                        )
                    }
                }
            }

            Section(
                title = "Шаблон задачи",
                footer = "Шаблон не попадает в список задач. Его можно использовать для работы по расписанию и повторов."
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Сохранить как шаблон", style = AshTheme.type.body, color = colors.text, modifier = Modifier.weight(1f))
                    Switch(checked = state.isTemplate, onCheckedChange = viewModel::setIsTemplate)
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

            Column(modifier = Modifier.fillMaxWidth()) {
                AshTextField(
                    value = state.tagsInput,
                    onValueChange = viewModel::setTagsInput,
                    label = stringResource(R.string.editor_tegi),
                    placeholder = stringResource(R.string.editor_dom_srochnoe)
                )
                val allTags by viewModel.availableTags.collectAsStateWithLifecycle()
                val currentTagNames = remember(state.tagsInput) {
                    state.tagsInput.split(Regex("[\\s,]+"))
                        .map { it.trim().removePrefix("#").lowercase() }
                        .filter { it.isNotEmpty() }
                        .toSet()
                }
                val tagSuggestions = remember(allTags, currentTagNames) {
                    allTags.filter { it.name.lowercase() !in currentTagNames }
                }
                if (tagSuggestions.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        tagSuggestions.take(12).forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(AshShapes.pill)
                                    .background(colors.surface2)
                                    .tappable { viewModel.appendTag(tag.name) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "+ #${tag.name}",
                                    style = AshTheme.type.caption,
                                    color = colors.text2
                                )
                            }
                        }
                    }
                }
            }

            Section(
                title = "Вложения",
                footer = if (state.canSave) {
                    "Фото и файлы хранятся в закрытой памяти приложения"
                } else {
                    "Сначала укажите название задачи"
                },
                footerDanger = !state.canSave
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChipButton(
                            text = "Фото",
                            selected = false,
                            enabled = state.canSave,
                            onClick = {
                                photoPicker.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            }
                        )
                        ChipButton(
                            text = "Файл",
                            selected = false,
                            enabled = state.canSave,
                            onClick = { filePicker.launch(arrayOf("*/*")) }
                        )
                    }
                    state.attachmentError?.let { message ->
                        Text(
                            text = message,
                            style = AshTheme.type.footnote,
                            color = colors.danger
                        )
                    }
                    attachments.forEach { attachment ->
                        AttachmentRow(
                            attachment = attachment,
                            onOpen = {
                                runCatching {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(
                                            Uri.parse(attachment.contentUri),
                                            attachment.mimeType
                                        )
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(intent, "Открыть вложение")
                                    )
                                }.onFailure {
                                    viewModel.reportAttachmentError(
                                        "Нет приложения для открытия этого файла"
                                    )
                                }
                            },
                            onDelete = { viewModel.deleteAttachment(attachment.id) }
                        )
                    }
                }
            }

            // --- подзадачи ------------------------------------------------------
            Section(stringResource(R.string.editor_podzadachi)) {
                val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                val haptics = dev.ashwake.ui.theme.rememberHaptics()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.subtasks.forEachIndexed { index, subtask ->
                        SubtaskRow(
                            title = subtask.title,
                            done = subtask.done,
                            onToggle = { viewModel.toggleSubtask(index) },
                            onEdit = { newTitle -> viewModel.editSubtask(index, newTitle) },
                            onCopy = {
                                clipboard.setText(androidx.compose.ui.text.AnnotatedString(subtask.title))
                                haptics.play(dev.ashwake.ui.theme.HapticKind.LIGHT)
                            },
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
                            singleLine = false,
                            maxLines = 3,
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

    if (showEndTimePicker) {
        TimePickerSheet(
            initial = state.endTime,
            onPick = viewModel::setEndTime,
            onDismiss = { showEndTimePicker = false }
        )
    }

    if (showTemplates) {
        TaskTemplatesDialog(
            presets = presets,
            userTemplates = userTemplates,
            onPick = {
                viewModel.applyPreset(it)
                showTemplates = false
            },
            onPickUser = {
                viewModel.applyUserTemplate(it)
                showTemplates = false
            },
            onDismiss = { showTemplates = false }
        )
    }
}

@Composable
private fun AttachmentRow(
    attachment: TaskAttachment,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = AshTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface1, AshShapes.group)
            .tappable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = if (attachment.kind == AttachmentKind.PHOTO) "🖼️" else "📎",
            fontSize = 20.sp
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = attachment.displayName,
                style = AshTheme.type.body,
                color = colors.text,
                maxLines = 1
            )
            Text(
                text = formatFileSize(attachment.sizeBytes),
                style = AshTheme.type.footnote,
                color = colors.text2
            )
        }
        IconAction(
            icon = AshIcons.Close,
            contentDescription = "Удалить вложение",
            tint = colors.text2,
            onClick = onDelete
        )
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f МБ".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.1f КБ".format(bytes / 1024.0)
    else -> "$bytes Б"
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
    onPickEndTime: () -> Unit,
    onSetDate: (LocalDate?) -> Unit,
    onClear: () -> Unit
) {
    val today = state.today
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
            val is24Hour = dev.ashwake.ui.theme.LocalIs24Hour.current
            ChipButton(
                text = state.dueTime?.let { dev.ashwake.ui.theme.formatTime(it, is24Hour) } ?: stringResource(R.string.editor_vremya),
                icon = AshIcons.Timer,
                selected = state.dueTime != null,
                onClick = onPickTime
            )
            if (state.dueTime != null) {
                ChipButton(
                    text = state.endTime?.let { "до " + dev.ashwake.ui.theme.formatTime(it, is24Hour) } ?: "До",
                    icon = AshIcons.Timer,
                    selected = state.endTime != null,
                    onClick = onPickEndTime
                )
            }
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
    onEdit: (String) -> Unit,
    onCopy: () -> Unit,
    onRemove: () -> Unit
) {
    val colors = AshTheme.colors
    var isEditing by remember { mutableStateOf(false) }
    var editValue by remember(title) { mutableStateOf(title) }

    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surface1, AshShapes.group)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!isEditing) {
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
                modifier = Modifier
                    .weight(1f)
                    .tappable { isEditing = true }
            )
            IconAction(
                icon = AshIcons.Pencil,
                contentDescription = "Редактировать",
                tint = colors.text3,
                onClick = { isEditing = true }
            )
            IconAction(
                icon = AshIcons.Copy,
                contentDescription = "Скопировать в буфер",
                tint = colors.text3,
                onClick = onCopy
            )
            IconAction(
                icon = AshIcons.Close,
                contentDescription = stringResource(R.string.editor_ubrat_podzadachu),
                tint = colors.text3,
                onClick = onRemove
            )
        } else {
            BasicTextField(
                value = editValue,
                onValueChange = { editValue = it },
                textStyle = AshTheme.type.body.copy(color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.weight(1f)
            )
            IconAction(
                icon = AshIcons.Check,
                contentDescription = "Сохранить",
                tint = colors.accent,
                onClick = {
                    if (editValue.isNotBlank()) {
                        onEdit(editValue.trim())
                    }
                    isEditing = false
                }
            )
            IconAction(
                icon = AshIcons.Close,
                contentDescription = "Отмена",
                tint = colors.text3,
                onClick = {
                    editValue = title
                    isEditing = false
                }
            )
        }
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
private fun formatMinutes(minutes: Int): String = formatEstimateRu(minutes)

@Composable
private fun TaskTemplatesDialog(
    presets: List<dev.ashwake.data.assets.TaskPreset>,
    userTemplates: List<dev.ashwake.domain.model.tasks.Task>,
    onPick: (dev.ashwake.data.assets.TaskPreset) -> Unit,
    onPickUser: (dev.ashwake.domain.model.tasks.Task) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = AshTheme.colors
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Шаблоны задач", style = AshTheme.type.title3) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (userTemplates.isNotEmpty()) {
                    Text("Мои шаблоны", style = AshTheme.type.headline, color = colors.text)
                    userTemplates.forEach { template ->
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(AshShapes.card)
                                .background(colors.surface2).tappable { onPickUser(template) }.padding(14.dp)
                        ) {
                            Text(template.title, style = AshTheme.type.headline, color = colors.text)
                            Text("${template.dueTime ?: "без времени"} · ${template.estimateMinutes ?: 0} мин", style = AshTheme.type.footnote, color = colors.text2)
                        }
                    }
                }
                if (presets.isNotEmpty()) Text("Готовые шаблоны", style = AshTheme.type.headline, color = colors.text)
                presets.forEach { preset ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AshShapes.card)
                            .background(colors.surface2)
                            .tappable { onPick(preset) }
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = preset.title,
                            style = AshTheme.type.headline,
                            color = colors.text
                        )
                        Text(
                            text = "${preset.subtasks.size} подзадач" +
                                (preset.estimateMinutes?.let { " · ~$it мин" } ?: ""),
                            style = AshTheme.type.footnote,
                            color = colors.text2
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextAction(text = stringResource(R.string.detail_otmena), onClick = onDismiss)
        }
    )
}
