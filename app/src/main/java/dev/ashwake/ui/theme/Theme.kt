package dev.ashwake.ui.theme

import dev.ashwake.R
import androidx.annotation.StringRes
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/** Режим темы. Тёмная — основная, светлая делается второй (раздел 2). */
enum class ThemeMode(@StringRes val titleRes: Int) {
    DARK(R.string.theme_mode_dark),
    LIGHT(R.string.theme_mode_light),
    SYSTEM(R.string.theme_mode_system);

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

    val target = remember(settings, systemDark) { settings.toColors(systemDark) }
    val shapes = remember(settings) { settings.toShapes() }
    val typography = remember { AshTypography() }

    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    val reduceMotion = remember(inspection) {
        if (inspection) false else systemReduceMotion(context)
    }

    // Цвета доезжают, а не подменяются: смена акцента или темы перекрашивает
    // всё приложение сразу, и мгновенный скачок читается как перезагрузка
    // экрана. Заодно это лучшая демонстрация редактора темы — там видно,
    // как приложение переливается вслед за ползунком.
    //
    // Признак «уменьшить движение» передаётся параметром: сюда он ещё не
    // положен в композицию, и читать его из темы было бы чтением умолчания
    val colors = target.animated(reduceMotion)

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
 * Та же палитра, но с доезжающими цветами.
 *
 * Анимируются только те токены, которые действительно меняются от настроек;
 * текст и разделители заданы прозрачностью поверх фона и доезжают вместе
 * с ним. Каждая анимация — отдельное состояние, поэтому смена одного цвета
 * не дёргает остальные.
 */
@Composable
private fun AshColors.animated(reduceMotion: Boolean): AshColors {
    if (reduceMotion) return this
    val spec = tween<Color>(durationMillis = COLOR_FADE_MS)

    val background by animateColorAsState(background, spec, label = "c-bg")
    val surface1 by animateColorAsState(surface1, spec, label = "c-s1")
    val surface2 by animateColorAsState(surface2, spec, label = "c-s2")
    val surface3 by animateColorAsState(surface3, spec, label = "c-s3")
    val text by animateColorAsState(text, spec, label = "c-text")
    val text2 by animateColorAsState(text2, spec, label = "c-text2")
    val text3 by animateColorAsState(text3, spec, label = "c-text3")
    val separator by animateColorAsState(separator, spec, label = "c-sep")
    val warm by animateColorAsState(warm, spec, label = "c-warm")
    val cold by animateColorAsState(cold, spec, label = "c-cold")
    val danger by animateColorAsState(danger, spec, label = "c-danger")
    val success by animateColorAsState(success, spec, label = "c-success")
    val accent by animateColorAsState(accent, spec, label = "c-accent")
    val accentAlt by animateColorAsState(accentAlt, spec, label = "c-accent-alt")

    return copy(
        background = background,
        surface1 = surface1,
        surface2 = surface2,
        surface3 = surface3,
        text = text,
        text2 = text2,
        text3 = text3,
        separator = separator,
        warm = warm,
        cold = cold,
        danger = danger,
        success = success,
        accent = accent,
        accentAlt = accentAlt,
        materialTint = surface1.copy(alpha = 0.72f)
    )
}

/** Сколько переливается перекраска. Дольше — и смена темы начинает тормозить. */
private const val COLOR_FADE_MS = 320

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
