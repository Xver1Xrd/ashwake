package dev.ashwake.ui.character.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Рендер собранного персонажа в Bitmap.
 *
 * Нужен там, где Compose-канвы нет: виджет на Glance умеет показывать только
 * готовое изображение, а «сохранить портрет» и экспорт экрана в картинку
 * по определению работают с растром.
 *
 * Геометрия берётся из [CharacterGeometry] — той же, по которой рисует экран,
 * поэтому портрет совпадает с тем, что человек видел в приложении.
 */
@Singleton
class CharacterBitmapRenderer @Inject constructor() {

    /**
     * @param scale целочисленный множитель. Для портрета в галерею — 8 (п. 15.9),
     *        для виджета хватает 2–3.
     * @param withFloor рисовать ли тень и подложку: у виджета фон свой.
     */
    fun render(
        layers: List<CharacterLayer>,
        scale: Int = 4,
        background: Int = Color.TRANSPARENT,
        withFloor: Boolean = true,
        frame: Boolean = false
    ): Bitmap {
        val safeScale = scale.coerceAtLeast(1)
        val size = (CharacterGeometry.CANVAS * safeScale).toInt()

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // Сглаживание выключено везде: пиксель-арт обязан оставаться резким (п. 15.1)
        val paint = Paint().apply {
            isAntiAlias = false
            isFilterBitmap = false
        }

        if (background != Color.TRANSPARENT) {
            canvas.drawColor(background)
        }

        if (withFloor) {
            paint.color = Color.argb(90, 0, 0, 0)
            val shadowWidth = 32f * safeScale
            val shadowHeight = 8f * safeScale
            canvas.drawOval(
                RectF(
                    CharacterGeometry.CENTER_COLUMN * safeScale - shadowWidth / 2f,
                    CharacterGeometry.FLOOR_ROW * safeScale - shadowHeight / 2f,
                    CharacterGeometry.CENTER_COLUMN * safeScale + shadowWidth / 2f,
                    CharacterGeometry.FLOOR_ROW * safeScale + shadowHeight / 2f
                ),
                paint
            )
        }

        // Тело первым слоем — портрет должен показывать человека, а не вещи,
        // висящие в воздухе
        drawSprite(canvas, paint, CharacterSprites.body, Color.WHITE, safeScale)

        layers.forEach { layer ->
            val sprite = CharacterSprites.items[layer.spriteId]
            if (sprite != null) {
                drawSprite(canvas, paint, sprite, layer.color.toArgb(), safeScale)
                return@forEach
            }
            val rect = CharacterGeometry.SLOT_RECTS[layer.slot] ?: return@forEach
            val left = rect[0] * safeScale
            val top = rect[1] * safeScale
            val right = (rect[0] + rect[2]) * safeScale
            val bottom = (rect[1] + rect[3]) * safeScale

            paint.style = Paint.Style.FILL
            paint.color = layer.color.toArgb()
            canvas.drawRect(left, top, right, bottom, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = safeScale.toFloat()
            paint.color = Color.argb(200, 26, 22, 34)
            canvas.drawRect(left, top, right, bottom, paint)
        }

        if (frame) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f * safeScale
            paint.color = Color.argb(255, 201, 162, 39)
            val inset = safeScale.toFloat()
            canvas.drawRect(inset, inset, size - inset, size - inset, paint)
        }

        return bitmap
    }

    /**
     * Спрайт в растр теми же координатами, что и на экране: портрет в
     * галерее и виджет обязаны совпадать с тем, что человек видел.
     */
    private fun drawSprite(
        canvas: Canvas,
        paint: Paint,
        sprite: CharacterSprites.Sprite,
        tint: Int,
        scale: Int
    ) {
        paint.style = Paint.Style.FILL
        val compose = androidx.compose.ui.graphics.Color(tint)
        sprite.rows.forEachIndexed { row, chars ->
            val top = SpriteShading.canvasY(sprite.y + row) * scale
            val bottom = SpriteShading.canvasY(sprite.y + row + 1) * scale
            // Ряд одинаковых пикселей — один прямоугольник: иначе между
            // соседними остаётся щель в пиксель, и спрайт выглядит сеткой
            var start = 0
            while (start < chars.length) {
                val char = chars[start]
                var end = start
                while (end + 1 < chars.length && chars[end + 1] == char) end++
                val color = SpriteShading.colorFor(char, compose)
                if (color != null) {
                    paint.color = color.toArgb()
                    canvas.drawRect(
                        SpriteShading.canvasX(sprite.x + start) * scale,
                        top,
                        SpriteShading.canvasX(sprite.x + end + 1) * scale,
                        bottom,
                        paint
                    )
                }
                start = end + 1
            }
        }
    }

    private fun androidx.compose.ui.graphics.Color.toArgb(): Int =
        Color.argb(
            (alpha * 255).toInt(),
            (red * 255).toInt(),
            (green * 255).toInt(),
            (blue * 255).toInt()
        )
}
