package dev.ashwake.ui.tasks.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ashwake.ui.components.AshIcons
import dev.ashwake.ui.components.AshNavBar
import dev.ashwake.ui.components.EntityIcon
import dev.ashwake.ui.components.IconAction
import dev.ashwake.ui.tasks.components.AttachmentGrid
import dev.ashwake.ui.theme.AshTheme
import java.time.format.DateTimeFormatter

@Composable
fun TaskDetailScreen(onBack: () -> Unit, onEdit: () -> Unit, viewModel: TaskDetailViewModel = hiltViewModel()) {
    val task by viewModel.task.collectAsStateWithLifecycle()
    val colors = AshTheme.colors
    Column(Modifier.fillMaxSize().background(colors.background)) {
        AshNavBar(title = "Задача", onBack = onBack, actions = {
            IconAction(AshIcons.Edit, "Редактировать", onClick = onEdit)
        })
        task?.let { item ->
            Column(
                Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                EntityIcon(item.emoji, item.iconPath, size = 52.dp)
                Text(item.title, style = AshTheme.type.headline, color = colors.text)
                item.note?.let { Text(it, style = AshTheme.type.body, color = colors.text2) }
                if (item.attachments.isNotEmpty()) {
                    Text("Вложения", style = AshTheme.type.subhead, color = colors.text2)
                    AttachmentGrid(item.attachments, onAdd = onEdit, onRemove = viewModel::removeAttachment)
                }
                Text("Параметры", style = AshTheme.type.subhead, color = colors.text2)
                Text("Приоритет: ${item.priority.name}", style = AshTheme.type.body, color = colors.text)
                item.dueDate?.let { date ->
                    val time = item.dueTime?.format(DateTimeFormatter.ofPattern("HH:mm"))
                    Text("Срок: $date${time?.let { " · $it" }.orEmpty()}", style = AshTheme.type.body, color = colors.text)
                }
                if (item.subtasks.isNotEmpty()) {
                    Text("Подзадачи", style = AshTheme.type.subhead, color = colors.text2)
                    item.subtasks.forEach { subtask ->
                        Text(if (subtask.isDone) "✓ ${subtask.title}" else "○ ${subtask.title}", style = AshTheme.type.body, color = colors.text)
                    }
                }
                Button(modifier = Modifier.fillMaxWidth(), onClick = viewModel::toggleDone) {
                    Text(if (item.isDone) "Вернуть в работу" else "Отметить выполненной")
                }
            }
        }
    }
}
