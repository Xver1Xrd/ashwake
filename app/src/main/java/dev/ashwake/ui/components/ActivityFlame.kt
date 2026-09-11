package dev.ashwake.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ashwake.R
import dev.ashwake.ui.theme.AshShapes
import dev.ashwake.ui.theme.AshTheme

enum class FlameLevel(val title: String, val description: String) {
    BLAZING(
        "Яркое пламя",
        "Вы полны энергии и активны! Огонь пылает ярко."
    ),
    BURNING(
        "Горящий огонёк",
        "Отличный темп, огонёк горит уверенно."
    ),
    EMBER(
        "Тлеющие угли",
        "Огонёк затихает. Выполните дело, чтобы подбросить углей!"
    ),
    DIM(
        "Угасающий огонёк",
        "Огонь почти погас. Самое время вернуться к делам."
    ),
    ASH(
        "Остывший пепел",
        "Огонь превратился в пепел. Выполните задачу или привычку, чтобы зажечь его вновь!"
    )
}

/**
 * Огонёк активности пользователя:
 * Яркое пламя -> Горящий огонёк -> Тлеющие угли -> Угасающий огонёк -> Пепел.
 * При возобновлении активности огонёк разгорается в обратном порядке.
 */
@Composable
fun ActivityFlame(
    level: FlameLevel,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    val haptics = dev.ashwake.ui.theme.rememberHaptics()

    val transition = rememberInfiniteTransition(label = "flame")
    val pulse by transition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flame-pulse"
    )

    val glowAlpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flame-glow"
    )

    val flutterPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flame-flutter"
    )

    val flameDesc = "Огонёк: ${level.title}"
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = level.title) {
                haptics.play(dev.ashwake.ui.theme.HapticKind.FLAME_CRACKLE)
                showDialog = true
            }
            .semantics(mergeDescendants = true) {
                contentDescription = flameDesc
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(34.dp)) {
            drawFlame(level, pulse, glowAlpha, flutterPhase)
        }
    }

    if (showDialog) {
        FlameDetailsDialog(
            level = level,
            onDismiss = { showDialog = false }
        )
    }
}

private fun DrawScope.drawFlame(level: FlameLevel, pulse: Float, glowAlpha: Float, flutterPhase: Float = 0f) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = size.minDimension / 2f

    val (flameColors, glowColor) = when (level) {
        FlameLevel.BLAZING -> listOf(
            Color(0xFFFFD54F),
            Color(0xFFFF9800),
            Color(0xFFFF3D00),
            Color(0xFFD50000)
        ) to Color(0xFFFF9800)
        FlameLevel.BURNING -> listOf(
            Color(0xFFFFE082),
            Color(0xFFFF9800),
            Color(0xFFE65100)
        ) to Color(0xFFFF9800)
        FlameLevel.EMBER -> listOf(
            Color(0xFFFF7043),
            Color(0xFFBF360C),
            Color(0xFF4E1D10)
        ) to Color(0xFFBF360C)
        FlameLevel.DIM -> listOf(
            Color(0xFF8D6E63),
            Color(0xFF5D4037),
            Color(0xFF3E2723)
        ) to Color(0xFF5D4037)
        FlameLevel.ASH -> listOf(
            Color(0xFF9E9E9E),
            Color(0xFF757575),
            Color(0xFF424242)
        ) to Color(0xFF616161)
    }

    val activePulse = if (level == FlameLevel.ASH) 1f else pulse

    // Glow background
    if (level != FlameLevel.ASH) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glowColor.copy(alpha = glowAlpha * 0.45f), Color.Transparent),
                center = Offset(cx, cy),
                radius = r * activePulse * 1.25f
            )
        )
    }

    // Живое органическое покачивание (flutter): смещение верхушки и контрольных точек волнами
    val flutterX = if (level != FlameLevel.ASH) {
        (kotlin.math.sin(flutterPhase.toDouble() * 3.0) * (size.width * 0.05f) +
            kotlin.math.sin(flutterPhase.toDouble() * 5.0) * (size.width * 0.025f)).toFloat()
    } else 0f
    val wobbleRight = if (level != FlameLevel.ASH) {
        (kotlin.math.sin(flutterPhase.toDouble() * 2.0 + 1.2) * (size.width * 0.04f)).toFloat()
    } else 0f
    val wobbleLeft = if (level != FlameLevel.ASH) {
        (kotlin.math.cos(flutterPhase.toDouble() * 2.5) * (size.width * 0.04f)).toFloat()
    } else 0f

    // Flame shape path
    val path = Path().apply {
        val w = size.width * 0.7f * activePulse
        val h = size.height * 0.85f * activePulse
        val left = cx - w / 2f + wobbleLeft
        val right = cx + w / 2f + wobbleRight
        val bottom = cy + h * 0.45f
        val top = cy - h * 0.5f

        moveTo(cx + flutterX, top)
        cubicTo(
            right * 1.05f, cy - h * 0.1f,
            right * 1.08f, cy + h * 0.25f,
            cx, bottom
        )
        cubicTo(
            left * 0.92f, cy + h * 0.25f,
            left * 0.95f, cy - h * 0.1f,
            cx + flutterX, top
        )
        close()
    }

    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            colors = flameColors,
            startY = cy - size.height * 0.4f,
            endY = cy + size.height * 0.45f
        )
    )

    // Inner bright spark/core
    if (level == FlameLevel.BLAZING || level == FlameLevel.BURNING) {
        val innerPath = Path().apply {
            val iw = size.width * 0.35f
            val ih = size.height * 0.45f
            val ibottom = cy + ih * 0.4f
            val itop = cy - ih * 0.35f
            moveTo(cx + flutterX * 0.6f, itop)
            cubicTo(cx + iw * 0.5f + wobbleRight * 0.5f, cy, cx + iw * 0.5f, ibottom * 0.9f, cx, ibottom)
            cubicTo(cx - iw * 0.5f, ibottom * 0.9f, cx - iw * 0.5f + wobbleLeft * 0.5f, cy, cx + flutterX * 0.6f, itop)
            close()
        }
        drawPath(
            path = innerPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFFFDE7), Color(0xFFFFEB3B).copy(alpha = 0.8f)),
                startY = cy - size.height * 0.2f,
                endY = cy + size.height * 0.2f
            )
        )
    }
}

@Composable
private fun FlameDetailsDialog(
    level: FlameLevel,
    onDismiss: () -> Unit
) {
    val colors = AshTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Canvas(modifier = Modifier.size(28.dp)) {
                    drawFlame(level, 1f, 0.5f)
                }
                Text(text = level.title, style = AshTheme.type.title3)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = level.description,
                    style = AshTheme.type.body,
                    color = colors.text
                )
                Box(
                    Modifier
                        .background(colors.surface2, AshShapes.card)
                        .padding(12.dp)
                ) {
                    Text(
                        text = when (level) {
                            FlameLevel.BLAZING -> "🔥 Поддерживайте огонь ежедневными привычками и задачами."
                            FlameLevel.BURNING -> "✨ Огонёк горит ровно. Еще пара дел раздует его до максимума!"
                            FlameLevel.EMBER -> "🍂 Пропуски остужают угли. Сделайте одно дело, чтобы разжечь огонь."
                            FlameLevel.DIM -> "⏳ Огонь затухает. Достаточно закрыть 1 задачу, чтобы вернуть тепло."
                            FlameLevel.ASH -> "🌱 Из пепла рождается новое пламя! Любое завершенное дело вернёт искру."
                        },
                        style = AshTheme.type.footnote,
                        color = colors.text2
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.detail_ponyatno))
            }
        }
    )
}
