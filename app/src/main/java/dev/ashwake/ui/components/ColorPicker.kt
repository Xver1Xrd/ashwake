package dev.ashwake.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.ashwake.ui.theme.AshShapes
import dev.ashwake.ui.theme.AshTheme

/**
 * Выбор цвета тремя ползунками: тон, насыщенность, светлота.
 *
 * Квадрат «насыщенность на светлоту» точнее, но пальцем по нему попадают
 * приблизительно, а цвет интерфейса подбирают именно точно — поэтому здесь
 * три отдельные оси, каждую из которых видно и можно довести до конца.
 *
 * Каждый ползунок закрашен тем, что получится: человек выбирает цвет,
 * а не абстрактное число.
 */
@Composable
fun ColorPicker(
    color: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    val hsv = remember(color) { color.toHsv() }
    var hue by remember(color) { mutableStateOf(hsv[0]) }
    var saturation by remember(color) { mutableStateOf(hsv[1]) }
    var value by remember(color) { mutableStateOf(hsv[2]) }

    fun emit() = onColorChange(hsvColor(hue, saturation, value))

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .background(hsvColor(hue, saturation, value), CircleShape)
                    .border(1.dp, AshTheme.colors.separator, CircleShape)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = hsvColor(hue, saturation, value).toHexString(),
                    style = AshTheme.type.headline,
                    color = AshTheme.colors.text
                )
                Text(
                    text = "тон ${hue.toInt()}° · насыщенность ${(saturation * 100).toInt()}%" +
                        " · яркость ${(value * 100).toInt()}%",
                    style = AshTheme.type.caption,
                    color = AshTheme.colors.text2
                )
            }
        }

        GradientSlider(
            fraction = hue / 360f,
            colors = HueStops,
            onChange = { hue = it * 360f; emit() }
        )
        GradientSlider(
            fraction = saturation,
            colors = listOf(hsvColor(hue, 0f, value), hsvColor(hue, 1f, value)),
            onChange = { saturation = it; emit() }
        )
        GradientSlider(
            fraction = value,
            colors = listOf(Color.Black, hsvColor(hue, saturation, 1f)),
            onChange = { value = it; emit() }
        )
    }
}

/**
 * Ползунок-градиент. Своё управление вместо Material Slider: у того дорожка
 * одноцветная, а здесь она и есть шкала — по ней видно, что получится.
 */
@Composable
private fun GradientSlider(
    fraction: Float,
    colors: List<Color>,
    onChange: (Float) -> Unit
) {
    var width by remember { mutableStateOf(1f) }
    val thumbColor = AshTheme.colors.text

    Box(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onChange((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    onChange((change.position.x / size.width).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(20.dp)
                .background(Brush.horizontalGradient(colors), AshShapes.pill)
        )
        Canvas(Modifier.fillMaxWidth().height(36.dp)) {
            width = size.width
            val x = (fraction.coerceIn(0f, 1f) * size.width)
                .coerceIn(HANDLE_RADIUS_PX, size.width - HANDLE_RADIUS_PX)
            drawCircle(
                color = thumbColor,
                radius = HANDLE_RADIUS_PX,
                center = Offset(x, size.height / 2f)
            )
            drawCircle(
                color = colors.interpolate(fraction),
                radius = HANDLE_RADIUS_PX - 3f,
                center = Offset(x, size.height / 2f)
            )
        }
    }
}

/** Цвет градиента в точке: нужен, чтобы кружок ползунка показывал выбранное. */
private fun List<Color>.interpolate(fraction: Float): Color {
    if (isEmpty()) return Color.Transparent
    if (size == 1) return first()
    val position = fraction.coerceIn(0f, 1f) * (size - 1)
    val index = position.toInt().coerceAtMost(size - 2)
    return androidx.compose.ui.graphics.lerp(this[index], this[index + 1], position - index)
}

private const val HANDLE_RADIUS_PX = 14f

private val HueStops = listOf(
    Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
    Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000)
)

fun Color.toHsv(): FloatArray = FloatArray(3).also {
    android.graphics.Color.colorToHSV(toArgb(), it)
}

fun hsvColor(hue: Float, saturation: Float, value: Float): Color = Color(
    android.graphics.Color.HSVToColor(
        floatArrayOf(
            hue.coerceIn(0f, 360f),
            saturation.coerceIn(0f, 1f),
            value.coerceIn(0f, 1f)
        )
    )
)

/** `#RRGGBB` — то, чем цвет называют везде, включая переписку с дизайнером. */
fun Color.toHexString(): String = "#%06X".format(toArgb() and 0xFFFFFF)

/** Кружок-образец в ряду выбора. Отмеченный получает контур и галочку. */
@Composable
fun ColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondColor: Color = color
) {
    val colors = AshTheme.colors
    Box(
        modifier
            .size(44.dp)
            .background(Brush.linearGradient(listOf(color, secondColor)), CircleShape)
            .border(
                width = if (selected) 2.5.dp else 0.dp,
                color = if (selected) colors.text else Color.Transparent,
                shape = CircleShape
            )
            .tappable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            androidx.compose.material3.Icon(
                AshIcons.Check,
                contentDescription = null,
                tint = if (color.luminanceIsLight()) Color.Black else Color.White,
                modifier = Modifier.size(20.dp).padding(0.dp)
            )
        }
    }
}

/** Светлый ли цвет: по нему выбирается, чем поверх него рисовать галочку. */
fun Color.luminanceIsLight(): Boolean =
    (0.299f * red + 0.587f * green + 0.114f * blue) > 0.6f
