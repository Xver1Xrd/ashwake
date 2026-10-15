package dev.ashwake.ui.tasks.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.ashwake.ui.theme.AshColors
import dev.ashwake.ui.theme.AshTheme

fun difficultyColor(difficulty: Int, colors: AshColors): Color = when (difficulty) {
    1 -> colors.success
    2 -> colors.cold
    3 -> colors.warm
    4 -> Color(0xFFFF9800)
    else -> colors.danger
}

fun difficultyDescription(difficulty: Int): String = when (difficulty) {
    1 -> "Очень просто"
    2 -> "Просто"
    3 -> "Средне"
    4 -> "Сложно"
    5 -> "Очень сложно"
    else -> ""
}

fun formatEstimateRu(minutes: Int): String = when {
    minutes <= 0 -> ""
    minutes < 60 -> "$minutes мин"
    minutes % 60 == 0 -> "${minutes / 60} ч"
    else -> "${minutes / 60} ч ${minutes % 60} мин"
}

@Composable
fun DifficultyDots(
    difficulty: Int,
    modifier: Modifier = Modifier,
    dotSize: Dp = 5.dp
) {
    val colors = AshTheme.colors
    val activeColor = difficultyColor(difficulty, colors)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 1..5) {
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(if (i <= difficulty) activeColor else colors.surface3)
            )
        }
    }
}
