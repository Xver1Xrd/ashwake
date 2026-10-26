package dev.ashwake.ui.tasks.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.ashwake.domain.model.tasks.TaskAttachment
import dev.ashwake.ui.components.AshIcons
import dev.ashwake.ui.components.ChipButton
import dev.ashwake.ui.theme.AshShapes
import dev.ashwake.ui.theme.AshTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Вложения показываются карточками; фото и файлы добавляются разными действиями. */
@Composable
fun AttachmentGrid(
    attachments: List<TaskAttachment>,
    onAddPhoto: (() -> Unit)? = null,
    onAddFile: (() -> Unit)? = null,
    addEnabled: Boolean = true,
    onRemove: (TaskAttachment) -> Unit
) {
    var opened by remember { mutableStateOf<TaskAttachment?>(null) }
    var pendingDelete by remember { mutableStateOf<TaskAttachment?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        attachments.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { attachment ->
                    AttachmentTile(attachment, Modifier.weight(1f), { opened = it }) { pendingDelete = it }
                }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
        if (onAddPhoto != null || onAddFile != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onAddPhoto?.let { action ->
                    ChipButton("Фото", icon = Icons.Filled.Image, enabled = addEnabled, onClick = action)
                }
                onAddFile?.let { action ->
                    ChipButton("Файл", icon = Icons.Filled.AttachFile, enabled = addEnabled, onClick = action)
                }
            }
        }
    }

    opened?.let { attachment -> AttachmentViewer(attachment) { opened = null } }
    pendingDelete?.let { attachment ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Удалить вложение?") },
            text = { Text("Вложение будет удалено из задачи без возможности восстановления.") },
            confirmButton = { TextButton(onClick = { onRemove(attachment); pendingDelete = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun AttachmentTile(
    attachment: TaskAttachment,
    modifier: Modifier,
    onOpen: (TaskAttachment) -> Unit,
    onDelete: (TaskAttachment) -> Unit
) {
    val colors = AshTheme.colors
    Box(
        modifier.height(144.dp).clip(AshShapes.group).background(colors.surface2)
            .clickable { onOpen(attachment) },
        contentAlignment = Alignment.Center
    ) {
        if (attachment.isImage) AttachmentImage(attachment, Modifier.matchParentSize())
        else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(12.dp)) {
            Icon(AshIcons.Archive, contentDescription = null, tint = colors.accent, modifier = Modifier.size(30.dp))
            Text(attachment.displayName, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(fileSize(attachment.sizeBytes), style = AshTheme.type.footnote, color = colors.text2)
        }
        Box(Modifier.align(Alignment.TopEnd).padding(6.dp).clickable { onDelete(attachment) }) {
            Icon(AshIcons.Close, "Удалить", tint = colors.text)
        }
    }
}

@Composable
private fun AttachmentViewer(attachment: TaskAttachment, close: () -> Unit) {
    if (attachment.isImage) {
        var scale by remember(attachment.id) { mutableStateOf(1f) }
        var offsetX by remember(attachment.id) { mutableStateOf(0f) }
        var offsetY by remember(attachment.id) { mutableStateOf(0f) }
        Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(attachment.id) {
                awaitEachGesture {
                    awaitFirstDown()
                    do {
                        val event = awaitPointerEvent()
                        val pan = event.calculatePan()
                        scale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = (offsetY + pan.y).coerceAtLeast(0f)
                        }
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    } while (event.changes.any { it.pressed })
                    if (scale <= 1f) {
                        if (offsetY > 160.dp.toPx()) close() else offsetY = 0f
                    }
                }
            }) {
                AttachmentImage(attachment, Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale; scaleY = scale; translationX = offsetX; translationY = offsetY
                }, ContentScale.Fit)
                TextButton(onClick = close, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                    Text("Закрыть", color = Color.White)
                }
            }
        }
        return
    }
    AlertDialog(
        onDismissRequest = close,
        confirmButton = { TextButton(onClick = close) { Text("Закрыть") } },
        text = {
            if (attachment.isImage) AttachmentImage(attachment, Modifier.fillMaxWidth())
            else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(attachment.displayName, style = AshTheme.type.headline)
                Text(attachment.mimeType, style = AshTheme.type.body)
                Text(fileSize(attachment.sizeBytes), style = AshTheme.type.body)
            }
        }
    )
}

@Composable
private fun AttachmentImage(attachment: TaskAttachment, modifier: Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val file = File(LocalContext.current.filesDir, "task_attachments/${attachment.fileName}")
    var bitmap by remember(file.path, contentScale) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(file.path, contentScale) {
        bitmap = withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            val limit = if (contentScale == ContentScale.Fit) 2048 else 512
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > limit * 2) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
    bitmap?.let { Image(it.asImageBitmap(), null, modifier, contentScale = contentScale) }
}

private fun fileSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f МБ".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.1f КБ".format(bytes / 1024.0)
    else -> "$bytes Б"
}
