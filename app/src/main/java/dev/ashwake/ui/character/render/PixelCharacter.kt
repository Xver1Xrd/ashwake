package dev.ashwake.ui.character.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import dev.ashwake.domain.model.character.EquipSlot
import dev.ashwake.ui.theme.Ash1A
import dev.ashwake.ui.theme.Ash3A
import dev.ashwake.ui.theme.AshE8
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** Слой персонажа: слот, цвет палитры и подпись для плейсхолдера. */
data class CharacterLayer(
    val slot: EquipSlot,
    val color: Color,
    val label: String,
    val frames: Int = 1
)

/**
 * Плейсхолдерный рендер персонажа (п. 15.12).
 *
 * До появления арта каждый слот рисуется прямоугольником своего цвета
 * с подписью. Смысл в том, что вся система — каталог, характеристики,
 * магазин, сеты, перекрытия слоёв — собирается и проверяется
 * **без единого готового спрайта**.
 *
 * Правила из п. 15.1 соблюдаются уже сейчас, чтобы подмена плейсхолдеров
 * на спрайты не потребовала переписывать разметку:
 * - холст ровно [CANVAS] пикселей, предмет знает своё место сам;
 * - масштаб только целочисленный, `floor(доступная высота / 128)`, минимум 2;
 * - пол на строке 116, центр симметрии — колонка 64.
 */
const val CANVAS = 128f
private const val FLOOR_ROW = 116f
private const val CENTER_COLUMN = 64f
private const val MIN_SCALE = 2

/** Геометрия слота на холсте 128×128: x, y, ширина, высота в пикселях холста. */
private val SLOT_RECTS: Map<EquipSlot, FloatArray> = mapOf(
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

/**
 * Персонаж целиком.
 *
 * Анимация идёт через [withFrameNanos], а не через таймер с рекомпозицией (п. 15.11):
 * перерисовывается только Canvas, дерево композиции не трогается.
 */
@Composable
fun PixelCharacter(
    layers: List<CharacterLayer>,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
    dimFace: Boolean = false,
    decay: Float = 0f
) {
    var breathOffset by remember { mutableIntStateOf(0) }
    var glowPhase by remember { mutableFloatStateOf(0f) }
    var cloakOffset by remember { mutableIntStateOf(0) }
    var scene by remember { mutableStateOf(CharacterScene.NONE) }
    val measurer = rememberTextMeasurer()

    // «Уменьшить движение» оставляет только дыхание (раздел 6 дизайн-системы),
    // и это же экономит кадры: при выключенном движении цикл не запускается
    // вовсе, а не крутится вхолостую, выставляя одни и те же нули
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) {
            breathOffset = 0
            glowPhase = 0f
            cloakOffset = 0
            scene = CharacterScene.NONE
            return@LaunchedEffect
        }

        var start = 0L
        var nextSceneAt = SCENE_MIN_GAP_MS
        while (true) {
            withFrameNanos { nanos ->
                if (start == 0L) start = nanos
                val elapsedMs = (nanos - start) / 1_000_000

                // Дыхание: 4 кадра, цикл 500 мс, смещение ровно на 1 пиксель холста
                breathOffset = ((elapsedMs / (BREATH_CYCLE_MS / 4)) % 4).toInt().let {
                    if (it == 1 || it == 2) 1 else 0
                }
                glowPhase = sin(elapsedMs / 1000.0 * Math.PI).toFloat()

                // Плащ живёт своим циклом: если он качается в такт дыханию,
                // персонаж выглядит одной деталью, а не тканью поверх тела
                cloakOffset = ((elapsedMs / (CLOAK_CYCLE_MS / 4)) % 4).toInt().let {
                    if (it == 1) 1 else if (it == 3) -1 else 0
                }

                // Случайная сценка раз в 12–20 секунд (п. 15.6)
                if (elapsedMs >= nextSceneAt) {
                    scene = CharacterScene.entries.random()
                    nextSceneAt = elapsedMs + SCENE_DURATION_MS +
                        SCENE_MIN_GAP_MS + (0..SCENE_GAP_JITTER_MS).random()
                } else if (scene != CharacterScene.NONE &&
                    elapsedMs > nextSceneAt - SCENE_MIN_GAP_MS - SCENE_GAP_JITTER_MS
                ) {
                    scene = CharacterScene.NONE
                }
            }
        }
    }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val availablePx = with(density) { minOf(maxWidth, maxHeight).toPx() }
        // Дробный масштаб запрещён: пиксель-арт обязан ложиться на целые пиксели
        val scale = floor(availablePx / CANVAS).toInt().coerceAtLeast(MIN_SCALE)

        Box {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val originX = (size.width - CANVAS * scale) / 2f
                val originY = (size.height - CANVAS * scale) / 2f

                drawShadow(originX, originY, scale, breathOffset)

                layers.forEach { layer ->
                    val rect = SLOT_RECTS[layer.slot] ?: return@forEach
                    // Корпус и голова дышат, ноги не двигаются вообще (п. 15.6)
                    val breathes = layer.slot.breathes
                    val yShift = when {
                        reduceMotion -> 0
                        layer.slot.isCloak -> -cloakOffset
                        scene == CharacterScene.HOP && layer.slot != EquipSlot.BACKGROUND_FX -> -2
                        breathes -> -breathOffset
                        else -> 0
                    }
                    val alpha = when {
                        layer.slot == EquipSlot.FACE && dimFace -> FACE_DIM_ALPHA
                        layer.slot == EquipSlot.FACE && scene == CharacterScene.BLINK -> 0.55f
                        else -> 1f
                    }
                    drawSlot(
                        rect = rect,
                        color = layer.color.desaturate(decay),
                        alpha = alpha,
                        originX = originX,
                        originY = originY,
                        scale = scale,
                        yShift = yShift,
                        label = layer.label,
                        measurer = measurer
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawShadow(originX: Float, originY: Float, scale: Int, breath: Int) {
    // Эллипс 32×8 под ногами, чуть сжимается на вдохе (п. 15.8)
    val width = (32f - breath) * scale
    val height = 8f * scale
    drawOval(
        color = Color.Black.copy(alpha = 0.35f),
        topLeft = Offset(
            originX + (CENTER_COLUMN * scale) - width / 2f,
            originY + FLOOR_ROW * scale - height / 2f
        ),
        size = Size(width, height)
    )
}

private fun DrawScope.drawSlot(
    rect: FloatArray,
    color: Color,
    alpha: Float,
    originX: Float,
    originY: Float,
    scale: Int,
    yShift: Int,
    label: String,
    measurer: TextMeasurer
) {
    val left = originX + rect[0] * scale
    val top = originY + (rect[1] + yShift) * scale
    val width = rect[2] * scale
    val height = rect[3] * scale

    drawRect(
        color = color.copy(alpha = color.alpha * alpha),
        topLeft = Offset(left, top),
        size = Size(width, height)
    )
    drawRect(
        color = Ash1A.copy(alpha = alpha),
        topLeft = Offset(left, top),
        size = Size(width, height),
        style = Stroke(width = scale.toFloat())
    )

    // Подпись слота — то, ради чего плейсхолдер и нужен: видно, что куда легло
    if (width > MIN_LABEL_WIDTH * scale) {
        val text = measurer.measure(
            label,
            style = TextStyle(fontSize = (3.5f * scale).sp, color = AshE8.copy(alpha = alpha))
        )
        drawText(
            textLayoutResult = text,
            topLeft = Offset(
                left + (width - text.size.width) / 2f,
                top + (height - text.size.height) / 2f
            )
        )
    }
}

/** Состояние упадка при пропусках: прогрессивная десатурация (п. 15.8). */
private fun Color.desaturate(amount: Float): Color {
    if (amount <= 0f) return this
    val gray = (red * 0.299f + green * 0.587f + blue * 0.114f)
    val k = amount.coerceIn(0f, MAX_DECAY)
    return Color(
        red = red + (gray - red) * k,
        green = green + (gray - green) * k,
        blue = blue + (gray - blue) * k,
        alpha = alpha
    )
}

/**
 * Короткая сценка, которая оживляет простой. Появляется редко и длится
 * недолго: постоянное движение на главном экране отвлекает от списка дел.
 */
enum class CharacterScene { NONE, BLINK, HOP, CLOAK_GUST }

/** Слои плаща качаются отдельно от дыхания. */
private val EquipSlot.isCloak: Boolean
    get() = this == EquipSlot.CLOAK_FRONT || this == EquipSlot.CLOAK_BACK

/** Слоты, которые участвуют в дыхании: ноги и обувь стоят на месте. */
private val EquipSlot.breathes: Boolean
    get() = this !in setOf(EquipSlot.LEGS, EquipSlot.BOOTS, EquipSlot.BACKGROUND_FX)

/** Цвет обводки по редкости — рисуется поверх собранного персонажа. */
fun rarityOutline(color: Color, phase: Float): Color =
    color.copy(alpha = 0.4f + 0.4f * ((phase + 1f) / 2f))

private const val BREATH_CYCLE_MS = 500L
private const val CLOAK_CYCLE_MS = 1_700L
private const val SCENE_DURATION_MS = 700L
private const val SCENE_MIN_GAP_MS = 12_000L
private const val SCENE_GAP_JITTER_MS = 8_000
private const val FACE_DIM_ALPHA = 0.4f
private const val MAX_DECAY = 0.7f
private const val MIN_LABEL_WIDTH = 14f

/** Заглушка фона диорамы до появления арта. */
val PlaceholderFloor: Color = Ash3A
