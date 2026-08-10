package dev.ashwake.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/** Режим темы. Тёмная — основная, светлая делается второй (раздел 2). */
enum class ThemeMode(val title: String) {
    DARK("Тёмная"),
    LIGHT("Светлая"),
    SYSTEM("Как в системе");

    companion object {
        val DEFAULT = DARK
    }
}

val LocalAshColors = staticCompositionLocalOf { darkAshColors() }
val LocalAshTypography = staticCompositionLocalOf { AshTypography() }

/**
 * Системная настройка «уменьшить движение». Отключает пружины и параллакс
 * обложки (раздел 6). Читается один раз на композицию темы, потому что
 * меняется она из настроек системы, а не в течение сессии.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Плотность списков. Живёт в теме, потому что настраивается в редакторе. */
val LocalAshDensity = staticCompositionLocalOf { UiDensity.NORMAL }

/** Разрешено ли размытие под панелями. Выключается в редакторе темы. */
val LocalBlurEnabled = staticCompositionLocalOf { true }

/** Короткие обращения к токенам: `AshTheme.colors.warm`, `AshTheme.type.headline`. */
object AshTheme {
    val colors: AshColors
        @Composable get() = LocalAshColors.current

    val type: AshTypography
        @Composable get() = LocalAshTypography.current

    val shapes: AshShapeScheme
        @Composable get() = LocalAshShapes.current

    val density: UiDensity
        @Composable get() = LocalAshDensity.current

    val blurEnabled: Boolean
        @Composable get() = LocalBlurEnabled.current

    val reduceMotion: Boolean
        @Composable get() = LocalReduceMotion.current
}

@Composable
fun AshwakeTheme(
    settings: ThemeSettings = ThemeSettings(),
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()

    val colors = remember(settings, systemDark) { settings.toColors(systemDark) }
    val shapes = remember(settings) { settings.toShapes() }
    val typography = remember { AshTypography() }

    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    val reduceMotion = remember(inspection) {
        if (inspection) false else systemReduceMotion(context)
    }

    CompositionLocalProvider(
        LocalAshColors provides colors,
        LocalAshTypography provides typography,
        LocalAshShapes provides shapes,
        LocalAshDensity provides settings.density,
        LocalBlurEnabled provides settings.blur,
        LocalReduceMotion provides reduceMotion,
        LocalContentColor provides colors.text
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialScheme(),
            typography = materialTypography(typography),
            shapes = shapes.toMaterialShapes(),
            content = content
        )
    }
}

/**
 * Мост в Material: экраны, написанные на `MaterialTheme.colorScheme`,
 * должны получать ровно те же цвета, что и новые компоненты. Иначе в
 * приложении окажется две палитры и стык будет виден на каждом экране.
 *
 * Динамические цвета намеренно не используются: они берутся из обоев и
 * ломают и палитру, и контраст, проверенный в разделе 9.
 */
private fun AshColors.toMaterialScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = surface2,
        onPrimaryContainer = text,
        secondary = warm,
        onSecondary = onAccent,
        tertiary = cold,
        onTertiary = onAccent,
        background = background,
        onBackground = text,
        surface = surface1,
        onSurface = text,
        surfaceVariant = surface2,
        onSurfaceVariant = text2,
        surfaceContainer = surface1,
        surfaceContainerHigh = surface2,
        surfaceContainerHighest = surface3,
        surfaceContainerLow = surface1,
        surfaceContainerLowest = background,
        inverseSurface = text,
        inverseOnSurface = background,
        outline = separator,
        outlineVariant = separator,
        error = danger,
        onError = Color.White,
        errorContainer = surface2,
        onErrorContainer = danger,
        scrim = Color.Black.copy(alpha = 0.4f)
    )
}

/**
 * «Уменьшить движение» в Android выражается через масштаб длительности
 * анимаций: пользователь выставляет его в ноль в настройках разработчика
 * или в специальных возможностях.
 */
private fun systemReduceMotion(context: android.content.Context): Boolean = runCatching {
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    ) == 0f
}.getOrDefault(false)
