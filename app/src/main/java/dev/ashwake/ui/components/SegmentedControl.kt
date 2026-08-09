package dev.ashwake.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ashwake.ui.theme.AshTheme
import dev.ashwake.ui.theme.HapticKind
import dev.ashwake.ui.theme.SquircleShape
import dev.ashwake.ui.theme.rememberHaptics

/**
 * Сегментированный контрол (раздел 5 и 7 дизайн-системы).
 *
 * Заменяет вкладки с полоской-индикатором: переключение периода над графиком,
 * фильтры в магазине, режимы экрана. Бегунок едет за выбором пружиной,
 * а не перепрыгивает.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit
) {
    if (options.isEmpty()) return
    val colors = AshTheme.colors
    val haptics = rememberHaptics()
    val reduceMotion = AshTheme.reduceMotion

    // Позиция бегунка в долях ширины: анимируется, чтобы движение читалось
    // как одно, а не как мигание подложки под новым сегментом.
    val target = selectedIndex.coerceIn(0, options.lastIndex).toFloat()
    val position by animateFloatAsState(
        targetValue = target,
        animationSpec = if (reduceMotion) ashSpring() else ashSpring(),
        label = "segment-thumb"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(SquircleShape(9.dp))
            .background(colors.surface2)
            .padding(2.dp)
    ) {
        val count = options.size
        // Бегунок кладётся первым слоем и двигается сдвигом на долю ширины
        Box(
            Modifier
                .layout { measurable, constraints ->
                    val width = constraints.maxWidth / count
                    val placeable = measurable.measure(
                        constraints.copy(minWidth = width, maxWidth = width)
                    )
                    layout(constraints.maxWidth, placeable.height) {
                        placeable.placeRelative((position * width).toInt(), 0)
                    }
                }
                .fillMaxWidth()
                .height(28.dp)
                .background(colors.surface3, SquircleShape(7.dp))
        )

        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .tappable(haptic = null) {
                            if (!selected) haptics.play(HapticKind.LIGHT)
                            onSelect(index)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = AshTheme.type.footnote.copy(
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = if (selected) colors.text else colors.text2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
