package dev.ashwake.ui.character.render

import androidx.compose.ui.graphics.Color
import dev.ashwake.domain.model.character.EquipItem
import dev.ashwake.domain.model.character.EquipSlot

/** Цвет слоя, если у палитры предмета нет оттенка в каталоге. */
private const val DEFAULT_TINT = 0xFF6E7BA6

/**
 * Сборка слоёв персонажа из надетых предметов.
 *
 * Здесь применяется `hides`: закрытый шлем прячет волосы и лицо, роба —
 * ноги и сапоги. Порядок задаёт z-таблица слотов.
 *
 * Функция общая для экрана персонажа, главного экрана, виджета и рендера
 * в Bitmap: раньше эта логика была скопирована в четыре места и разъехалась
 * бы на первой правке z-порядка.
 */
fun buildCharacterLayers(
    items: Collection<EquipItem>,
    tints: Map<String, Int>
): List<CharacterLayer> {
    val hidden = items.flatMap { it.hides }.toSet()
    return items
        .filterNot { it.slot in hidden }
        .sortedBy { it.layer }
        .map { item ->
            CharacterLayer(
                slot = item.slot,
                color = Color(tints[item.paletteId] ?: DEFAULT_TINT.toInt()),
                label = item.slot.title,
                spriteId = item.baseSpriteId,
                frames = item.frames
            )
        }
}

/**
 * Геометрия персонажа на холсте 128×128 (п. 15.1).
 *
 * Вынесена из компонента в общее место намеренно: по этим же координатам
 * рисует и экран, и рендер в Bitmap для виджета с портретом. Две копии
 * разметки разъехались бы на первой же правке.
 */
object CharacterGeometry {

    const val CANVAS = 128f
    const val FLOOR_ROW = 116f
    const val CENTER_COLUMN = 64f
    const val MIN_SCALE = 2

    /** Слот → прямоугольник на холсте: x, y, ширина, высота. */
    val SLOT_RECTS: Map<EquipSlot, FloatArray> = mapOf(
        EquipSlot.BACKGROUND_FX to floatArrayOf(30f, 18f, 68f, 96f),
        EquipSlot.BACK_ITEM to floatArrayOf(38f, 40f, 52f, 34f),
        EquipSlot.CLOAK_BACK to floatArrayOf(40f, 38f, 48f, 62f),
        EquipSlot.OFFHAND_BACK to floatArrayOf(28f, 52f, 12f, 40f),
        EquipSlot.BODY to floatArrayOf(52f, 34f, 24f, 82f),
        EquipSlot.UNDERLAYER to floatArrayOf(50f, 44f, 28f, 32f),
        EquipSlot.BOOTS to floatArrayOf(50f, 102f, 28f, 14f),
        EquipSlot.LEGS to floatArrayOf(50f, 76f, 28f, 28f),
        EquipSlot.CHEST to floatArrayOf(46f, 42f, 36f, 36f),
        EquipSlot.BELT to floatArrayOf(48f, 74f, 32f, 6f),
        EquipSlot.GLOVES to floatArrayOf(40f, 62f, 48f, 10f),
        EquipSlot.HEAD_HAIR to floatArrayOf(52f, 18f, 24f, 12f),
        EquipSlot.FACE to floatArrayOf(54f, 24f, 20f, 10f),
        EquipSlot.HELMET to floatArrayOf(50f, 16f, 28f, 18f),
        EquipSlot.CLOAK_FRONT to floatArrayOf(44f, 40f, 40f, 60f),
        EquipSlot.MAINHAND to floatArrayOf(86f, 46f, 10f, 46f),
        EquipSlot.SHOULDERS to floatArrayOf(42f, 40f, 44f, 12f),
        EquipSlot.FOREGROUND_FX to floatArrayOf(34f, 22f, 60f, 90f)
    )

    /** Слоты, участвующие в дыхании: ноги и обувь стоят на месте (п. 15.6). */
    val BREATHING_SLOTS: Set<EquipSlot> = EquipSlot.entries.toSet() -
        setOf(EquipSlot.LEGS, EquipSlot.BOOTS, EquipSlot.BACKGROUND_FX)

    fun breathes(slot: EquipSlot): Boolean = slot in BREATHING_SLOTS

    /**
     * Целочисленный масштаб под доступную высоту.
     * Дробный запрещён: пиксель-арт обязан ложиться на целые пиксели (п. 15.1).
     */
    fun scaleFor(availablePx: Float): Int =
        kotlin.math.floor(availablePx / CANVAS).toInt().coerceAtLeast(MIN_SCALE)
}
