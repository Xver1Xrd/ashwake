package dev.ashwake.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Непрерывное скругление (squircle) из дизайн-системы, раздел 4.
 *
 * Обычный [RoundedCornerShape] — дуга окружности, которая стыкуется с прямой
 * стороной со скачком кривизны. Глаз этот скачок замечает, и именно он выдаёт
 * «андроидную» карточку. Здесь угол — четверть суперэллипса
 * `|x/r|^n + |y/r|^n = 1`, у которого кривизна нарастает плавно.
 *
 * [smoothing] переводится в показатель степени: 0 даёт обычную окружность
 * (n = 2), 1 — предельно «квадратный» угол. Значение по умолчанию 0.6
 * соответствует тому, что рисует iOS.
 *
 * Углы считаются полилинией: при радиусах 10–20dp двенадцати сегментов
 * на угол хватает, чтобы граница читалась гладкой на любой плотности,
 * а стоит это дешевле, чем подбирать контрольные точки безье.
 */
class SquircleShape(
    private val radius: Dp,
    private val smoothing: Float = DEFAULT_SMOOTHING
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val maxRadius = min(size.width, size.height) / 2f
        val r = min(with(density) { radius.toPx() }, maxRadius)
        if (r <= 0f) return Outline.Rectangle(size.toRect())
        return Outline.Generic(squirclePath(size, r, smoothing))
    }

    override fun equals(other: Any?): Boolean =
        other is SquircleShape && other.radius == radius && other.smoothing == smoothing

    override fun hashCode(): Int = 31 * radius.hashCode() + smoothing.hashCode()

    companion object {
        const val DEFAULT_SMOOTHING = 0.6f
    }
}

private fun Size.toRect() = androidx.compose.ui.geometry.Rect(0f, 0f, width, height)

private const val SEGMENTS = 12
private const val QUARTER = (Math.PI / 2.0).toFloat()

private fun squirclePath(size: Size, r: Float, smoothing: Float): Path {
    val w = size.width
    val h = size.height
    // 0 → окружность, 1 → почти прямой угол. Диапазон подобран так, чтобы
    // значение 0.6 попадало в «эппловскую» середину.
    val n = 2f + 4f * smoothing.coerceIn(0f, 1f)
    val power = 2f / n

    return Path().apply {
        moveTo(r, 0f)
        lineTo(w - r, 0f)
        corner(w - r, r, r, sx = 1f, sy = -1f, from = QUARTER, to = 0f, power = power)
        lineTo(w, h - r)
        corner(w - r, h - r, r, sx = 1f, sy = 1f, from = 0f, to = QUARTER, power = power)
        lineTo(r, h)
        corner(r, h - r, r, sx = -1f, sy = 1f, from = QUARTER, to = 0f, power = power)
        lineTo(0f, r)
        corner(r, r, r, sx = -1f, sy = -1f, from = 0f, to = QUARTER, power = power)
        close()
    }
}

/**
 * Четверть суперэллипса вокруг центра угла. Знаки [sx] и [sy] выбирают,
 * в какой из четырёх углов смотрит дуга, [from] и [to] — направление обхода:
 * контур рисуется по часовой стрелке, поэтому у соседних углов они обратные.
 */
private fun Path.corner(
    cx: Float,
    cy: Float,
    r: Float,
    sx: Float,
    sy: Float,
    from: Float,
    to: Float,
    power: Float
) {
    for (i in 0..SEGMENTS) {
        val t = from + (to - from) * i / SEGMENTS
        val x = cx + sx * r * cos(t).coerceAtLeast(0f).pow(power)
        val y = cy + sy * r * sin(t).coerceAtLeast(0f).pow(power)
        lineTo(x, y)
    }
}

/**
 * Набор форм приложения.
 *
 * Радиусы больше не константы: их масштаб и стиль угла выбираются в
 * редакторе темы, поэтому набор собирается из настроек и раздаётся через
 * тему. Шкала внутри набора остаётся пропорциональной — чем крупнее блок,
 * тем больше радиус, и группа, карточка и лист читаются как один язык форм.
 */
@androidx.compose.runtime.Immutable
data class AshShapeScheme(
    val smoothing: Float = SquircleShape.DEFAULT_SMOOTHING,
    /** Непрерывное скругление или обычная дуга окружности. */
    val continuous: Boolean = true,
    val smallRadius: Dp = 12.dp,
    val groupRadius: Dp = 18.dp,
    val cardRadius: Dp = 24.dp,
    val sheetRadius: Dp = 30.dp,
    val alertRadius: Dp = 26.dp,
    val pillPercent: Int = 50
) {
    /** Мелкое: значки, метки, вложенные плашки. */
    val small: Shape get() = shape(smallRadius)
    /** Группы сгруппированных списков и поля ввода. */
    val group: Shape get() = shape(groupRadius)
    /** Карточки и плитки. */
    val card: Shape get() = shape(cardRadius)
    /** Обложка, герой-блок, модальные листы. */
    val sheet: Shape get() = shape(sheetRadius)
    /** Alert: всегда обычное скругление, блок маленький и почти квадратный. */
    val alert: Shape get() = RoundedCornerShape(alertRadius)
    /** Кнопки-таблетки, чипы, аватары, панель вкладок. */
    val pill: Shape get() = RoundedCornerShape(percent = pillPercent)

    /** Верхние углы модального листа: низ прижат к краю экрана. */
    val sheetTop: Shape
        get() = RoundedCornerShape(topStart = sheetRadius, topEnd = sheetRadius)

    /** Скругление под конкретный размер: аватар задачи, значок в строке. */
    fun shape(radius: Dp): Shape = when {
        radius <= 0.dp -> RoundedCornerShape(0.dp)
        continuous -> SquircleShape(radius, smoothing)
        else -> RoundedCornerShape(radius)
    }

    /** Форма под размер с поправкой на масштаб радиусов из настроек. */
    fun scaled(radius: Dp): Shape = shape(radius * (cardRadius / 24.dp))
}

val LocalAshShapes = androidx.compose.runtime.staticCompositionLocalOf { AshShapeScheme() }

/**
 * Короткое обращение к формам: `AshShapes.card`.
 *
 * Свойства читают текущую тему, поэтому вызываются только из композиции —
 * ровно там, где формы и нужны.
 */
object AshShapes {
    val small: Shape @Composable get() = LocalAshShapes.current.small
    val group: Shape @Composable get() = LocalAshShapes.current.group
    val card: Shape @Composable get() = LocalAshShapes.current.card
    val sheet: Shape @Composable get() = LocalAshShapes.current.sheet
    val alert: Shape @Composable get() = LocalAshShapes.current.alert
    val pill: Shape @Composable get() = LocalAshShapes.current.pill
    val sheetTop: Shape @Composable get() = LocalAshShapes.current.sheetTop

    @Composable
    fun squircle(radius: Dp): Shape = LocalAshShapes.current.scaled(radius)
}

/**
 * Формы для Material-компонентов на ещё не переписанных экранах.
 * Собираются из того же набора, чтобы чипы и диалоги не жили своей формой.
 */
fun AshShapeScheme.toMaterialShapes(): androidx.compose.material3.Shapes =
    androidx.compose.material3.Shapes(
        extraSmall = RoundedCornerShape(smallRadius * 0.8f),
        small = RoundedCornerShape(smallRadius),
        medium = RoundedCornerShape(groupRadius),
        large = RoundedCornerShape(cardRadius),
        extraLarge = RoundedCornerShape(sheetRadius)
    )
