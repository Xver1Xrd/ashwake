package dev.ashwake.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

val LocalIs24Hour = compositionLocalOf { true }

fun formatTime(time: LocalTime?, is24Hour: Boolean = true): String {
    if (time == null) return ""
    val pattern = if (is24Hour) "HH:mm" else "h:mm a"
    return time.format(DateTimeFormatter.ofPattern(pattern, Locale.US))
}

@Composable
fun LocalTime.formatDisplay(): String = formatTime(this, LocalIs24Hour.current)
