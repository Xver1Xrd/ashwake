package dev.ashwake.ui.tasks.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ashwake.domain.model.tasks.StaleResolution
import dev.ashwake.domain.model.tasks.Task

/**
 * Диалог залежавшейся задачи: открывается на 5-м переносе (п. 1).
 *
 * Формулировки нейтральные — это разбор завала, а не выговор. Вариант «оставить»
 * есть всегда: если человек его выбирает третий раз подряд, это данные для аналитики,
 * а не повод не дать закрыть окно.
 */
@Composable
fun StaleTaskDialog(
    task: Task,
    onResolve: (StaleResolution, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember { mutableStateOf<StaleResolution?>(null) }
    var payload by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Задача переносится ${task.postponeCount}-й раз") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(task.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Похоже, в текущем виде она не двигается. Что с ней сделать?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                when (mode) {
                    StaleResolution.DELEGATE -> OutlinedTextField(
                        value = payload,
                        onValueChange = { payload = it },
                        label = { Text("Кому делегировать") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    StaleResolution.SPLIT -> OutlinedTextField(
                        value = payload,
                        onValueChange = { payload = it },
                        label = { Text("Подзадачи, по одной в строке") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )

                    else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(
                            onClick = { onResolve(StaleResolution.DELETE, null) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Удалить") }

                        OutlinedButton(
                            onClick = { mode = StaleResolution.DELEGATE },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Делегировать") }

                        OutlinedButton(
                            onClick = { mode = StaleResolution.SPLIT },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Разбить на подзадачи") }

                        OutlinedButton(
                            onClick = { onResolve(StaleResolution.SCHEDULE_SLOT, null) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Поставить на сегодня") }
                    }
                }
            }
        },
        confirmButton = {
            if (mode != null) {
                TextButton(
                    onClick = { onResolve(mode!!, payload.takeIf { it.isNotBlank() }) },
                    enabled = payload.isNotBlank()
                ) { Text("Готово") }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (mode != null) mode = null else onResolve(StaleResolution.KEEP, null)
            }) {
                Text(if (mode != null) "Назад" else "Оставить как есть")
            }
        }
    )
}
