package dev.ashwake.ui.tasks.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.ashwake.domain.engine.nlp.ParsedQuickInput
import dev.ashwake.ui.theme.PriorityColors
import java.time.format.DateTimeFormatter

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Быстрый ввод одной строкой (п. 1, 11).
 *
 * Под полем показываются чипы того, что распозналось: без них пользователь
 * не понимает, сработал парсер или «завтра» осталось частью названия.
 */
@Composable
fun QuickAddBar(
    value: String,
    parsed: ParsedQuickInput?,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onVoiceClick: () -> Unit,
    modifier: Modifier = Modifier,
    listening: Boolean = false
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (parsed != null && parsed.hasAnyMarkup) {
                ParsedChips(parsed)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("купить молоко завтра 18:00 !p2 #дом ~30м") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = onVoiceClick) {
                            Icon(
                                Icons.Filled.Mic,
                                contentDescription = "Голосовой ввод",
                                // Во время записи микрофон подсвечен: иначе непонятно,
                                // слушает приложение или нет
                                tint = if (listening) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = { onSubmit() }
                    )
                )
                FilledIconButton(onClick = onSubmit, enabled = value.isNotBlank()) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Добавить")
                }
            }
        }
    }
}

@Composable
private fun ParsedChips(parsed: ParsedQuickInput) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        parsed.date?.let { Chip(it.format(DATE_FORMAT)) }
        parsed.time?.let { Chip(it.format(TIME_FORMAT)) }
        parsed.priority?.let {
            Chip(it.name, PriorityColors[it.ordinal])
        }
        parsed.estimateMinutes?.let { Chip("~${it}м") }
        parsed.tagNames.forEach { Chip("#$it") }
    }
}

@Composable
private fun Chip(label: String, color: androidx.compose.ui.graphics.Color? = null) {
    AssistChip(
        onClick = {},
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        colors = if (color != null) {
            AssistChipDefaults.assistChipColors(labelColor = color)
        } else {
            AssistChipDefaults.assistChipColors()
        }
    )
}
