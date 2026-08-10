package dev.ashwake.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.ashwake.ui.theme.AshTheme

/**
 * Общие приёмы движения.
 *
 * Правило на все: анимация оправдана, только если что-то объясняет — куда
 * делся объект, откуда взялись монеты, что действие принято. Всё, что
 * оказывается на пути ввода, укладывается в 250 мс: движение, которое надо
 * переждать, чтобы нажать следующее, — это налог, а не украшение.
 *
 * Каждая анимация обязана слушать системное «уменьшить движение»: для этого
 * здесь [motionScale], на который умножаются длительности. При выключенном
 * движении он равен нулю, и всё происходит мгновенно, не ломая логику.
 */

/** Отклик на действие: быстрый, но не мгновенный. */
const val QUICK_MS = 180

/** Смена состояния, за которой нужно проследить глазами. */
const val NORMAL_MS = 260

/** Праздник: единственное место, где движение можно растянуть. */
const val CELEBRATION_MS = 900

/** 0 при выключенном движении, 1 при обычном. Множитель длительностей. */
val motionScale: Float
    @Composable get() = if (AshTheme.reduceMotion) 0f else 1f

@Composable
fun <T> motionTween(durationMillis: Int = NORMAL_MS, delayMillis: Int = 0): FiniteAnimationSpec<T> {
    val scale = motionScale
    return tween(
        durationMillis = (durationMillis * scale).toInt(),
        delayMillis = (delayMillis * scale).toInt()
    )
}

/** Пружина отклика: та же, что у нажатия, чтобы движение было одной семьи. */
fun <T> responseSpring(): AnimationSpec<T> = spring(dampingRatio = 0.7f, stiffness = 500f)

/**
 * Число с перекатом разрядов.
 *
 * Меняется только тот разряд, который действительно изменился: старая цифра
 * уезжает вверх, новая приезжает снизу. Подмена числа целиком читается как
 * «здесь что-то мигнуло», а перекат — как «стало больше», и это ровно то,
 * что нужно сказать про монеты, дни и уровень.
 *
 * Шрифт табличный, поэтому ширина разряда не пляшет и соседние цифры
 * не дёргаются вместе с меняющейся.
 */
@Composable
fun RollingNumber(
    value: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = AshTheme.type.headline,
    color: Color = AshTheme.colors.text,
    minDigits: Int = 1
) {
    val text = value.toString().padStart(minDigits, '0')

    // Спеки считаются здесь: лямбда перехода не композабельная, а длительность
    // зависит от настройки «уменьшить движение», которую можно прочитать
    // только из композиции
    val slideSpec = motionTween<IntOffset>(QUICK_MS)
    val fadeSpec = motionTween<Float>(QUICK_MS)

    Row(modifier) {
        text.forEachIndexed { index, digit ->
            // Ключ по позиции справа: при переходе 99 → 100 разряды не должны
            // считаться другими только потому, что число стало длиннее
            val placeFromEnd = text.length - index
            AnimatedContent(
                targetState = digit,
                transitionSpec = {
                    val up = targetState > initialState || initialState == '9'
                    val direction = if (up) 1 else -1
                    (
                        slideInVertically(slideSpec) { height -> direction * height } +
                            fadeIn(fadeSpec)
                        ) togetherWith (
                        slideOutVertically(slideSpec) { height -> -direction * height } +
                            fadeOut(fadeSpec)
                        )
                },
                label = "digit-$placeFromEnd"
            ) { shown ->
                Text(text = shown.toString(), style = style, color = color)
            }
        }
    }
}

/**
 * Галочка, которая рисуется штрихом.
 *
 * Самое частое действие в приложении — отметка сделанного, и оно заслуживает
 * большего, чем мгновенная подмена значка. Контур прочерчивается за [QUICK_MS]:
 * рука узнаёт этот жест раньше, чем глаз успевает прочитать строку.
 *
 * Длина штриха берётся из [PathMeasure], а не подбирается на глаз, поэтому
 * скорость прочерчивания одинаковая при любом размере.
 */
@Composable
fun DrawnCheck(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 2.dp
) {
    Canvas(modifier) {
        if (progress <= 0f) return@Canvas

        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.22f, h * 0.52f)
            lineTo(w * 0.42f, h * 0.72f)
            lineTo(w * 0.78f, h * 0.28f)
        }

        val measure = PathMeasure().apply { setPath(path, false) }
        val drawn = Path()
        measure.getSegment(0f, measure.length * progress.coerceIn(0f, 1f), drawn, true)

        drawPath(
            path = drawn,
            color = color,
            style = Stroke(
                width = strokeWidth.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

/**
 * Медленная пульсация: значок пустого состояния и всё, что должно выглядеть
 * живым, но ничего не требовать. Амплитуда намеренно маленькая — заметная
 * пульсация на статичном экране читается как ошибка.
 */
@Composable
fun rememberBreath(
    periodMillis: Int = 3200,
    from: Float = 0.97f,
    to: Float = 1.03f
): Float {
    if (AshTheme.reduceMotion) return 1f
    val transition = rememberInfiniteTransition(label = "breath")
    val value by transition.animateFloat(
        initialValue = from,
        targetValue = to,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath-value"
    )
    return value
}
