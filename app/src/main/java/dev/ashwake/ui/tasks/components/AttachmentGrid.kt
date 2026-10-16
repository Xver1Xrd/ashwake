package dev.ashwake.ui.tasks.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.ashwake.domain.model.tasks.TaskAttachment
import dev.ashwake.ui.components.AshIcons
import dev.ashwake.ui.theme.AshShapes
import dev.ashwake.ui.theme.AshTheme
import java.io.File

@Composable
fun AttachmentGrid(attachments: List<TaskAttachment>, onAdd: () -> Unit, onRemove: (TaskAttachment) -> Unit) {
    var opened by remember { mutableStateOf<TaskAttachment?>(null) }
    var pendingDelete by remember { mutableStateOf<TaskAttachment?>(null) }
    val cells = attachments + listOf<TaskAttachment?>(null)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { attachment ->
                    AttachmentTile(attachment, Modifier.weight(1f), onAdd, { opened = it }) { pendingDelete = it }
                }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
    opened?.let { attachment -> AttachmentViewer(attachment) { opened = null } }
    pendingDelete?.let { attachment ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Удалить фото?") },
            text = { Text("Вложение будет удалено из задачи без возможности восстановления.") },
            confirmButton = { TextButton(onClick = { onRemove(attachment); pendingDelete = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun AttachmentTile(attachment: TaskAttachment?, modifier: Modifier, onAdd: () -> Unit, onOpen: (TaskAttachment) -> Unit, onDelete: (TaskAttachment) -> Unit) {
    val colors = AshTheme.colors
    Box(modifier.height(144.dp).clip(AshShapes.group).background(colors.surface2).clickable { if (attachment == null) onAdd() else onOpen(attachment) }, contentAlignment = Alignment.Center) {
        if (attachment == null) Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(AshIcons.Add, null, tint = colors.accent); Text("Добавить", color = colors.text2) }
        else {
            AttachmentImage(attachment, Modifier.matchParentSize())
            Box(Modifier.align(Alignment.TopEnd).padding(6.dp).clickable { onDelete(attachment) }) { Icon(AshIcons.Close, "Удалить", tint = colors.text) }
        }
    }
}

@Composable private fun AttachmentViewer(attachment: TaskAttachment, close: () -> Unit) {
    AlertDialog(onDismissRequest = close, confirmButton = { TextButton(onClick = close) { Text("Закрыть") } }, text = { AttachmentImage(attachment, Modifier.fillMaxWidth()) })
}

@Composable private fun AttachmentImage(attachment: TaskAttachment, modifier: Modifier) {
    val file = File(LocalContext.current.filesDir, "task_attachments/${attachment.fileName}")
    val bitmap = remember(file.path) { BitmapFactory.decodeFile(file.path) }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    }
}
