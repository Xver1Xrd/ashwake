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

        layers.forEach { layer ->
            val rect = CharacterGeometry.SLOT_RECTS[layer.slot] ?: return@forEach
            val left = rect[0] * safeScale
            val top = rect[1] * safeScale
            val right = (rect[0] + rect[2]) * safeScale
            val bottom = (rect[1] + rect[3]) * safeScale

            paint.style = Paint.Style.FILL
            paint.color = layer.color.toArgb()
            canvas.drawRect(left, top, right, bottom, paint)

            // Обводка отделяет слои друг от друга — на плейсхолдерах без неё
            // видна одна цветная каша
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

    private fun androidx.compose.ui.graphics.Color.toArgb(): Int =
        Color.argb(
            (alpha * 255).toInt(),
            (red * 255).toInt(),
            (green * 255).toInt(),
            (blue * 255).toInt()
        )
}
