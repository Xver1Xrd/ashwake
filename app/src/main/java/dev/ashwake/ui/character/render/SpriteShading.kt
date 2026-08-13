package dev.ashwake.ui.character.render

import androidx.compose.ui.graphics.Color

/**
 * Как символ спрайта превращается в цвет.
 *
 * Вынесено из обоих рисовальщиков: экран рисует на Compose-канве, виджет — в
 * Bitmap, и если оттенки считать в двух местах, портрет в виджете рано или
 * поздно перестанет совпадать с тем, что человек видел в приложении.
 *
 * Предмет в каталоге задан одним цветом палитры. Тень и свет берутся из него
 * умножением: так одна и та же куртка в бронзе и в стали остаётся одной
 * курткой, а не двумя разными вещами.
 */
object SpriteShading {

    /** Кожа: не зависит от палитры предмета — это тело, а не одежда. */
    private val SKIN = Color(0xFFE2BEA0)
    private val SKIN_SHADE = Color(0xFFC49E82)
    private val EYE = Color(0xFF201C26)

    /**
     * Контур не чёрный, а очень тёмный оттенок самого предмета: чистый чёрный
     * рядом с тёмной палитрой сливается, а рядом со светлой режет глаз.
     */
    private const val OUTLINE_K = 0.28f
    private const val SHADE_K = 0.62f
    private const val LIGHT_K = 1.28f
    private const val HIGHLIGHT_K = 1.58f

    /** null — пиксель прозрачный. */
    fun colorFor(char: Char, tint: Color): Color? = when (char) {
        'o' -> tint.scaled(OUTLINE_K)
        'd' -> tint.scaled(SHADE_K)
        'm' -> tint
        'l' -> tint.scaled(LIGHT_K)
        'w' -> tint.scaled(HIGHLIGHT_K)
        'k' -> SKIN
        'K' -> SKIN_SHADE
        'e' -> EYE
        else -> null
    }

    private fun Color.scaled(k: Float) = Color(
        red = (red * k).coerceIn(0f, 1f),
        green = (green * k).coerceIn(0f, 1f),
        blue = (blue * k).coerceIn(0f, 1f),
        alpha = alpha
    )

    /**
     * Кукла на холсте 128×128.
     *
     * Ноги стоят на полу [CharacterGeometry.FLOOR_ROW], середина куклы
     * совпадает с центральной колонкой холста. Смещения считаются здесь, а не
     * в рисовальщиках: съехавшая на пиксель кукла — это съехавшая тень.
     */
    const val ORIGIN_X = 33f
    const val ORIGIN_Y = 4f
    const val STEP = 2f

    fun canvasX(dollX: Int): Float = ORIGIN_X + dollX * STEP
    fun canvasY(dollY: Int): Float = ORIGIN_Y + dollY * STEP
}
