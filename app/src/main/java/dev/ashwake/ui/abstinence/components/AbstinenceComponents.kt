package dev.ashwake.ui.abstinence.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ashwake.domain.engine.abstinence.AbstinenceStats
import dev.ashwake.domain.engine.abstinence.Savings
import dev.ashwake.domain.model.abstinence.Attempt
import dev.ashwake.ui.theme.CounterLarge
import dev.ashwake.ui.theme.CounterSmall
import dev.ashwake.ui.theme.Gold
import dev.ashwake.ui.theme.Moss
import dev.ashwake.ui.theme.Steel
import java.time.Duration
import kotlin.math.roundToInt

/**
 * Живой счётчик: дни самым крупным кеглем, остальное мельче (п. 4).
 * Моноширинные цифры обязательны — иначе тикающие секунды дёргают всю строку.
 */
@Composable
fun LiveCounter(duration: Duration, modifier: Modifier = Modifier) {
    val days = duration.toDays()
    val hours = duration.toHoursPart()
    val minutes = duration.toMinutesPart()
    val seconds = duration.toSecondsPart()

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(days.toString(), style = CounterLarge)
            Text(
                "  ${dayWord(days)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        Text(
            text = "%02d:%02d:%02d".format(hours, minutes, seconds),
            style = CounterSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Кольцо прогресса до ближайшей вехи. */
@Composable
fun MilestoneRing(
    progress: Float,
    label: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val track = MaterialTheme.colorScheme.surfaceVariant

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(220.dp)) {
                val stroke = 10.dp.toPx()
                val diameter = size.minDimension - stroke
                val topLeft = Offset(
                    (size.width - diameter) / 2f,
                    (size.height - diameter) / 2f
                )
                drawArc(
                    color = track, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                    topLeft = topLeft, size = Size(diameter, diameter),
                    style = Stroke(width = stroke)
                )
                drawArc(
                    color = Gold, startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f), useCenter = false,
                    topLeft = topLeft, size = Size(diameter, diameter),
                    style = Stroke(width = stroke)
                )
            }
            content()
        }
        if (label != null) {
            Text(
                "до вехи: $label",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

/** Три числа под счётчиком: рекорд, всего чистых дней, номер попытки. */
@Composable
fun StatsRow(stats: AbstinenceStats, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatCell(stats.record.toDays().toString(), "рекорд")
        StatCell(stats.totalCleanDays.toString(), "всего чистых")
        StatCell("№${stats.attemptNumber}", "попытка")
    }
}

@Composable
private fun StatCell(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Блок «сэкономлено». Без baseline не вызывается вовсе — цифры не выдумываются. */
@Composable
fun SavingsBlock(savings: Savings, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text("Сэкономлено", style = MaterialTheme.typography.titleSmall)
        Text(
            "не ${savings.units.roundToInt()} ${savings.unitName}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "${formatMoney(savings.money)} ${currencySymbol(savings.currency)}",
            style = MaterialTheme.typography.headlineSmall,
            color = Gold
        )
    }
}

/**
 * История попыток горизонтальными полосами.
 * Смысл графика в том, чтобы было видно: попытки становятся длиннее (п. 4).
 */
@Composable
fun AttemptsChart(
    attempts: List<Attempt>,
    currentDuration: Duration,
    modifier: Modifier = Modifier
) {
    if (attempts.size < 2) return
    val durations = attempts.map { attempt ->
        if (attempt.endedAt == null) currentDuration
        else Duration.between(attempt.startedAt, attempt.endedAt)
    }
    val maxDays = durations.maxOf { it.toDays() }.coerceAtLeast(1)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Попытки", style = MaterialTheme.typography.titleSmall)
        attempts.forEachIndexed { index, attempt ->
            val days = durations[index].toDays()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "№${attempt.ordinal}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(28.dp)
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(days.toFloat() / maxDays)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (attempt.isCurrent) Moss else Steel)
                    )
                }
                Text(
                    "  $days",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(36.dp)
                )
            }
        }
    }
}

/** Heatmap тяги: строки — дни недели, столбцы — часы. */
@Composable
fun CravingHeatmap(heatmap: Array<IntArray>, modifier: Modifier = Modifier) {
    val max = heatmap.flatMap { it.asIterable() }.maxOrNull() ?: 0
    if (max == 0) return
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    val labels = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        (1..7).forEach { day ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    labels[day - 1],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                    modifier = Modifier.width(18.dp)
                )
                (0..23).forEach { hour ->
                    val count = heatmap[day][hour]
                    Box(
                        Modifier
                            .weight(1f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(
                                if (count == 0) emptyColor
                                else Gold.copy(alpha = 0.25f + 0.75f * count / max)
                            )
                    )
                }
            }
        }
        Row(modifier = Modifier.padding(start = 18.dp)) {
            listOf(0, 6, 12, 18).forEach { hour ->
                Text(
                    "%02d".format(hour),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

internal fun dayWord(days: Long): String {
    val mod100 = days % 100
    val mod10 = days % 10
    return when {
        mod100 in 11..14 -> "дней"
        mod10 == 1L -> "день"
        mod10 in 2..4 -> "дня"
        else -> "дней"
    }
}

internal fun formatMoney(value: Float): String =
    if (value >= 1000) "%,d".format(value.roundToInt()).replace(',', ' ')
    else value.roundToInt().toString()

internal fun currencySymbol(code: String): String = when (code.uppercase()) {
    "RUB" -> "₽"
    "USD" -> "$"
    "EUR" -> "€"
    else -> code
}

/** Цвет фона карточки счётчика в списке. */
internal val CounterAccent: Color = Moss
