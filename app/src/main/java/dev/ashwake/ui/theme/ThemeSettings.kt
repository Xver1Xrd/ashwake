package dev.ashwake.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Оформление приложения целиком: то, что настраивается в редакторе темы.
 *
 * Всё, что здесь есть, выводится в живые токены — цвета, формы, плотность.
 * Ни один экран не читает эти настройки напрямую: он берёт готовый токен
 * из темы, иначе каждая новая настройка означала бы правку в тридцати местах.
 */
@Immutable
data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.DEFAULT,
    val accent: AccentColor = AccentColor.DEFAULT,
    /** Свой цвет акцента в ARGB. Перекрывает пресет, null — пресет. */
    val customAccent: Int? = null,
    /** Второй тон акцента: градиент на герой-блоке и главной кнопке. */
    val gradient: Boolean = true,
    val background: BackgroundStyle = BackgroundStyle.INK,
    val corner: CornerStyle = CornerStyle.CONTINUOUS,
    /** Множитель всех радиусов. 1 — как задумано, 0 — прямые углы. */
    val cornerScale: Float = 1f,
    val density: UiDensity = UiDensity.NORMAL,
    /** Размытие под панелями. Выключается ради слабых устройств. */
    val blur: Boolean = true,
    /** Переопределения семантических цветов в ARGB. null — как в палитре. */
    val warm: Int? = null,
    val cold: Int? = null,
    val danger: Int? = null,
    val success: Int? = null
) {
    /** Есть ли хоть одно отличие от исходной темы: по нему включается сброс. */
    val isCustomized: Boolean
        get() = this != ThemeSettings()
}

/**
 * Фон приложения.
 *
 * Чистый чёрный экономит батарею на OLED, но карточки на нём висят в
 * пустоте; чернильный оставляет запас светлоты. Выбор за человеком —
 * это ровно тот случай, когда «правильного» ответа нет.
 */
enum class BackgroundStyle(val title: String, val note: String) {
    INK("Чернильный", "Тёмный с холодным подтоном"),
    BLACK("Чёрный", "Экономит батарею на OLED"),
    GRAPHITE("Графит", "Светлее, мягче контраст")
}

/**
 * Форма угла.
 *
 * Непрерывное скругление — четверть суперэллипса, у которого кривизна
 * нарастает плавно; обычное — дуга окружности со скачком кривизны на стыке
 * с прямой стороной. Разницу глаз замечает, даже не умея её назвать.
 */
enum class CornerStyle(val title: String, val smoothing: Float) {
    CONTINUOUS("Непрерывное", 0.6f),
    ROUNDED("Обычное", 0f),
    SHARP("Прямое", 0f)
}

/** Плотность списков: сколько воздуха в строке. */
enum class UiDensity(
    val title: String,
    val rowVerticalPadding: Dp,
    val minRowHeight: Dp,
    val groupSpacing: Dp
) {
    COMPACT("Плотно", 7.dp, 40.dp, 14.dp),
    NORMAL("Обычно", 11.dp, 48.dp, 22.dp),
    SPACIOUS("Просторно", 15.dp, 56.dp, 28.dp)
}

// ---------------------------------------------------------------------------
// Вывод токенов из настроек
// ---------------------------------------------------------------------------

/** Тёмная ли тема при этих настройках и такой системной. */
fun ThemeSettings.isDark(systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    ThemeMode.SYSTEM -> systemDark
}

/**
 * Цвета по настройкам.
 *
 * Своя пара акцента считается из одного выбранного цвета: второй тон —
 * тот же цвет со сдвигом оттенка на 22°. Просить у человека два цвета
 * ради градиента значит заставлять его подбирать сочетание, а из одного
 * оно получается предсказуемо.
 */
fun ThemeSettings.toColors(systemDark: Boolean): AshColors {
    val dark = isDark(systemDark)
    val base = if (dark) darkAshColors(accent) else lightAshColors(accent)

    val accentColor = customAccent?.let(::Color) ?: base.accent
    val accentAltColor = when {
        !gradient -> accentColor
        customAccent != null -> accentColor.shiftHue(GRADIENT_HUE_SHIFT)
        else -> base.accentAlt
    }

    return base.copy(
        background = backgroundColor(dark) ?: base.background,
        surface1 = surfaceColor(dark, 1) ?: base.surface1,
        surface2 = surfaceColor(dark, 2) ?: base.surface2,
        surface3 = surfaceColor(dark, 3) ?: base.surface3,
        accent = accentColor,
        accentAlt = accentAltColor,
        warm = warm?.let(::Color) ?: base.warm,
        cold = cold?.let(::Color) ?: base.cold,
        danger = danger?.let(::Color) ?: base.danger,
        success = success?.let(::Color) ?: base.success,
        materialTint = (surfaceColor(dark, 1) ?: base.surface1).copy(alpha = 0.72f)
    )
}

/** Фон и поверхности стиля. null — оставить как в палитре. */
private fun ThemeSettings.backgroundColor(dark: Boolean): Color? {
    if (!dark) return null
    return when (background) {
        BackgroundStyle.INK -> null
        BackgroundStyle.BLACK -> Color(0xFF000000)
        BackgroundStyle.GRAPHITE -> Color(0xFF15151A)
    }
}

private fun ThemeSettings.surfaceColor(dark: Boolean, level: Int): Color? {
    if (!dark) return null
    return when (background) {
        BackgroundStyle.INK -> null
        BackgroundStyle.BLACK -> when (level) {
            1 -> Color(0xFF101016)
            2 -> Color(0xFF1A1A22)
            else -> Color(0xFF25252F)
        }

        BackgroundStyle.GRAPHITE -> when (level) {
            1 -> Color(0xFF1F1F27)
            2 -> Color(0xFF282833)
            else -> Color(0xFF33333F)
        }
    }
}

/** Формы по настройкам: масштаб радиуса и стиль угла. */
fun ThemeSettings.toShapes(): AshShapeScheme {
    val scale = cornerScale.coerceIn(0f, MAX_CORNER_SCALE)
    val sharp = corner == CornerStyle.SHARP
    return AshShapeScheme(
        smoothing = corner.smoothing,
        continuous = corner == CornerStyle.CONTINUOUS,
        smallRadius = radius(12, scale, sharp),
        groupRadius = radius(18, scale, sharp),
        cardRadius = radius(24, scale, sharp),
        sheetRadius = radius(30, scale, sharp),
        alertRadius = radius(26, scale, sharp),
        // Таблетка остаётся таблеткой при любом масштабе, кроме прямых углов:
        // у кнопки-таблетки скругление это её суть, а не отделка
        pillPercent = if (sharp) 0 else 50
    )
}

private fun radius(base: Int, scale: Float, sharp: Boolean): Dp =
    if (sharp) 0.dp else (base * scale).roundToInt().dp

/** Сдвиг оттенка для второго тона градиента, в градусах. */
private const val GRADIENT_HUE_SHIFT = 22f

const val MAX_CORNER_SCALE = 1.75f

/**
 * Тот же цвет с другим оттенком. Считается через HSV, потому что сдвиг
 * по каналам RGB даёт непредсказуемый результат: он меняет и светлоту.
 */
fun Color.shiftHue(degrees: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[0] = (hsv[0] + degrees + 360f) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv))
}
