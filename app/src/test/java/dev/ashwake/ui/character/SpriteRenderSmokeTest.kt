package dev.ashwake.ui.character

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import dev.ashwake.domain.model.character.EquipSlot
import dev.ashwake.ui.character.render.CharacterBitmapRenderer
import dev.ashwake.ui.character.render.CharacterLayer
import dev.ashwake.ui.character.render.CharacterSprites
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Спрайты доезжают до растра.
 *
 * Портрет и виджет рисуются в Bitmap, а не на экране: если координаты куклы
 * разъедутся с холстом 128×128, персонаж уедет за край и этого никто не
 * заметит до первого виджета на домашнем экране.
 */
// Рисование проверяется настоящим Skia — режим включён на весь модуль
// в `robolectric.properties`: одному классу его включать нельзя, соседние
// тесты базы в той же JVM теряют нативную привязку SQLite
@RunWith(RobolectricTestRunner::class)
class SpriteRenderSmokeTest {

    @Test
    fun `персонаж рисуется и не выходит за холст`() {
        val renderer = CharacterBitmapRenderer()
        val layers = listOf(
            layer(EquipSlot.CHEST, "chest_hoodie"),
            layer(EquipSlot.LEGS, "legs_jeans"),
            layer(EquipSlot.BOOTS, "boots_sneakers"),
            layer(EquipSlot.HEAD_HAIR, "hair_short")
        )
        val bitmap = renderer.render(layers, scale = 4, withFloor = false)

        assertTrue("нарисовано пусто", filled(bitmap) > 500)
        assertTrue("спрайты вышли за холст", withinBounds(bitmap))

        val dump = System.getenv("ASHWAKE_SPRITE_DUMP")
        if (dump != null) {
            File(dump).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test
    fun `у каждого предмета каталога есть спрайт`() {
        val ids = javaClass.classLoader!!.getResourceAsStream("assets/catalog/items.json")
            ?.bufferedReader()?.readText().orEmpty()
            .let { Regex("\"id\": \"([a-z_]+)\"").findAll(it).map { m -> m.groupValues[1] }.toList() }
        val missing = ids.filterNot { it in CharacterSprites.items }
        assertTrue("без спрайта: $missing", missing.isEmpty())
    }

    private fun layer(slot: EquipSlot, sprite: String) =
        CharacterLayer(slot = slot, color = Color(0xFF6E7BA6), label = slot.name, spriteId = sprite)

    private fun filled(bitmap: Bitmap): Int {
        var n = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            if (bitmap.getPixel(x, y) != 0) n++
        }
        return n
    }

    /** Крайний ряд пикселей обязан остаться пустым: иначе персонаж обрезан. */
    private fun withinBounds(bitmap: Bitmap): Boolean {
        val last = bitmap.width - 1
        for (i in 0 until bitmap.width) {
            if (bitmap.getPixel(i, 0) != 0 || bitmap.getPixel(i, last) != 0) return false
            if (bitmap.getPixel(0, i) != 0 || bitmap.getPixel(last, i) != 0) return false
        }
        return true
    }
}
