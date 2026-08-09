package dev.ashwake.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.ashwake.ui.theme.AshTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

/**
 * Материалы из раздела 2 дизайн-системы.
 *
 * Навигационная панель, панель вкладок и заголовки листов полупрозрачны и
 * размывают фон под собой. Это ключевой признак эппловского интерфейса:
 * сплошная заливка вместо материала убивает всё ощущение, поэтому размытие
 * здесь не украшение, а обязательная часть.
 *
 * Радиус 30dp, поверх — тонировка «Поверхности 1» с прозрачностью 70%.
 */
private val BLUR_RADIUS = 30.dp

/**
 * Размытие фона появилось в API 31. Ниже — сплошная «Поверхность 1»,
 * это заявленная в дизайн-системе допустимая деградация: приложение
 * остаётся читаемым, просто без стекла.
 */
private val blurSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun rememberHazeState(): HazeState = remember { HazeState() }

/**
 * Помечает контент, который должен просвечивать сквозь панели.
 * Вешается на прокручиваемое содержимое экрана, а не на весь Scaffold:
 * размывать нужно то, что уезжает под панель, а не саму панель.
 */
fun Modifier.hazeSource(state: HazeState): Modifier = haze(state)

/**
 * Полупрозрачная панель с размытием того, что под ней.
 *
 * @param state тот же [HazeState], что передан в [hazeSource]
 * @param shape форма панели; у листов скруглены только верхние углы
 */
@Composable
fun Modifier.glass(
    state: HazeState,
    shape: Shape = RectangleShape
): Modifier {
    val colors = AshTheme.colors
    return if (blurSupported) {
        hazeChild(
            state = state,
            shape = shape,
            style = HazeStyle(
                backgroundColor = colors.background,
                tint = HazeTint(colors.materialTint),
                blurRadius = BLUR_RADIUS,
                noiseFactor = 0f
            )
        )
    } else {
        background(colors.surface1, shape)
    }
}
