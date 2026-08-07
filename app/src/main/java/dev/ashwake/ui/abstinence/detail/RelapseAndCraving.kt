package dev.ashwake.ui.abstinence.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ashwake.domain.model.abstinence.Abstinence
import dev.ashwake.domain.model.abstinence.CravingTrigger
import dev.ashwake.domain.model.abstinence.RelapseReason
import dev.ashwake.ui.theme.Blood
import dev.ashwake.ui.theme.Steel
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

/**
 * Регистрация срыва.
 *
 * Кнопка, ведущая сюда, спрятана за подтверждением и не стоит на видном месте (п. 4).
 * Формулировки не обвиняющие: экран после срыва показывает, сколько человек
 * продержался, а не сколько потерял.
 */
@Composable
fun RelapseDialog(
    reasons: List<RelapseReason>,
    onConfirm: (reasonId: Long?, note: String?, at: Instant?) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedReason by remember { mutableStateOf<Long?>(null) }
    var note by remember { mutableStateOf("") }
    var hoursAgo by remember { mutableIntStateOf(0) }
    var confirmed by remember { mutableStateOf(false) }

    if (!confirmed) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Отметить срыв?") },
            text = {
                Text(
                    "Счётчик начнёт новую попытку. История никуда не денется: " +
                        "рекорд и общее число чистых дней сохранятся.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = { TextButton(onClick = { confirmed = true }) { Text("Да") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Что случилось") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Причина", style = MaterialTheme.typography.labelLarge)
                ReasonChips(reasons, selectedReason) { selectedReason = it }

                Text("Когда", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 3, 12, 24).forEach { hours ->
                        FilterChip(
                            selected = hoursAgo == hours,
                            onClick = { hoursAgo = hours },
                            label = { Text(if (hours == 0) "сейчас" else "$hours ч назад") }
                        )
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Заметка") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    selectedReason,
                    note.takeIf { it.isNotBlank() },
                    Instant.now().minus(Duration.ofHours(hoursAgo.toLong()))
                )
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReasonChips(
    reasons: List<RelapseReason>,
    selected: Long?,
    onSelect: (Long?) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        reasons.forEach { reason ->
            FilterChip(
                selected = selected == reason.id,
                onClick = { onSelect(if (selected == reason.id) null else reason.id) },
                label = { Text(reason.label) }
            )
        }
    }
}

/**
 * Лист «Тяжело»: личный текст, заместители, дыхательный таймер, фиксация исхода.
 *
 * Порядок шагов не случаен — сначала напоминание «зачем», потом действие,
 * и только в конце вопрос о результате. Спрашивать про интенсивность в момент,
 * когда накрыло, бессмысленно: человеку нужно занятие, а не анкета.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CravingSheet(
    abstinence: Abstinence,
    triggers: List<CravingTrigger>,
    onStart: (intensity: Int, triggerId: Long?) -> Unit,
    onFinish: (resisted: Boolean, durationSeconds: Int?, note: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var intensity by remember { mutableIntStateOf(3) }
    var triggerId by remember { mutableStateOf<Long?>(null) }
    var started by remember { mutableStateOf(false) }
    var breathingSeconds by remember { mutableIntStateOf(0) }
    var breathingRunning by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }

    LaunchedEffect(breathingRunning) {
        while (breathingRunning) {
            delay(1_000)
            breathingSeconds++
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Сейчас тяжело", style = MaterialTheme.typography.titleMedium)

            abstinence.motivationText?.let { text ->
                Text(
                    text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider()
            }

            if (abstinence.substitutes.isNotEmpty()) {
                Text("Можно сделать вместо", style = MaterialTheme.typography.labelLarge)
                abstinence.substitutes.forEach { substitute ->
                    Text("· ${substitute.text}", style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider()
            }

            BreathingTimer(
                seconds = breathingSeconds,
                running = breathingRunning,
                onToggle = {
                    breathingRunning = !breathingRunning
                    if (breathingRunning && !started) {
                        started = true
                        onStart(intensity, triggerId)
                    }
                }
            )

            HorizontalDivider()
            Text("Насколько сильно", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..5).forEach { level ->
                    FilterChip(
                        selected = intensity == level,
                        onClick = { intensity = level },
                        label = { Text("$level") }
                    )
                }
            }

            Text("Что подтолкнуло", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                triggers.forEach { trigger ->
                    FilterChip(
                        selected = triggerId == trigger.id,
                        onClick = { triggerId = if (triggerId == trigger.id) null else trigger.id },
                        label = { Text(trigger.label) }
                    )
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Заметка") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (!started) onStart(intensity, triggerId)
                        onFinish(true, breathingSeconds.takeIf { it > 0 }, note.takeIf { it.isNotBlank() })
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Переждал") }

                OutlinedButton(
                    onClick = {
                        if (!started) onStart(intensity, triggerId)
                        onFinish(false, breathingSeconds.takeIf { it > 0 }, note.takeIf { it.isNotBlank() })
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Не помогло") }
            }
        }
    }
}

/**
 * Дыхательный таймер на 2–5 минут: круг расширяется на вдохе и сжимается на выдохе.
 * Цикл 4-4-4 секунды — достаточно медленный, чтобы за ним было легко следовать.
 */
@Composable
private fun BreathingTimer(seconds: Int, running: Boolean, onToggle: () -> Unit) {
    val phase = (seconds % CYCLE_SECONDS)
    val inhaling = phase < PHASE_SECONDS
    val holding = phase in PHASE_SECONDS until PHASE_SECONDS * 2

    val targetScale = when {
        !running -> 0.6f
        inhaling -> 1f
        holding -> 1f
        else -> 0.6f
    }
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(durationMillis = PHASE_SECONDS * 1000),
        label = "breathing"
    )
    val color = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(140.dp)) {
                drawCircle(color = color.copy(alpha = 0.2f), radius = size.minDimension / 2f)
                drawCircle(color = color, radius = size.minDimension / 2f * scale)
            }
            Text(
                text = if (!running) "старт" else when {
                    inhaling -> "вдох"
                    holding -> "держим"
                    else -> "выдох"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
        Text(
            "%d:%02d".format(seconds / 60, seconds % 60),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            "Две–пять минут обычно достаточно, чтобы волна прошла",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        OutlinedButton(onClick = onToggle) {
            Text(if (running) "Пауза" else "Начать дышать")
        }
    }
}

/**
 * Однократный нейтральный экран для отказов, связанных с алкоголем или веществами (п. 4).
 * Без нравоучений и без повторов: галочка «понятно» и больше он не появляется.
 */
@Composable
fun SubstanceWarningDialog(onAcknowledge: () -> Unit) {
    AlertDialog(
        onDismissRequest = onAcknowledge,
        title = { Text("Одно замечание") },
        text = {
            Text(
                "Резкий отказ от алкоголя или веществ может быть небезопасен. " +
                    "Имеет смысл обсудить это с врачом.\n\n" +
                    "Приложение считает дни и ведёт историю. Медицинских рекомендаций " +
                    "оно не даёт.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = { TextButton(onClick = onAcknowledge) { Text("Понятно") } }
    )
}

/** Кнопка срыва: приглушённая и внизу, а не на видном месте. */
@Composable
fun RelapseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text("Отметить срыв", color = Blood.copy(alpha = 0.8f))
    }
}

/** Кнопка отмены ошибочного срыва — видна только первые сутки. */
@Composable
fun UndoRelapseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Text("Отменить срыв", color = Steel)
    }
}

private const val PHASE_SECONDS = 4
private const val CYCLE_SECONDS = PHASE_SECONDS * 3
