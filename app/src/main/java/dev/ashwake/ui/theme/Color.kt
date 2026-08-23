package dev.ashwake.ui.theme

import androidx.compose.ui.res.stringResource
import dev.ashwake.R
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Токены цвета.
 *
 * Иерархия строится светлотой поверхности, а не тенями: поэтому здесь не
 * «фон и карточка», а фон и три уровня поднятия. Тени в приложении есть
 * только под модальным листом.
 *
 * Палитра не серая: у фона и поверхностей есть общий холодный подтон
 * (синева с уходом в фиолетовый). Нейтрально-серый интерфейс на телефоне
 * выглядит выцветшим, а один и тот же подтон на всех уровнях связывает
 * экраны в одно целое и даёт акцентам, которых мало, звучать ярче.
 *
 * Material-схема собирается из этих же токенов в [AshwakeTheme], чтобы
 * экраны, написанные на `MaterialTheme.colorScheme`, получали те же цвета,
 * а не вторую независимую палитру.
 */
@Immutable
data class AshColors(
    /** Базовый фон приложения. */
    val background: Color,
    /** Группы списков, карточки. */
    val surface1: Color,
    /** Элементы внутри карточек, поля ввода. */
    val surface2: Color,
    /** Нажатое состояние, сегментированный контрол. */
    val surface3: Color,
    val text: Color,
    /** Подписи, метаданные. */
    val text2: Color,
    /** Плейсхолдеры, неактивное. */
    val text3: Color,
    /** Линии в списках, рисуются толщиной 0.5dp. */
    val separator: Color,
    /** Выполнено, стрик, награда, монеты. */
    val warm: Color,
    /** Счётчики отказов, таймеры, фокус. */
    val cold: Color,
    /** Срыв, удаление, просрочка. */
    val danger: Color,
    /** Подтверждение: бэкап, выполненная привычка. */
    val success: Color,
    /** Выбранный пользователем акцент. */
    val accent: Color,
    /**
     * Второй цвет акцентного градиента. Заливка «в одну краску» на больших
     * плоскостях выглядит плоско, поэтому герой-блоки и главная кнопка
     * тонируются переходом между двумя близкими тонами, а не одним цветом.
     */
    val accentAlt: Color,
    /** Тонировка полупрозрачных панелей поверх размытия. */
    val materialTint: Color,
    val isDark: Boolean
) {
    /** Градиент акцента: слева направо и сверху вниз, всегда в одну сторону. */
    val accentGradient: Brush
        get() = Brush.linearGradient(listOf(accent, accentAlt))
}

// ---------------------------------------------------------------------------
// Тёмная тема — основная
//
// Фон не чистый чёрный: на OLED он экономит батарею, но карточки на нём
// висят в пустоте, а границы поверхностей приходится рисовать линиями.
// Почти-чёрный с синевой оставляет запас, чтобы уровни различались светлотой.
// ---------------------------------------------------------------------------
private val DarkBackground = Color(0xFF0A0A10)
private val DarkSurface1 = Color(0xFF14141D)
private val DarkSurface2 = Color(0xFF1D1D28)
private val DarkSurface3 = Color(0xFF282836)
private val DarkText = Color(0xFFF4F4F8)
private val DarkText2 = Color(0xA3E4E4F2)   // 64%
private val DarkText3 = Color(0x59E4E4F2)   // 35%
private val DarkSeparator = Color(0x2E9A9AC0)

// Светлая тема — вторая. Тот же подтон, только светлотой наоборот.
private val LightBackground = Color(0xFFF4F4F8)
private val LightSurface1 = Color(0xFFFFFFFF)
private val LightSurface2 = Color(0xFFEDEDF4)
private val LightSurface3 = Color(0xFFE1E1EC)
private val LightText = Color(0xFF14141B)
private val LightText2 = Color(0xA31B1B2E)
private val LightText3 = Color(0x591B1B2E)
private val LightSeparator = Color(0x2E4A4A70)

// Семантика. Одни и те же роли в обеих темах, разной светлоты: на тёмном
// фоне нужны более светлые тона, иначе контраст проваливается.
private val WarmDark = Color(0xFFFFB84D)
private val WarmLight = Color(0xFFE8890B)
private val ColdDark = Color(0xFF4CC9F0)
private val ColdLight = Color(0xFF0E92C4)
private val DangerDark = Color(0xFFFF6B6B)
private val DangerLight = Color(0xFFE03131)
private val SuccessDark = Color(0xFF3FDC9A)
private val SuccessLight = Color(0xFF12A06A)

/**
 * Акценты, из которых пользователь выбирает свой.
 *
 * Каждый — пара близких тонов: второй нужен градиенту. Пары подобраны так,
 * чтобы переход читался как один цвет с подсветкой, а не как двухцветная
 * заливка: разница по тону не больше 30°.
 */
enum class AccentColor(
    @StringRes val titleRes: Int,
    private val darkFrom: Color,
    private val darkTo: Color,
    private val lightFrom: Color,
    private val lightTo: Color
) {
    VIOLET(R.string.accent_violet, Color(0xFF8B7CFF), Color(0xFFB07CFF), Color(0xFF6A4CF0), Color(0xFF9A4CF0)),
    EMBER(R.string.accent_ember, Color(0xFFFF8A4C), Color(0xFFFF5E7A), Color(0xFFE8620F), Color(0xFFE03A5C)),
    AMBER(R.string.accent_amber, Color(0xFFFFC24D), Color(0xFFFF9A3D), Color(0xFFDD9500), Color(0xFFDD6E00)),
    MINT(R.string.accent_mint, Color(0xFF3FDC9A), Color(0xFF3FD0C8), Color(0xFF0E9E68), Color(0xFF0E9490)),
    AZURE(R.string.accent_azure, Color(0xFF4CC9F0), Color(0xFF4C9BF0), Color(0xFF0E8CC0), Color(0xFF1367CE)),
    INDIGO(R.string.accent_indigo, Color(0xFF6C7BFF), Color(0xFF8B62F5), Color(0xFF4453EE), Color(0xFF6B36DE)),
    ROSE(R.string.accent_rose, Color(0xFFFF6FA5), Color(0xFFFF7BD0), Color(0xFFE0407D), Color(0xFFDB4BAF)),
    LIME(R.string.accent_lime, Color(0xFFA8DC3F), Color(0xFF5FD86B), Color(0xFF6F9C10), Color(0xFF23A03A));

    fun resolve(isDark: Boolean): Color = if (isDark) darkFrom else lightFrom

    fun resolveAlt(isDark: Boolean): Color = if (isDark) darkTo else lightTo

    companion object {
        val DEFAULT = VIOLET

        /**
         * Разбор сохранённого значения. Возвращает [DEFAULT], если имени нет:
         * настройки старых сборок не должны ронять запуск.
         */
        fun of(name: String?): AccentColor =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

fun darkAshColors(accent: AccentColor = AccentColor.DEFAULT): AshColors = AshColors(
    background = DarkBackground,
    surface1 = DarkSurface1,
    surface2 = DarkSurface2,
    surface3 = DarkSurface3,
    text = DarkText,
    text2 = DarkText2,
    text3 = DarkText3,
    separator = DarkSeparator,
    warm = WarmDark,
    cold = ColdDark,
    danger = DangerDark,
    success = SuccessDark,
    accent = accent.resolve(isDark = true),
    accentAlt = accent.resolveAlt(isDark = true),
    materialTint = DarkSurface1.copy(alpha = 0.72f),
    isDark = true
)

fun lightAshColors(accent: AccentColor = AccentColor.DEFAULT): AshColors = AshColors(
    background = LightBackground,
    surface1 = LightSurface1,
    surface2 = LightSurface2,
    surface3 = LightSurface3,
    text = LightText,
    text2 = LightText2,
    text3 = LightText3,
    separator = LightSeparator,
    warm = WarmLight,
    cold = ColdLight,
    danger = DangerLight,
    success = SuccessLight,
    accent = accent.resolve(isDark = false),
    accentAlt = accent.resolveAlt(isDark = false),
    materialTint = LightSurface1.copy(alpha = 0.72f),
    isDark = false
)

/** Цвет текста на заливке акцентом. Считается один раз, а не подбирается на месте. */
val AshColors.onAccent: Color
    get() = if (isDark) Color(0xFF0A0A10) else Color.White

// ---------------------------------------------------------------------------
// Совместимость со старой палитрой
//
// До дизайн-системы у проекта была своя фэнтезийная палитра (Gold, Ember,
// Steel, Moss, Blood, Ash*). На неё ссылается ещё десяток экранов.
//
// Раньше это были константы тёмной темы, и на светлой теме такой экран
// выглядел сломанным: чёрный текст на чёрном фоне. Теперь это свойства,
// читающие текущую тему, — то есть настоящие токены под старыми именами.
// Каждый переписанный экран убирает по одной такой ссылке.
//
// Для виджетов Glance и рисования в DrawScope, где темы нет, рядом лежат
// сырые константы [WidgetPalette].
// ---------------------------------------------------------------------------

@Deprecated("Использовать AshTheme.colors.background", ReplaceWith("AshTheme.colors.background"))
val Ash0D: Color @Composable get() = LocalAshColors.current.background

@Deprecated("Использовать AshTheme.colors.surface1", ReplaceWith("AshTheme.colors.surface1"))
val Ash1A: Color @Composable get() = LocalAshColors.current.surface1

@Deprecated("Использовать AshTheme.colors.surface2", ReplaceWith("AshTheme.colors.surface2"))
val Ash26: Color @Composable get() = LocalAshColors.current.surface2

@Deprecated("Использовать AshTheme.colors.surface3", ReplaceWith("AshTheme.colors.surface3"))
val Ash3A: Color @Composable get() = LocalAshColors.current.surface3

@Deprecated("Использовать AshTheme.colors.text", ReplaceWith("AshTheme.colors.text"))
val AshE8: Color @Composable get() = LocalAshColors.current.text

@Deprecated("Использовать AshTheme.colors.text2", ReplaceWith("AshTheme.colors.text2"))
val AshMuted: Color @Composable get() = LocalAshColors.current.text2

/** Награды, монеты, стрик, выполнено. */
@Deprecated("Использовать AshTheme.colors.warm", ReplaceWith("AshTheme.colors.warm"))
val Gold: Color @Composable get() = LocalAshColors.current.warm

@Deprecated("Использовать AshTheme.colors.warm", ReplaceWith("AshTheme.colors.warm"))
val Ember: Color @Composable get() = LocalAshColors.current.warm

/** Фокус, таймеры, счётчики отказов. */
@Deprecated("Использовать AshTheme.colors.cold", ReplaceWith("AshTheme.colors.cold"))
val Steel: Color @Composable get() = LocalAshColors.current.cold

@Deprecated("Использовать AshTheme.colors.success", ReplaceWith("AshTheme.colors.success"))
val Moss: Color @Composable get() = LocalAshColors.current.success

/** Срыв, удаление, просрочка. */
@Deprecated("Использовать AshTheme.colors.danger", ReplaceWith("AshTheme.colors.danger"))
val Blood: Color @Composable get() = LocalAshColors.current.danger

/**
 * Цвета для мест без композиции темы: виджеты Glance и рисование в DrawScope.
 *
 * Значения тёмной темы и только они: виджет живёт на рабочем столе, где
 * настройки приложения не читаются, а выбирать между двумя палитрами там
 * всё равно нечем.
 */
object WidgetPalette {
    val background = DarkBackground
    val surface1 = DarkSurface1
    val surface2 = DarkSurface2
    val surface3 = DarkSurface3
    val text = DarkText
    val text2 = DarkText2
    val warm = WarmDark
    val cold = ColdDark
    val danger = DangerDark
    val success = SuccessDark
}
