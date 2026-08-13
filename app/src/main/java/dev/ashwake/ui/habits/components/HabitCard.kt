package dev.ashwake.ui.habits.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ashwake.ui.components.PrimaryButton
import dev.ashwake.ui.components.TextAction
import dev.ashwake.domain.model.habits.HabitType
import dev.ashwake.domain.model.habits.HabitWithProgress
import dev.ashwake.ui.theme.Ember
import dev.ashwake.ui.theme.Gold
import dev.ashwake.ui.theme.Moss
import dev.ashwake.ui.theme.AshTheme
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import dev.ashwake.R

/**
 * Карточка привычки: score и стрик рядом, а не вместо друг друга (п. 3).
 *
 * Тап — отметка, долгий тап — подробности со счётчиком и заметкой.
 * Кнопка «Минимум» появляется только у привычек, где минимальная планка задана:
 * пустая вторая кнопка на каждой карточке была бы шумом.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HabitCard(
    progress: HabitWithProgress,
    onPrimaryAction: () -> Unit,
    onMinimum: () -> Unit,
    onLongClick: () -> Unit,
    onOpenDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    val habit = progress.habit

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AshTheme.colors.surface1)
            .combinedClickable(onClick = onOpenDetail, onLongClick = onLongClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(
                score = progress.score,
                done = progress.doneToday,
                modifier = Modifier.size(44.dp)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = habit.name,
                    style = AshTheme.type.body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle(progress),
                    style = AshTheme.type.footnote,
                    color = AshTheme.colors.text2
                )
            }

            if (progress.paused) {
                Icon(
                    Icons.Filled.PauseCircle,
                    contentDescription = stringResource(R.string.components_na_pauze),
                    tint = AshTheme.colors.text2
                )
            } else {
                PrimaryButton(progress, onPrimaryAction)
            }
        }

        if (habit.hasMinimum && !progress.doneToday && !progress.paused) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextAction(text = stringResource(R.string.components_minimum_1_s, formatValue(habit.minimumValue ?: 0f)), onClick = onMinimum)
            }
        }
    }
}

@Composable
private fun PrimaryButton(progress: HabitWithProgress, onClick: () -> Unit) {
    val habit = progress.habit
    FilledTonalButton(onClick = onClick, contentPadding = PaddingValuesCompact) {
        when {
            habit.type == HabitType.COUNTER -> {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(
                    "  ${formatValue(progress.todayValue)}/${formatValue(habit.targetValue)}",
                    style = AshTheme.type.footnote
                )
            }

            progress.doneToday -> {
                Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.components_snyat_otmetku),
                    modifier = Modifier.size(18.dp))
            }

            else -> Text(stringResource(R.string.components_otmetit), style = AshTheme.type.footnote)
        }
    }
}

/**
 * Кольцо score. Цвет — ступенчатый, потому что непрерывный градиент
 * не читается: важно отличить «держится» от «сыпется», а не 62% от 64%.
 */
@Composable
private fun ScoreRing(score: Float, done: Boolean, modifier: Modifier = Modifier) {
    val color = when {
        score >= 0.7f -> Moss
        score >= 0.4f -> Gold
        else -> Ember
    }
    val track = AshTheme.colors.surface2

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth()) {
            val stroke = 4.dp.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = androidx.compose.ui.geometry.Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f
            )
            drawArc(
                color = track,
                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = Size(diameter, diameter),
                style = Stroke(width = stroke)
            )
            drawArc(
                color = color,
                startAngle = -90f, sweepAngle = 360f * score.coerceIn(0f, 1f), useCenter = false,
                topLeft = topLeft, size = Size(diameter, diameter),
                style = Stroke(width = stroke)
            )
        }
        Text(
            text = "${(score * 100).roundToInt()}",
            style = AshTheme.type.footnote,
            color = if (done) color else AshTheme.colors.text
        )
    }
}

@Composable
private fun subtitle(progress: HabitWithProgress): String {
    val parts = mutableListOf<String>()
    parts += stringResource(R.string.components_seriya_1_s, progress.currentStreak)
    if (progress.recordStreak > progress.currentStreak) {
        parts += stringResource(R.string.abstinence_rekord_1_s, progress.recordStreak)
    }
    if (progress.freezesLeftThisMonth > 0) {
        parts += stringResource(R.string.components_zamorozki_1_s, progress.freezesLeftThisMonth)
    }
    if (progress.paused) parts += stringResource(R.string.components_pauza)
    return parts.joinToString(" · ")
}

internal fun formatValue(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else "%.1f".format(value)

private val PaddingValuesCompact =
    androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)

/** Иконка заморозки — используется листом действий и heatmap-легендой. */
internal val FreezeIcon = Icons.Filled.AcUnit

/** Цвет заморозки в heatmap: холодный, чтобы не путался с выполнением. */
internal val FreezeColor: Color
    @Composable get() = AshTheme.colors.cold
