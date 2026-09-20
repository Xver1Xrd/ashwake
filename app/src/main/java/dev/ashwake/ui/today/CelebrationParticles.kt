package dev.ashwake.ui.today

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.ashwake.ui.theme.AshTheme
import dev.ashwake.ui.theme.HapticKind
import dev.ashwake.ui.theme.rememberHaptics

/**
 * Элегантная минималистичная анимация завершения дня вместо дешёвого конфетти.
 *
 * Создаёт мягкую теплую волну свечения (Radiant Aura Pulse) и тонкое расширяющееся
 * световое кольцо с тактильным откликом — стиль благородного завершения как в Things 3.
 */
@Composable
fun CelebrationParticles(
    trigger: Boolean,
    modifier: Modifier = Modifier
) {
    if (!trigger) return

    val colors = AshTheme.colors
    val haptics = rememberHaptics()
    val progress = remember(trigger) { Animatable(0f) }
    val glowColor = colors.warm

    LaunchedEffect(trigger) {
        haptics.play(HapticKind.SUCCESS)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(1100, easing = FastOutSlowInEasing)
        )
    }

    val p = progress.value
    if (p < 1f) {
        Canvas(modifier = modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val maxRadius = size.minDimension * 0.7f

            val currentRadius = maxRadius * p
            val alpha = (1f - p * p).coerceIn(0f, 1f)

            // Мягкий рассеивающийся радиальный ореол
            val radialGlow = Brush.radialGradient(
                colors = listOf(
                    glowColor.copy(alpha = 0.28f * alpha),
                    glowColor.copy(alpha = 0.08f * alpha),
                    androidx.compose.ui.graphics.Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = currentRadius + 40.dp.toPx()
            )
            drawCircle(
                brush = radialGlow,
                radius = currentRadius + 40.dp.toPx(),
                center = Offset(cx, cy)
            )

            // Тонкое расширяющееся кольцо импульса
            drawCircle(
                color = glowColor.copy(alpha = 0.45f * alpha),
                radius = currentRadius,
                center = Offset(cx, cy),
                style = Stroke(width = (2.dp.toPx() * (1f - p * 0.5f)).coerceAtLeast(0.5f))
            )

            // Второе деликатное внутреннее кольцо
            if (p > 0.15f) {
                val innerP = (p - 0.15f) / 0.85f
                val innerAlpha = (1f - innerP).coerceIn(0f, 1f)
                drawCircle(
                    color = colors.accent.copy(alpha = 0.35f * innerAlpha),
                    radius = maxRadius * 0.75f * innerP,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }
    }
}
