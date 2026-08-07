package dev.ashwake.ui.habits.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.ashwake.domain.engine.habit.ScorePoint
import dev.ashwake.domain.model.habits.EntryStatus
import dev.ashwake.domain.model.habits.HabitEntry
import dev.ashwake.ui.theme.Ash3A
import dev.ashwake.ui.theme.Ember
import dev.ashwake.ui.theme.Gold
import dev.ashwake.ui.theme.Moss
import dev.ashwake.ui.theme.Steel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val MONTH_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("LLL", Locale("ru"))

/**
 * Heatmap за год (п. 3).
 *
 * Колонка — неделя, строка — день недели, как в привычных трекерах.
 * Заморозка и пауза красятся отдельным холодным цветом: пользователь должен
 * видеть, что день не провален, а сознательно выведен из счёта.
 */
@Composable
fun HabitHeatmap(
    entries: Map<LocalDate, HabitEntry>,
    excludedDays: Set<LocalDate>,
    from: LocalDate,
    to: LocalDate,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val firstMonday = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weeks = ((to.toEpochDay() - firstMonday.toEpochDay()) / 7 + 1).toInt().coerceAtLeast(1)
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            repeat(weeks) { weekIndex ->
                val weekStart = firstMonday.plusWeeks(weekIndex.toLong())
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // Подпись месяца ставится над неделей, в которую он начинается
                    Text(
                        text = if (weekStart.dayOfMonth <= 7) weekStart.format(MONTH_LABEL) else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.height(14.dp)
                    )
                    repeat(7) { dayIndex ->
                        val date = weekStart.plusDays(dayIndex.toLong())
                        val inRange = date in from..to
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (!inRange) Color.Transparent
                                    else colorOf(entries[date], date in excludedDays, emptyColor)
                                )
                                .then(
                                    if (inRange) Modifier.clickable { onDayClick(date) }
                                    else Modifier
                                )
                        )
                    }
                }
            }
        }
        HeatmapLegend()
    }
}

@Composable
private fun HeatmapLegend() {
    Row(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendItem(Moss, "выполнено")
        LegendItem(Moss.copy(alpha = 0.5f), "минимум")
        LegendItem(Ember.copy(alpha = 0.6f), "пропуск")
        LegendItem(Steel, "заморозка")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(
            "  $label",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun colorOf(entry: HabitEntry?, excluded: Boolean, emptyColor: Color): Color = when {
    entry?.status == EntryStatus.DONE -> Moss
    entry?.status == EntryStatus.MINIMUM -> Moss.copy(alpha = 0.5f)
    entry?.status == EntryStatus.FROZEN || entry?.status == EntryStatus.PAUSED -> Steel
    excluded -> Steel
    entry?.status == EntryStatus.SKIPPED -> Ember.copy(alpha = 0.6f)
    else -> emptyColor
}

/**
 * График роста score. Без осей и подписей намеренно: интересна форма кривой —
 * растёт, держится или сыпется, — а точное значение показано числом рядом.
 */
@Composable
fun ScoreChart(
    series: List<ScorePoint>,
    modifier: Modifier = Modifier
) {
    if (series.size < 2) {
        Text(
            "Данных пока мало для графика",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
        )
        return
    }

    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = Ash3A
    val targetColor = Gold

    Canvas(modifier = modifier.fillMaxWidth().height(120.dp)) {
        val stepX = size.width / (series.size - 1)
        fun y(score: Float) = size.height * (1f - score.coerceIn(0f, 1f))

        // Ориентир 80% — то самое целевое значение, к которому ведёт формула
        drawLine(
            color = targetColor.copy(alpha = 0.4f),
            start = Offset(0f, y(0.8f)),
            end = Offset(size.width, y(0.8f)),
            strokeWidth = 1f
        )
        drawLine(
            color = gridColor,
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 1f
        )

        val path = Path().apply {
            moveTo(0f, y(series.first().score))
            series.forEachIndexed { index, point ->
                lineTo(index * stepX, y(point.score))
            }
        }
        drawPath(path = path, color = lineColor, style = Stroke(width = 3f))
    }
}
