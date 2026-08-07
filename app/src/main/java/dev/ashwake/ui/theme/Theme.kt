package dev.ashwake.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Палитра проекта (п. 15.5): холодные серо-синие тени, тёплые акценты только
 * на огне и золоте. Чистых #000000 и #FFFFFF нет — самый тёмный #0D0B12,
 * самый светлый #E8E0D0.
 */
val Ash0D = Color(0xFF0D0B12)   // самый тёмный
val Ash1A = Color(0xFF1A1622)
val Ash26 = Color(0xFF262130)
val Ash3A = Color(0xFF3A3346)
val AshE8 = Color(0xFFE8E0D0)   // самый светлый
val AshMuted = Color(0xFF9A93A8)

val Gold = Color(0xFFC9A227)
val Ember = Color(0xFFD97A45)
val Steel = Color(0xFF6E7BA6)
val Moss = Color(0xFF5E8C7A)
val Blood = Color(0xFF9E4A4A)

private val DarkScheme = darkColorScheme(
    primary = Steel,
    onPrimary = Ash0D,
    primaryContainer = Ash3A,
    onPrimaryContainer = AshE8,
    secondary = Gold,
    onSecondary = Ash0D,
    tertiary = Ember,
    background = Ash0D,
    onBackground = AshE8,
    surface = Ash1A,
    onSurface = AshE8,
    surfaceVariant = Ash26,
    onSurfaceVariant = AshMuted,
    outline = Ash3A,
    error = Blood
)

private val LightScheme = lightColorScheme(
    primary = Steel,
    secondary = Gold,
    tertiary = Ember,
    error = Blood
)

/** Цвет метки приоритета. Используется и списком, и матрицей Эйзенхауэра. */
val PriorityColors = listOf(Blood, Ember, Gold, Moss)

@Composable
fun AshwakeTheme(
    darkTheme: Boolean = true,           // тёмная тема по умолчанию, а не по системной
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(colorScheme = scheme, typography = AshwakeTypography, content = content)
}

/** Оставлено на будущее: настройка «следовать системе» появится в разделе настроек. */
@Composable
fun systemPrefersDark(): Boolean = isSystemInDarkTheme()
