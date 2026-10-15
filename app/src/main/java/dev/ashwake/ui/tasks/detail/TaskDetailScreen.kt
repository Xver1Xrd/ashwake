package dev.ashwake.ui.tasks.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ashwake.core.time.AppClock
import dev.ashwake.domain.model.tasks.Task
import dev.ashwake.domain.model.tasks.TaskAttachment
import dev.ashwake.domain.repository.tasks.TaskAttachmentRepository
import dev.ashwake.domain.repository.tasks.TaskRepository
import dev.ashwake.ui.components.AshIcons
import dev.ashwake.ui.components.AshNavBar
import dev.ashwake.ui.components.EntityIcon
import dev.ashwake.ui.components.IconAction
import dev.ashwake.ui.components.ListDivider
import dev.ashwake.ui.components.ListGroup
import dev.ashwake.ui.components.ListRow
import dev.ashwake.ui.components.PrimaryButton
import dev.ashwake.ui.components.tappable
import dev.ashwake.domain.model.tasks.AttachmentKind
import dev.ashwake.ui.theme.AshShapes
import dev.ashwake.ui.theme.AshTheme
import dev.ashwake.ui.theme.priorityColor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tasks: TaskRepository,
    private val attachments: TaskAttachmentRepository,
    private val clock: AppClock
) : ViewModel() {
    private val taskId = savedStateHandle.get<String>("taskId")?.toLongOrNull() ?: 0L

    private val taskFlow = if (taskId == 0L) flowOf<Task?>(null) else tasks.observeTask(taskId)
    val task: StateFlow<Task?> = taskFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val attachmentFlow = if (taskId == 0L) flowOf(emptyList()) else attachments.observe(taskId)
    val taskAttachments: StateFlow<List<TaskAttachment>> = attachmentFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleDone() = viewModelScope.launch {
        val current = task.value ?: return@launch
        if (current.isDone) tasks.reopen(current.id) else tasks.complete(current.id)
    }

    fun today() = clock.today()
}

@Composable
fun TaskDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: TaskDetailViewModel = hiltViewModel()
) {
    val task by viewModel.task.collectAsStateWithLifecycle()
    val attachments by viewModel.taskAttachments.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = AshTheme.colors

    Column(Modifier.fillMaxSize().background(colors.background)) {
        AshNavBar(
            title = "Задача",
            onBack = onBack,
            actions = {
                task?.let { current ->
                    IconAction(
                        icon = AshIcons.Edit,
                        contentDescription = "Редактировать задачу",
                        onClick = { onEdit(current.id) }
                    )
                }
            }
        )

        val current = task
        if (current == null) {
            Text(
                text = "Задача не найдена",
                style = AshTheme.type.body,
                color = colors.text2,
                modifier = Modifier.padding(24.dp)
            )
            return@Column
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(colors.surface1, AshShapes.card)
                    .padding(16.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EntityIcon(
                    emoji = current.emoji,
                    iconPath = current.iconPath,
                    size = 42.dp
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = current.title,
                        style = AshTheme.type.headline,
                        color = if (current.isDone) colors.text3 else colors.text,
                        textDecoration = if (current.isDone) TextDecoration.LineThrough else null
                    )
                    taskDueLabel(current, viewModel.today())?.let { due ->
                        Text(due, style = AshTheme.type.subhead, color = colors.text2)
                    }
                }
            }

            if (current.subtasks.isNotEmpty()) {
                ListGroup(header = "Подзадачи") {
                    current.subtasks.forEachIndexed { index, subtask ->
                        ListRow(
                            title = subtask.title,
                            subtitle = if (subtask.isDone) "Выполнено" else "Не выполнено",
                            trailing = {
                                androidx.compose.material3.Icon(
                                    if (subtask.isDone) AshIcons.CheckCircle else AshIcons.Circle,
                                    contentDescription = null,
                                    tint = if (subtask.isDone) colors.success else colors.text3
                                )
                            }
                        )
                        if (index != current.subtasks.lastIndex) ListDivider()
                    }
                }
            }

            current.note?.takeIf { it.isNotBlank() }?.let { note ->
                ListGroup(header = "Заметка") {
                    Text(note, style = AshTheme.type.body, color = colors.text, modifier = Modifier.padding(16.dp))
                }
            }

            if (attachments.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Вложения", style = AshTheme.type.headline, color = colors.text)
                    attachments.forEach { attachment ->
                        Column(
                            Modifier.fillMaxWidth()
                                .background(colors.surface1, AshShapes.card)
                                .tappable {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW)
                                                .setDataAndType(Uri.parse(attachment.contentUri), attachment.mimeType)
                                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        )
                                    }
                                }
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                if (attachment.kind == AttachmentKind.PHOTO) "Фото" else "Файл",
                                style = AshTheme.type.footnote,
                                color = colors.accent
                            )
                            Text(attachment.displayName, style = AshTheme.type.body, color = colors.text)
                            Text(
                                "${attachment.mimeType} · ${formatAttachmentSize(attachment.sizeBytes)} · Нажмите, чтобы открыть",
                                style = AshTheme.type.footnote,
                                color = colors.text2
                            )
                        }
                    }
                }
            }

            ListGroup(header = "Параметры") {
                ListRow(
                    title = "Приоритет",
                    subtitle = priorityLabel(current.priority.name),
                    trailing = {
                        androidx.compose.foundation.layout.Box(
                            Modifier.size(14.dp).background(colors.priorityColor(current.priority), CircleShape)
                        )
                    }
                )
                ListDivider()
                ListRow(
                    title = "Время",
                    subtitle = taskTimeLabel(current)
                )
                if (current.tags.isNotEmpty()) {
                    ListDivider()
                    ListRow(title = "Теги", subtitle = current.tags.joinToString(" ") { "#${it.name}" })
                }
            }

            PrimaryButton(
                text = if (current.isDone) "Вернуть в работу" else "Отметить выполненной",
                onClick = viewModel::toggleDone,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun formatAttachmentSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f МБ".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.1f КБ".format(bytes / 1024.0)
    else -> "$bytes Б"
}

private fun taskDueLabel(task: Task, today: java.time.LocalDate): String? {
    task.dueDate?.let { date ->
        return when {
            date == today -> "Сегодня"
            date == today.plusDays(1) -> "Завтра"
            date < today && !task.isDone -> "Просрочено: ${date.format(DateTimeFormatter.ofPattern("d MMMM"))}"
            else -> date.format(DateTimeFormatter.ofPattern("d MMMM"))
        }
    }
    return "Без срока"
}

private fun taskTimeLabel(task: Task): String = buildList {
    task.dueTime?.let { add("с ${it.format(DateTimeFormatter.ofPattern("HH:mm"))}") }
    task.estimateMinutes?.let { minutes ->
        if (task.dueTime != null) add("до ${task.dueTime.plusMinutes(minutes.toLong()).format(DateTimeFormatter.ofPattern("HH:mm"))}")
        else add("$minutes мин")
    }
    task.recurrence?.let { add("Повторяется") }
}.joinToString(" · ").ifBlank { "Не задано" }

private fun priorityLabel(priority: String): String = when (priority) {
    "P1" -> "Срочно и важно"
    "P2" -> "Важно"
    "P3" -> "Обычный"
    else -> "Без приоритета"
}
