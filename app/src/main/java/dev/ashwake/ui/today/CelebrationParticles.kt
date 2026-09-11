package dev.ashwake.ui.today

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

private data class Spark(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val color: Color,
    val size: Float
)

@Composable
fun CelebrationParticles(
    trigger: Boolean,
    modifier: Modifier = Modifier
) {
    if (!trigger) return

    val progress = remember(trigger) { Animatable(0f) }
    val sparks = remember(trigger) {
        val rand = Random()
        val palette = listOf(
            Color(0xFFFFD54F),
            Color(0xFFFF9800),
            Color(0xFF80D8FF),
            Color(0xFF69F0AE),
            Color(0xFFFF4081)
        )
        (0..28).map {
            val angle = rand.nextDouble() * 2.0 * Math.PI
            val speed = 250f + rand.nextFloat() * 450f
            Spark(
                x = 0.5f,
                y = 0.5f,
                vx = (cos(angle) * speed).toFloat(),
                vy = (sin(angle) * speed - 150f).toFloat(),
                color = palette[rand.nextInt(palette.size)],
                size = 4f + rand.nextFloat() * 6f
            )
        }
    }

    LaunchedEffect(trigger) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(1200, easing = LinearEasing)
        )
    }

    if (progress.value < 1f) {
        Canvas(modifier = modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val p = progress.value
            val alpha = (1f - p).coerceIn(0f, 1f)

            sparks.forEach { spark ->
                val px = cx + spark.vx * p
                val py = cy + spark.vy * p + (300f * p * p) // gravity
                drawCircle(
                    color = spark.color.copy(alpha = alpha),
                    radius = spark.size * (1f - p * 0.5f),
                    center = Offset(px, py)
                )
            }
        }
    }
}
