package dev.ashwake.ui.tasks.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ashwake.domain.model.tasks.Task
import dev.ashwake.ui.components.AshIcons
import dev.ashwake.ui.components.AshNavBar
import dev.ashwake.ui.components.EntityIcon
import dev.ashwake.ui.components.IconAction
import dev.ashwake.ui.components.ListDivider
import dev.ashwake.ui.components.ListGroup
import dev.ashwake.ui.components.ListRow
import dev.ashwake.ui.tasks.components.AttachmentGrid
import dev.ashwake.ui.theme.AshShapes
import dev.ashwake.ui.theme.AshTheme
import dev.ashwake.ui.theme.meaning
import dev.ashwake.ui.theme.priorityColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Полный просмотр: нажатие на задачу не переводит её в редактор. */
@Composable
fun TaskDetailScreen(onBack: () -> Unit, onEdit: () -> Unit, viewModel: TaskDetailViewModel = hiltViewModel()) {
    val task by viewModel.task.collectAsStateWithLifecycle()
    val attachmentError by viewModel.attachmentError.collectAsStateWithLifecycle()
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)) {
        viewModel.addAttachments(it)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        viewModel.addAttachments(it)
    }
    val colors = AshTheme.colors
    Column(Modifier.fillMaxSize().background(colors.background)) {
        AshNavBar(title = "Задача", onBack = onBack, actions = {
            IconAction(AshIcons.Edit, "Редактировать", onClick = onEdit)
        })
        task?.let { item ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().background(colors.surface1, AshShapes.card).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EntityIcon(item.emoji, item.iconPath, size = 46.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.title, style = AshTheme.type.headline,
                            color = if (item.isDone) colors.text3 else colors.text,
                            textDecoration = if (item.isDone) TextDecoration.LineThrough else null)
                        Text(taskDue(item, LocalDate.now()), style = AshTheme.type.subhead, color = colors.text2)
                    }
                }
                if (item.subtasks.isNotEmpty()) ListGroup(header = "Подзадачи") {
                    item.subtasks.forEachIndexed { index, subtask ->
                        ListRow(title = subtask.title, subtitle = if (subtask.isDone) "Выполнено" else "Не выполнено",
                            trailing = { Icon(if (subtask.isDone) AshIcons.CheckCircle else AshIcons.Circle, null,
                                tint = if (subtask.isDone) colors.success else colors.text3) })
                        if (index != item.subtasks.lastIndex) ListDivider()
                    }
                }
                item.note?.takeIf { it.isNotBlank() }?.let { note ->
                    ListGroup(header = "Заметка") { Text(note, style = AshTheme.type.body, color = colors.text, modifier = Modifier.padding(16.dp)) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Фото и файлы", style = AshTheme.type.subhead, color = colors.text2)
                    AttachmentGrid(
                        attachments = item.attachments,
                        onAddPhoto = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onAddFile = { filePicker.launch(arrayOf("*/*")) },
                        onRemove = viewModel::removeAttachment
                    )
                }
                ListGroup(header = "Параметры") {
                    ListRow(title = "Приоритет", subtitle = item.priority.meaning,
                        trailing = { Box(Modifier.size(14.dp).background(colors.priorityColor(item.priority), CircleShape)) })
                    ListDivider()
                    ListRow(title = "Время", subtitle = taskTime(item))
                    if (item.tags.isNotEmpty()) { ListDivider(); ListRow(title = "Теги", subtitle = item.tags.joinToString(" ") { "#${it.name}" }) }
                }
                Button(modifier = Modifier.fillMaxWidth(), onClick = viewModel::toggleDone) {
                    Text(if (item.isDone) "Вернуть в работу" else "Отметить выполненной")
                }
            }
        }
    }
    attachmentError?.let { message ->
        AlertDialog(onDismissRequest = viewModel::dismissAttachmentError, title = { Text("Ошибка вложения") },
            text = { Text(message) }, confirmButton = {
                TextButton(onClick = viewModel::dismissAttachmentError) { Text("Понятно") }
            })
    }
}

private fun taskDue(task: Task, today: LocalDate): String = task.dueDate?.let { date ->
    when { date == today -> "Сегодня"; date == today.plusDays(1) -> "Завтра"; date < today && !task.isDone -> "Просрочено: ${date.format(DateTimeFormatter.ofPattern("d MMMM"))}"; else -> date.format(DateTimeFormatter.ofPattern("d MMMM")) }
} ?: "Без срока"

private fun taskTime(task: Task): String = buildList {
    task.dueTime?.let { add("с ${it.format(DateTimeFormatter.ofPattern("HH:mm"))}") }
    task.estimateMinutes?.let { duration -> if (task.dueTime != null) add("до ${task.dueTime.plusMinutes(duration.toLong()).format(DateTimeFormatter.ofPattern("HH:mm"))}") else add("$duration мин") }
}.joinToString(" · ").ifBlank { "Не задано" }
