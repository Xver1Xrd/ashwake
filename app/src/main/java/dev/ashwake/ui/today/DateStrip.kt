package dev.ashwake.ui.today

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ashwake.ui.theme.AshShapes
import dev.ashwake.ui.theme.AshTheme
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Горизонтальная интерактивная лента недели.
 *
 * Позволяет в один клик переключаться между днями: просмотреть вчерашние хвосты,
 * текущие дела или планы на завтра.
 */
@Composable
fun DateStrip(
    today: LocalDate,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AshTheme.colors
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val haptics = dev.ashwake.ui.theme.rememberHaptics()

    // Диапазон и предрассчитанные строки кэшируются на дату
    val days = remember(today) {
        val ruLocale = Locale("ru")
        (-7..7).map { offset ->
            val d = today.plusDays(offset.toLong())
            StripDay(
                date = d,
                dayOfWeek = d.dayOfWeek.getDisplayName(TextStyle.SHORT, ruLocale)
                    .replaceFirstChar { it.uppercase() },
                dayOfMonth = d.dayOfMonth.toString()
            )
        }
    }

    // Автоматическая магнитная центровка выбранного дня
    LaunchedEffect(selectedDate) {
        val index = days.indexOfFirst { it.date == selectedDate }
        if (index >= 0) {
            val itemWidthPx = with(density) { 56.dp.toPx() }
            val targetOffset = (index * itemWidthPx - with(density) { 120.dp.toPx() }).toInt().coerceAtLeast(0)
            scrollState.animateScrollTo(targetOffset)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        days.forEach { item ->
            val isSelected = item.date == selectedDate
            val isToday = item.date == today

            val bgColor by animateColorAsState(
                targetValue = when {
                    isSelected -> colors.surface2
                    isToday -> colors.surface1
                    else -> Color.Transparent
                },
                label = "strip-bg"
            )

            val borderColor = when {
                isSelected -> colors.accent
                isToday -> colors.accent.copy(alpha = 0.5f)
                else -> Color.Transparent
            }

            Column(
                modifier = Modifier
                    .width(48.dp)
                    .clip(AshShapes.card)
                    .background(bgColor)
                    .border(
                        width = if (isSelected || isToday) 1.5.dp else 0.dp,
                        color = borderColor,
                        shape = AshShapes.card
                    )
                    .clickable {
                        haptics.play(dev.ashwake.ui.theme.HapticKind.LIGHT)
                        onSelectDate(item.date)
                    }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.dayOfWeek,
                    style = AshTheme.type.caption,
                    color = if (isSelected) colors.accent else colors.text2
                )
                Text(
                    text = item.dayOfMonth,
                    style = AshTheme.type.headline,
                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) colors.text else colors.text2
                )

                // Точка статуса дня
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isToday -> colors.accent
                                item.date < today -> colors.text3
                                else -> Color.Transparent
                            }
                        )
                )
            }
        }
    }
}

private data class StripDay(
    val date: LocalDate,
    val dayOfWeek: String,
    val dayOfMonth: String
)
