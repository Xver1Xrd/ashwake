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
                    taskMeta(current, viewModel.today())?.let { meta ->
                        Text(meta, style = AshTheme.type.subhead, color = colors.text2)
                    }
                    if (current.tags.isNotEmpty()) {
                        Text(
                            current.tags.joinToString(" ") { "#${it.name}" },
                            style = AshTheme.type.footnote,
                            color = colors.accent
                        )
                    }
                }
            }

            current.note?.takeIf { it.isNotBlank() }?.let { note ->
                ListGroup(header = "Описание") {
                    Text(
                        note,
                        style = AshTheme.type.body,
                        color = colors.text,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            ListGroup(header = "Детали") {
                ListRow(title = "Приоритет", subtitle = current.priority.name)
                ListDivider()
                ListRow(title = "Сложность", subtitle = "${current.difficulty} из 5")
                current.recurrence?.let { rule ->
                    ListDivider()
                    ListRow(title = "Расписание", subtitle = recurrenceText(rule.type.name, rule.intervalDays))
                }
                current.persistentReminderMinutes?.let { minutes ->
                    ListDivider()
                    ListRow(title = "Напоминание", subtitle = "Каждые $minutes мин до выполнения")
                }
                current.sourceLink?.let { link ->
                    ListDivider()
                    ListRow(title = "Ссылка", subtitle = link)
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

private fun recurrenceText(type: String, interval: Int?): String = when (type) {
    "DAILY" -> "Каждый день"
    "EVERY_N_DAYS" -> "Каждые ${interval ?: 1} дн."
    "WEEKDAYS" -> "По дням недели"
    "DAY_OF_MONTH" -> "По числам месяца"
    else -> type
}

private fun taskMeta(task: Task, today: java.time.LocalDate): String? {
    val values = mutableListOf<String>()
    task.dueDate?.let { date ->
        values += when {
            date == today -> "Сегодня"
            date == today.plusDays(1) -> "Завтра"
            date < today && !task.isDone -> "Просрочено: ${date.format(DateTimeFormatter.ofPattern("d MMMM"))}"
            else -> date.format(DateTimeFormatter.ofPattern("d MMMM"))
        }
    }
    task.dueTime?.let { values += it.format(DateTimeFormatter.ofPattern("HH:mm")) }
    task.estimateMinutes?.let { values += "$it мин" }
    return values.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
