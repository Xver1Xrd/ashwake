package dev.ashwake.ui.habits.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ashwake.domain.model.habits.HabitType
import dev.ashwake.domain.model.habits.HabitWithProgress
import dev.ashwake.domain.model.habits.SkipReason
import androidx.compose.ui.res.stringResource
import dev.ashwake.R

/**
 * Лист действий по долгому тапу: счётчик, заметка, пропуск с причиной,
 * заморозка, пауза и архив.
 *
 * Причина пропуска спрашивается здесь, а не отдельным диалогом после свайпа:
 * данные для аналитики «почему привычка сыпется» собираются только если
 * ответить на вопрос дешевле, чем его закрыть.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HabitActionsSheet(
    progress: HabitWithProgress,
    skipReasons: List<SkipReason>,
    onSetCounter: (Float) -> Unit,
    onNote: (String?) -> Unit,
    onSkip: (Long?) -> Unit,
    onFreeze: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val habit = progress.habit
    var note by remember { mutableStateOf(progress.todayEntry?.note.orEmpty()) }
    var counter by remember { mutableFloatStateOf(progress.todayValue) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(habit.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "score ${(progress.score * 100).toInt()}% · серия ${progress.currentStreak} · рекорд ${progress.recordStreak}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (habit.type == HabitType.COUNTER) {
                HorizontalDivider()
                Text(
                    "Сегодня: ${formatValue(counter)} из ${formatValue(habit.targetValue)}" +
                        (habit.unitName?.let { " $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = counter,
                    onValueChange = { counter = it },
                    onValueChangeFinished = { onSetCounter(counter) },
                    valueRange = 0f..(habit.targetValue * 1.5f)
                )
            }

            HorizontalDivider()
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.components_zametka_k_otmetke)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                trailingIcon = {
                    TextButton(onClick = { onNote(note.takeIf { it.isNotBlank() }) }) {
                        Text(stringResource(R.string.components_ok))
                    }
                }
            )

            HorizontalDivider()
            Text(stringResource(R.string.components_propustit_i_ukazat_prichinu), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                skipReasons.forEach { reason ->
                    AssistChip(
                        onClick = { onSkip(reason.id) },
                        label = { Text(reason.label) }
                    )
                }
                AssistChip(onClick = { onSkip(null) }, label = { Text(stringResource(R.string.components_bez_prichiny)) })
            }

            HorizontalDivider()
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onFreeze,
                    enabled = progress.freezesLeftThisMonth > 0
                ) {
                    Icon(FreezeIcon, contentDescription = null)
                    Text("  Заморозить (${progress.freezesLeftThisMonth})")
                }
                OutlinedButton(onClick = if (progress.paused) onResume else onPause) {
                    Text(if (progress.paused) "Снять паузу" else "Пауза")
                }
            }

            if (progress.todayEntry != null) {
                TextButton(onClick = onClear) { Text(stringResource(R.string.components_snyat_otmetku_za_segodnya)) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                    Text(stringResource(R.string.components_izmenit))
                }
                OutlinedButton(onClick = onArchive) {
                    Icon(Icons.Filled.Archive, contentDescription = null)
                    Text(stringResource(R.string.components_v_arhiv))
                }
            }
        }
    }
}
