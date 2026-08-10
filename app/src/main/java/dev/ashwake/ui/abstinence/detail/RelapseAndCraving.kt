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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import dev.ashwake.ui.components.AshTextField
import dev.ashwake.ui.components.PrimaryButton
import dev.ashwake.ui.components.TextAction
import dev.ashwake.ui.components.ChipButton
import dev.ashwake.ui.theme.AshTheme
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
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import androidx.compose.ui.res.stringResource
import dev.ashwake.R

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
            title = { Text(stringResource(R.string.detail_otmetit_sryv)) },
            text = {
                Text(
                    "Счётчик начнёт новую попытку. История никуда не денется: рекорд и общее число чистых дней сохранятся.",
                    style = AshTheme.type.callout
                )
            },
            confirmButton = { TextAction(text = stringResource(R.string.detail_da), onClick = { confirmed = true }) },
            dismissButton = { TextAction(text = stringResource(R.string.detail_otmena), onClick = onDismiss) }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.detail_chto_sluchilos)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.detail_prichina), style = AshTheme.type.subhead)
                ReasonChips(reasons, selectedReason) { selectedReason = it }

                Text(stringResource(R.string.detail_kogda), style = AshTheme.type.subhead)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 3, 12, 24).forEach { hours ->
                        ChipButton(
                        text = if (hours == 0) "сейчас" else "$hours ч назад",
                        selected = hoursAgo == hours,
                        onClick = { hoursAgo = hours }
                    )
                    }
                }

                AshTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = stringResource(R.string.detail_zametka),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            TextAction(
                text = stringResource(R.string.detail_sohranit),
                onClick = {
                    onConfirm(
                        selectedReason,
                        note.takeIf { it.isNotBlank() },
                        Instant.now().minus(Duration.ofHours(hoursAgo.toLong()))
                    )
                }
            )
        },
        dismissButton = { TextAction(text = stringResource(R.string.detail_otmena), onClick = onDismiss) }
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
            ChipButton(
                        text = reason.label,
                        selected = selected == reason.id,
                        onClick = { onSelect(if (selected == reason.id) null else reason.id) }
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
    onRelapse: (durationSeconds: Int?, note: String?) -> Unit,
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
            Text(stringResource(R.string.detail_seychas_tyazhelo), style = AshTheme.type.title3)

            abstinence.motivationText?.let { text ->
                Text(
                    text,
                    style = AshTheme.type.body,
                    modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider()
            }

            if (abstinence.substitutes.isNotEmpty()) {
                Text(stringResource(R.string.detail_mozhno_sdelat_vmesto), style = AshTheme.type.subhead)
                abstinence.substitutes.forEach { substitute ->
                    Text("· ${substitute.text}", style = AshTheme.type.callout)
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
            Text(stringResource(R.string.detail_naskolko_silno), style = AshTheme.type.subhead)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..5).forEach { level ->
                    ChipButton(
                        text = "$level",
                        selected = intensity == level,
                        onClick = { intensity = level }
                    )
                }
            }

            Text(stringResource(R.string.detail_chto_podtolknulo), style = AshTheme.type.subhead)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                triggers.forEach { trigger ->
                    ChipButton(
                        text = trigger.label,
                        selected = triggerId == trigger.id,
                        onClick = { triggerId = if (triggerId == trigger.id) null else trigger.id }
                    )
                }
            }

            AshTextField(
                value = note,
                onValueChange = { note = it },
                label = stringResource(R.string.detail_zametka),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            HorizontalDivider()

            // Три исхода вместо двух. «Не помогло» раньше означало только
            // «тяга не прошла», но читалось как отметка срыва — теперь срыв
            // это отдельный, названный своим словом выход
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(
                    text = "Справился",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (!started) onStart(intensity, triggerId)
                        onFinish(true, breathingSeconds.takeIf { it > 0 }, note.takeIf { it.isNotBlank() })
                    }
                )

                ChipButton(
                    text = "Отпустило само",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (!started) onStart(intensity, triggerId)
                        onFinish(false, breathingSeconds.takeIf { it > 0 }, note.takeIf { it.isNotBlank() })
                    }
                )
            }

            TextAction(
                text = "Не справился — отметить срыв",
                color = AshTheme.colors.danger,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (!started) onStart(intensity, triggerId)
                    onRelapse(
                        breathingSeconds.takeIf { it > 0 },
                        note.takeIf { it.isNotBlank() }
                    )
                }
            )

            Text(
                "Пока вы не отметили срыв, счётчик идёт дальше",
                style = AshTheme.type.footnote,
                color = AshTheme.colors.text2
            )
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

    // Круг движется по синусоиде, а не линейно между двумя размерами.
    // Дыхание само по себе синусоидально: линейное расширение с рывком на
    // границе фазы сбивает с ритма ровно того, кому за ним надо следовать.
    val progress = remember { Animatable(0f) }
    LaunchedEffect(running, phase) {
        if (!running) {
            progress.animateTo(0f, tween(600))
            return@LaunchedEffect
        }
        when {
            inhaling -> progress.animateTo(1f, tween(PHASE_SECONDS * 1000, easing = EaseInOutSine))
            holding -> progress.animateTo(1f, tween(200))
            else -> progress.animateTo(0f, tween(PHASE_SECONDS * 1000, easing = EaseInOutSine))
        }
    }

    val colors = AshTheme.colors
    val color = colors.cold
    val scale = 0.55f + 0.45f * progress.value

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(160.dp)) {
                val radius = size.minDimension / 2f

                // Подсветка на задержке дыхания: круг замер, и без свечения
                // непонятно, идёт ли ещё отсчёт
                val glow = if (holding && running) 1f else 0f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = 0.35f + 0.25f * glow),
                            color.copy(alpha = 0f)
                        ),
                        radius = radius * scale * 1.6f
                    ),
                    radius = radius * scale * 1.6f
                )
                drawCircle(color = color.copy(alpha = 0.18f), radius = radius)
                drawCircle(color = color, radius = radius * scale)
            }
            Text(
                text = if (!running) "старт" else when {
                    inhaling -> "вдох"
                    holding -> "держим"
                    else -> "выдох"
                },
                style = AshTheme.type.subhead,
                color = if (colors.isDark) Color.Black else Color.White
            )
        }
        Text(
            "%d:%02d".format(seconds / 60, seconds % 60),
            style = AshTheme.type.title3
        )
        Text(
            "Две–пять минут обычно достаточно, чтобы волна прошла",
            style = AshTheme.type.footnote,
            color = AshTheme.colors.text2,
            textAlign = TextAlign.Center
        )
        ChipButton(text = if (running) "Пауза" else "Начать дышать", onClick = onToggle)
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
        title = { Text(stringResource(R.string.detail_odno_zamechanie)) },
        text = {
            Text(
                "Резкий отказ от алкоголя или веществ может быть небезопасен. " +
                    "Имеет смысл обсудить это с врачом.\n\n" +
                    "Приложение считает дни и ведёт историю. Медицинских рекомендаций оно не даёт.",
                style = AshTheme.type.callout
            )
        },
        confirmButton = { TextAction(text = stringResource(R.string.detail_ponyatno), onClick = onAcknowledge) }
    )
}

/** Кнопка срыва: приглушённая и внизу, а не на видном месте. */
@Composable
fun RelapseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextAction(
        text = stringResource(R.string.detail_otmetit_sryv_2), color = Blood.copy(alpha = 0.8f),
        modifier = modifier,
        onClick = onClick
    )
}

/** Кнопка отмены ошибочного срыва — видна только первые сутки. */
@Composable
fun UndoRelapseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextAction(
        text = stringResource(R.string.detail_otmenit_sryv),
        color = AshTheme.colors.text2,
        modifier = modifier,
        onClick = onClick
    )
}

private const val PHASE_SECONDS = 4
private const val CYCLE_SECONDS = PHASE_SECONDS * 3
