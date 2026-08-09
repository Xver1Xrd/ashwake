package dev.ashwake.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
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
 * Радиусы из раздела 4. Больше в приложении не появляется никаких:
 * если понадобился новый радиус — это признак, что компонент придуман мимо системы.
 */
object AshShapes {
    /** Группы сгруппированных списков. */
    val group = SquircleShape(10.dp)
    /** Карточки и плитки. */
    val card = SquircleShape(16.dp)
    /** Обложка и модальные листы сверху. */
    val sheet = SquircleShape(20.dp)
    /** Alert — единственное место с круговым скруглением: он и в iOS такой. */
    val alert = RoundedCornerShape(14.dp)
    /** Кнопки-таблетки, чипы, аватары. */
    val pill = RoundedCornerShape(percent = 50)

    /** Верхние углы модального листа: низ прижат к краю экрана. */
    val sheetTop = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

    /** Скругление только сверху у обложки, когда она схлопывается. */
    fun squircle(radius: Dp) = SquircleShape(radius)
}
