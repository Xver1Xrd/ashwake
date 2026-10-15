package dev.ashwake.ui.tasks.editor

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ashwake.core.model.Priority
import dev.ashwake.domain.model.tasks.AttachmentKind
import dev.ashwake.domain.model.tasks.TaskAttachment
import dev.ashwake.domain.model.tasks.RecurrenceType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private val ESTIMATE_PRESETS = listOf(5, 15, 30, 45, 60, 90, 120)
private val REMINDER_PRESETS = listOf(5, 10, 15, 30, 60)
private val WEEKDAY_LABELS = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorScreen(
    onDone: () -> Unit,
    viewModel: TaskEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val projects by viewModel.projectList.collectAsStateWithLifecycle()
    val spaces by viewModel.spaceList.collectAsStateWithLifecycle()
    val attachments by viewModel.attachments.collectAsStateWithLifecycle()
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
    var newSubtask by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "Новая задача" else "Задача") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = viewModel::delete) {
                            Icon(Icons.Filled.Delete, contentDescription = "Удалить")
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
            if (state.hasSyncConflict) {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        "Эту задачу изменили на другом устройстве. Если сохранить её здесь, " +
                            "серверная версия будет заменена текущей после синхронизации.",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::setTitle,
                label = { Text("Название") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::setNote,
                label = { Text("Заметка") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            if (spaces.isNotEmpty()) {
                SectionTitle("Пространство")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    spaces.forEach { space ->
                        FilterChip(
                            selected = state.spaceId == space.id,
                            onClick = { viewModel.setSpace(space.id) },
                            label = {
                                Text(
                                    when (space.type) {
                                        "personal" -> "Личное"
                                        "couple" -> "Мы"
                                        else -> "Общее"
                                    }
                                )
                            }
                        )
                    }
                }
                Text(
                    "Личные задачи видите только вы; задачи «Мы» доступны участникам пары.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionTitle("Приоритет")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Priority.entries.forEach { priority ->
                    FilterChip(
                        selected = state.priority == priority,
                        onClick = { viewModel.setPriority(priority) },
                        label = { Text(priority.name) }
                    )
                }
            }

            SectionTitle("Срок")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.dueDate != null,
                    onClick = { showDatePicker = true },
                    label = { Text(state.dueDate?.format(DATE_FORMAT) ?: "Дата") }
                )
                FilterChip(
                    selected = state.dueTime != null,
                    onClick = { showTimePicker = true },
                    label = { Text(state.dueTime?.format(TIME_FORMAT) ?: "Время") }
                )
                if (state.dueDate != null || state.dueTime != null) {
                    IconButton(onClick = {
                        viewModel.setDate(null)
                        viewModel.setTime(null)
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = "Убрать срок")
                    }
                }
            }

            SectionTitle("Оценка времени")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ESTIMATE_PRESETS.take(5).forEach { minutes ->
                    FilterChip(
                        selected = state.estimateMinutes == minutes,
                        onClick = {
                            viewModel.setEstimate(
                                if (state.estimateMinutes == minutes) null else minutes
                            )
                        },
                        label = { Text(formatMinutes(minutes)) }
                    )
                }
            }
            Text(
                "Нужна планировщику дня: без оценки задача не попадёт в автораскладку",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SectionTitle("Проект")
            ProjectDropdown(
                projects = projects.map { it.id to it.name },
                selectedId = state.projectId,
                onSelect = viewModel::setProject
            )

            OutlinedTextField(
                value = state.tagsInput,
                onValueChange = viewModel::setTagsInput,
                label = { Text("Теги") },
                placeholder = { Text("#дом #срочное") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            HorizontalDivider()

            SectionTitle("Вложения")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        photoPicker.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    enabled = state.canSave
                ) {
                    Icon(Icons.Filled.Photo, contentDescription = null)
                    Text("  Фото")
                }
                OutlinedButton(
                    onClick = { filePicker.launch(arrayOf("*/*")) },
                    enabled = state.canSave
                ) {
                    Icon(Icons.Filled.AttachFile, contentDescription = null)
                    Text("  Файл")
                }
            }
            Text(
                if (state.canSave) {
                    "Файлы хранятся в закрытой памяти приложения и входят в резервную копию"
                } else {
                    "Укажите название задачи, чтобы прикрепить файл"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            state.attachmentError?.let { message ->
                Text(message, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error)
            }
            attachments.forEach { attachment ->
                AttachmentRow(
                    attachment = attachment,
                    onOpen = {
                        runCatching {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.parse(attachment.contentUri), attachment.mimeType)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Открыть вложение"))
                        }.onFailure {
                            viewModel.reportAttachmentError("Нет приложения для открытия этого файла")
                        }
                    },
                    onDelete = { viewModel.deleteAttachment(attachment.id) }
                )
            }

            HorizontalDivider()

            SectionTitle("Подзадачи")
            state.subtasks.forEachIndexed { index, subtask ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = subtask.done,
                        onCheckedChange = { viewModel.toggleSubtask(index) }
                    )
                    Text(subtask.title, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.removeSubtask(index) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Убрать подзадачу")
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newSubtask,
                    onValueChange = { newSubtask = it },
                    label = { Text("Новая подзадача") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                IconButton(
                    onClick = {
                        viewModel.addSubtask(newSubtask)
                        newSubtask = ""
                    },
                    enabled = newSubtask.isNotBlank()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Добавить подзадачу")
                }
            }

            HorizontalDivider()

            RecurrenceSection(state, viewModel)

            HorizontalDivider()

            SectionTitle("Настойчивое напоминание")
            Text(
                "Повторять уведомление, пока задача не закрыта",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = state.persistentReminderMinutes == null,
                    onClick = { viewModel.setPersistentReminder(null) },
                    label = { Text("Выкл") }
                )
                REMINDER_PRESETS.forEach { minutes ->
                    FilterChip(
                        selected = state.persistentReminderMinutes == minutes,
                        onClick = { viewModel.setPersistentReminder(minutes) },
                        label = { Text("${minutes}м") }
                    )
                }
            }
            if (state.persistentReminderMinutes != null && state.dueTime == null) {
                Text(
                    "Нужно указать время: без него напоминать не от чего",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = viewModel::save,
                    enabled = state.canSave,
                    modifier = Modifier.weight(1f)
                ) { Text("Сохранить") }

                if (!state.isNew) {
                    TextButton(onClick = viewModel::toggleDone) {
                        Text(if (state.isDone) "Вернуть в работу" else "Выполнено")
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.dueDate
                ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        // Пикер отдаёт UTC-полночь: переводим её в дату без сдвига часового пояса
                        viewModel.setDate(
                            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        )
                    }
                    showDatePicker = false
                }) { Text("Готово") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Отмена") }
            }
        ) { DatePicker(state = pickerState) }
    }

    if (showTimePicker) {
        val now = state.dueTime ?: LocalTime.of(9, 0)
        val pickerState = rememberTimePickerState(
            initialHour = now.hour, initialMinute = now.minute, is24Hour = true
        )
        DatePickerDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setTime(LocalTime.of(pickerState.hour, pickerState.minute))
                    showTimePicker = false
                }) { Text("Готово") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Отмена") }
            }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) { TimePicker(state = pickerState) }
        }
    }
}

@Composable
private fun AttachmentRow(
    attachment: TaskAttachment,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            if (attachment.kind == AttachmentKind.PHOTO) Icons.Filled.Photo
            else Icons.Filled.AttachFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Column(Modifier.weight(1f)) {
            Text(attachment.displayName, maxLines = 1)
            Text(
                formatFileSize(attachment.sizeBytes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Close, contentDescription = "Удалить вложение")
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f МБ".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.1f КБ".format(bytes / 1024.0)
    else -> "$bytes Б"
}

@Composable
private fun RecurrenceSection(state: TaskEditorState, viewModel: TaskEditorViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Повторять", style = MaterialTheme.typography.titleSmall)
        Switch(
            checked = state.recurrenceEnabled,
            onCheckedChange = viewModel::setRecurrenceEnabled
        )
    }
    if (!state.recurrenceEnabled) return

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RecurrenceType.entries.forEach { type ->
            FilterChip(
                selected = state.recurrenceType == type,
                onClick = { viewModel.setRecurrenceType(type) },
                label = { Text(recurrenceLabel(type)) }
            )
        }
    }

    when (state.recurrenceType) {
        RecurrenceType.EVERY_N_DAYS -> OutlinedTextField(
            value = state.recurrenceInterval.toString(),
            onValueChange = { it.toIntOrNull()?.let(viewModel::setRecurrenceInterval) },
            label = { Text("Каждые N дней") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Number
            )
        )

        RecurrenceType.WEEKDAYS -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DayOfWeek.entries.forEachIndexed { index, day ->
                FilterChip(
                    selected = day in state.recurrenceWeekdays,
                    onClick = { viewModel.toggleRecurrenceWeekday(day) },
                    label = { Text(WEEKDAY_LABELS[index]) }
                )
            }
        }

        RecurrenceType.DAY_OF_MONTH -> OutlinedTextField(
            value = state.recurrenceDayOfMonth.toString(),
            onValueChange = { it.toIntOrNull()?.let(viewModel::setRecurrenceDayOfMonth) },
            label = { Text("Число месяца") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Number
            )
        )

        RecurrenceType.DAILY -> Unit
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = state.recurrenceFromCompletion,
            onCheckedChange = viewModel::setRecurrenceFromCompletion
        )
        Text(
            "Считать от факта выполнения, а не от плана",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectDropdown(
    projects: List<Pair<Long, String>>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = projects.firstOrNull { it.first == selectedId }?.second ?: "Без проекта"

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Проект") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Без проекта") },
                onClick = { onSelect(null); expanded = false }
            )
            projects.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { onSelect(id); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall)
}

private fun recurrenceLabel(type: RecurrenceType): String = when (type) {
    RecurrenceType.DAILY -> "каждый день"
    RecurrenceType.EVERY_N_DAYS -> "каждые N дней"
    RecurrenceType.WEEKDAYS -> "по дням недели"
    RecurrenceType.DAY_OF_MONTH -> "N-е число"
}

private fun formatMinutes(minutes: Int): String = when {
    minutes < 60 -> "${minutes}м"
    minutes % 60 == 0 -> "${minutes / 60}ч"
    else -> "${minutes / 60}ч${minutes % 60}"
}
