package dev.ashwake.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Токены цвета из дизайн-системы, раздел 2.
 *
 * Иерархия строится светлотой поверхности, а не тенями: поэтому здесь не
 * «фон и карточка», а фон и три уровня поднятия. Тени в приложении есть
 * только под модальным листом.
 *
 * Material-схема собирается из этих же токенов в [AshwakeTheme], чтобы
 * экраны, написанные на `MaterialTheme.colorScheme`, получали те же цвета,
 * а не вторую независимую палитру.
 */
@Immutable
data class AshColors(
    /** Базовый фон приложения. В тёмной теме — чистый чёрный ради OLED. */
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
    /** Только подтверждение бэкапа. */
    val success: Color,
    /** Выбранный пользователем акцент. По умолчанию совпадает с [warm]. */
    val accent: Color,
    /** Тонировка полупрозрачных панелей поверх размытия. */
    val materialTint: Color,
    val isDark: Boolean
)

// Тёмная тема — основная.
private val DarkBackground = Color(0xFF000000)
private val DarkSurface1 = Color(0xFF1C1C1E)
private val DarkSurface2 = Color(0xFF2C2C2E)
private val DarkSurface3 = Color(0xFF3A3A3C)
private val DarkText = Color(0xFFFFFFFF)
private val DarkText2 = Color(0x99EBEBF5)   // rgba(235,235,245,0.60)
private val DarkText3 = Color(0x4DEBEBF5)   // rgba(235,235,245,0.30)
private val DarkSeparator = Color(0xA6545458) // rgba(84,84,88,0.65)

// Светлая тема — вторая.
private val LightBackground = Color(0xFFF2F2F7)
private val LightSurface1 = Color(0xFFFFFFFF)
private val LightSurface2 = Color(0xFFF2F2F7)
private val LightSurface3 = Color(0xFFE5E5EA)
private val LightText = Color(0xFF000000)
private val LightText2 = Color(0x993C3C43)
private val LightText3 = Color(0x4D3C3C43)
private val LightSeparator = Color(0x5C3C3C43)

/**
 * Системная палитра Apple: восемь цветов, из которых пользователь выбирает
 * акцент (раздел 2). Тёмные и светлые варианты одного цвета различаются —
 * на чёрном фоне нужны более светлые тона, иначе контраст проваливается.
 */
enum class AccentColor(
    val title: String,
    val dark: Color,
    val light: Color
) {
    ORANGE("Оранжевый", Color(0xFFFF9F0A), Color(0xFFFF9500)),
    RED("Красный", Color(0xFFFF453A), Color(0xFFFF3B30)),
    YELLOW("Жёлтый", Color(0xFFFFD60A), Color(0xFFFFCC00)),
    GREEN("Зелёный", Color(0xFF30D158), Color(0xFF34C759)),
    CYAN("Голубой", Color(0xFF64D2FF), Color(0xFF32ADE6)),
    BLUE("Синий", Color(0xFF0A84FF), Color(0xFF007AFF)),
    INDIGO("Индиго", Color(0xFF5E5CE6), Color(0xFF5856D6)),
    PURPLE("Фиолетовый", Color(0xFFBF5AF2), Color(0xFFAF52DE));

    fun resolve(isDark: Boolean): Color = if (isDark) dark else light

    companion object {
        val DEFAULT = ORANGE
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
    warm = Color(0xFFFF9F0A),
    cold = Color(0xFF5E5CE6),
    danger = Color(0xFFFF453A),
    success = Color(0xFF30D158),
    accent = accent.resolve(isDark = true),
    materialTint = DarkSurface1.copy(alpha = 0.70f),
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
    warm = Color(0xFFFF9500),
    cold = Color(0xFF5856D6),
    danger = Color(0xFFFF3B30),
    success = Color(0xFF34C759),
    accent = accent.resolve(isDark = false),
    materialTint = LightSurface1.copy(alpha = 0.70f),
    isDark = false
)

/**
 * Цвета меток приоритета P1–P4. Дублируются словом в интерфейсе:
 * ни одно состояние не передаётся только цветом (раздел 9).
 */
val AshColors.priorityColors: List<Color>
    get() = listOf(danger, warm, cold, text2)

// ---------------------------------------------------------------------------
// Совместимость со старой палитрой
//
// До перехода на дизайн-систему у проекта была своя фэнтезийная палитра
// (Gold, Ember, Steel, Moss, Blood, Ash*). На неё ссылается два десятка
// экранов. Названия сохранены как псевдонимы новых токенов, чтобы смена
// палитры прошла по всему приложению сразу, а не превратилась в один
// гигантский коммит, который нечем проверить.
//
// Значения здесь — тёмной темы: это константы времени компиляции, они не
// умеют реагировать на смену темы. Каждый экран, переписанный на
// `AshTheme.colors`, убирает по одной такой ссылке; пока они есть,
// светлая тема на непереписанных экранах будет неполной.
// ---------------------------------------------------------------------------

@Deprecated("Использовать AshTheme.colors.background", ReplaceWith("AshTheme.colors.background"))
val Ash0D = DarkBackground

@Deprecated("Использовать AshTheme.colors.surface1", ReplaceWith("AshTheme.colors.surface1"))
val Ash1A = DarkSurface1

@Deprecated("Использовать AshTheme.colors.surface2", ReplaceWith("AshTheme.colors.surface2"))
val Ash26 = DarkSurface2

@Deprecated("Использовать AshTheme.colors.surface3", ReplaceWith("AshTheme.colors.surface3"))
val Ash3A = DarkSurface3

@Deprecated("Использовать AshTheme.colors.text", ReplaceWith("AshTheme.colors.text"))
val AshE8 = DarkText

@Deprecated("Использовать AshTheme.colors.text2", ReplaceWith("AshTheme.colors.text2"))
val AshMuted = DarkText2

/** Награды, монеты, стрик, выполнено. */
@Deprecated("Использовать AshTheme.colors.warm", ReplaceWith("AshTheme.colors.warm"))
val Gold = Color(0xFFFF9F0A)

/**
 * Второй тёплый цвет старой палитры. Отображается в тот же тёплый акцент:
 * по разделу 10 двух тёплых акцентов в приложении быть не должно.
 */
@Deprecated("Использовать AshTheme.colors.warm", ReplaceWith("AshTheme.colors.warm"))
val Ember = Color(0xFFFF9F0A)

/** Фокус, таймеры, счётчики отказов. */
@Deprecated("Использовать AshTheme.colors.cold", ReplaceWith("AshTheme.colors.cold"))
val Steel = Color(0xFF5E5CE6)

/** Подтверждение бэкапа. */
@Deprecated("Использовать AshTheme.colors.success", ReplaceWith("AshTheme.colors.success"))
val Moss = Color(0xFF30D158)

/** Срыв, удаление, просрочка. */
@Deprecated("Использовать AshTheme.colors.danger", ReplaceWith("AshTheme.colors.danger"))
val Blood = Color(0xFFFF453A)

/**
 * Метки приоритета P1–P4 для экранов, ещё не переписанных на токены.
 * Порядок совпадает с [priorityColors].
 */
val PriorityColors: List<Color> = listOf(
    Color(0xFFFF453A),
    Color(0xFFFF9F0A),
    Color(0xFF5E5CE6),
    DarkText2
)
