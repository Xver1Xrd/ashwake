package dev.ashwake.ui.today

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import dev.ashwake.ui.theme.AshColors
import java.time.LocalTime

enum class DayAtmosphere(val title: String, val icon: String) {
    DAWN("Утро · Рассвет", "🌅"),
    DAY("День · Фокус", "☀️"),
    SUNSET("Вечер · Закат", "🌇"),
    NIGHT("Ночь · Покой", "🌙")
}

fun currentAtmosphere(time: LocalTime = LocalTime.now()): DayAtmosphere = when (time.hour) {
    in 5..8 -> DayAtmosphere.DAWN
    in 9..17 -> DayAtmosphere.DAY
    in 18..21 -> DayAtmosphere.SUNSET
    else -> DayAtmosphere.NIGHT
}

@Composable
fun rememberAtmosphereGradient(colors: AshColors, atmosphere: DayAtmosphere): Brush {
    val (c1, c2) = when (atmosphere) {
        DayAtmosphere.DAWN -> Color(0xFF28233C) to Color(0xFF1E2838)
        DayAtmosphere.DAY -> colors.accent.copy(alpha = 0.18f) to colors.surface2
        DayAtmosphere.SUNSET -> Color(0xFF381F28) to Color(0xFF281C32)
        DayAtmosphere.NIGHT -> Color(0xFF131728) to Color(0xFF111422)
    }

    val animC1 by animateColorAsState(targetValue = c1, animationSpec = tween(1000), label = "c1")
    val animC2 by animateColorAsState(targetValue = c2, animationSpec = tween(1000), label = "c2")

    return Brush.linearGradient(
        listOf(animC1, animC2, colors.surface1)
    )
}
