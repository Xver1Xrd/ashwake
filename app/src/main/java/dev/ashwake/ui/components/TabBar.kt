package dev.ashwake.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.ashwake.ui.theme.AshShapes
import dev.ashwake.ui.theme.AshTheme
import dev.ashwake.ui.theme.HapticKind
import dev.ashwake.ui.theme.rememberHaptics
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Вкладка нижней панели навигации (тянущийся остров).
 */
data class TabItem(
    val route: String,
    val title: String,
    val icon: ImageVector
)

private val TabBarHeight = 52.dp
private val TabBarInset = 24.dp
private const val SELECTED_FILL_ALPHA = 0.18f

private fun rubberBand(offset: Float, limit: Float = 110f): Float {
    val sign = if (offset < 0f) -1f else 1f
    val absOffset = abs(offset)
    return sign * (limit * (1f - (1f / (absOffset * 0.42f / limit + 1f))))
}

/**
 * Парящий тянущийся остров навигации в стиле iOS Dynamic Island.
 *
 * Особенности:
 * - При оттягивании пальцем за пределы бара сам остров растягивается резиной (Rubber-Band Jelly Stretch).
 * - Капля гарантированно удерживается внутри острова и не улетает в никуда.
 * - При переключении остров делает эластичный стрейч и возвращается на место.
 * - Иконки центрированы с каплей (соосность 100%), стрелочки цикла используют упругий iOS Spring Bounce.
 */
@Composable
fun AshTabBar(
    tabs: List<TabItem>,
    selectedRoute: String?,
    modifier: Modifier = Modifier,
    onSelect: (TabItem) -> Unit
) {
    val colors = AshTheme.colors
    val accentColor = colors.accent
    val haze = LocalHazeState.current
    val haptics = rememberHaptics()
    val select by rememberUpdatedState(onSelect)
    val reduceMotion = AshTheme.reduceMotion
    val scope = rememberCoroutineScope()

    var pressedIndex by remember { mutableIntStateOf(-1) }
    var dragRatio by remember { mutableFloatStateOf(0f) }
    val animatedOverscroll = remember { Animatable(0f) }

    val activeIndex = when {
        pressedIndex >= 0 -> pressedIndex
        else -> tabs.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)
    }

    var prevIndex by remember { mutableIntStateOf(activeIndex) }
    val islandStretchX = remember { Animatable(1f) }
    val islandSquashY = remember { Animatable(1f) }

    // При смене вкладки весь остров делает упругий Dynamic Island стрейч
    LaunchedEffect(activeIndex) {
        val distance = abs(activeIndex - prevIndex)
        if (distance > 0 && !reduceMotion) {
            val stretchAmount = 1f + (distance * 0.035f).coerceAtMost(0.09f)
            val squashAmount = 1f - (distance * 0.025f).coerceAtMost(0.06f)

            launch {
                islandStretchX.animateTo(
                    targetValue = stretchAmount,
                    animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f)
                )
                islandStretchX.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.65f, stiffness = 380f)
                )
            }
            launch {
                islandSquashY.animateTo(
                    targetValue = squashAmount,
                    animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f)
                )
                islandSquashY.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.65f, stiffness = 380f)
                )
            }
        }
        prevIndex = activeIndex
    }

    // Двухточечная жидкостная анимация внутренней капли
    val movingRight = activeIndex >= prevIndex
    val leadSpring = remember(reduceMotion) {
        if (reduceMotion) tween<Float>(0)
        else spring<Float>(dampingRatio = 0.62f, stiffness = 420f)
    }
    val trailSpring = remember(reduceMotion) {
        if (reduceMotion) tween<Float>(0)
        else spring<Float>(dampingRatio = 0.72f, stiffness = 280f)
    }

    val targetFloat = activeIndex.toFloat()

    val leftEdge by animateFloatAsState(
        targetValue = targetFloat,
        animationSpec = if (movingRight) trailSpring else leadSpring,
        label = "island-left-edge"
    )
    val rightEdge by animateFloatAsState(
        targetValue = targetFloat,
        animationSpec = if (movingRight) leadSpring else trailSpring,
        label = "island-right-edge"
    )

    val islandAlpha by animateFloatAsState(
        targetValue = if (selectedRoute == null && pressedIndex < 0) 0f else 1f,
        animationSpec = tween(250),
        label = "island-alpha"
    )

    // Расчёт физики резинового растяжения острова при оттягивании за границы
    val currentOverscroll = animatedOverscroll.value
    val rubberOffset = if (reduceMotion) 0f else rubberBand(currentOverscroll, 85f)
    val extraStretchX = (abs(rubberOffset) / 200f).coerceAtMost(0.24f)
    val extraSquashY = (abs(rubberOffset) / 400f).coerceAtMost(0.12f)

    val dynamicBarScaleX = (if (pressedIndex >= 0 && !reduceMotion) {
        1f + abs(dragRatio) * 0.03f
    } else {
        islandStretchX.value
    }) + extraStretchX

    val dynamicBarScaleY = (if (pressedIndex >= 0 && !reduceMotion) {
        1f - abs(dragRatio) * 0.02f
    } else {
        islandSquashY.value
    }) - extraSquashY

    val barTranslationX = rubberOffset * 0.5f
    val transformOrigin = when {
        rubberOffset > 2f -> TransformOrigin(0f, 0.5f) // тянем вправо -> закреплён левый край
        rubberOffset < -2f -> TransformOrigin(1f, 0.5f) // тянем влево -> закреплён правый край
        else -> TransformOrigin.Center
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = TabBarInset, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TabBarHeight)
                .graphicsLayer {
                    scaleX = dynamicBarScaleX
                    scaleY = dynamicBarScaleY
                    translationX = barTranslationX
                    this.transformOrigin = transformOrigin
                }
                .clip(AshShapes.pill)
                .then(
                    if (haze != null) Modifier.glass(haze, AshShapes.pill)
                    else Modifier.background(colors.surface1, AshShapes.pill)
                )
                .border(
                    width = if (colors.isDark) 0.75.dp else 1.dp,
                    color = if (colors.isDark) colors.surface3 else colors.separator,
                    shape = AshShapes.pill
                )
                .pointerInput(tabs) {
                    if (tabs.isEmpty()) return@pointerInput

                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var index = tabIndexAt(down.position.x, size.width, tabs.size)
                        pressedIndex = index
                        dragRatio = 0f
                        scope.launch { animatedOverscroll.snapTo(0f) }
                        haptics.play(HapticKind.LIGHT)
                        select(tabs[index])

                        drag(down.id) { change ->
                            val rawX = change.position.x
                            val totalW = size.width.toFloat()
                            val next = tabIndexAt(rawX, size.width, tabs.size)
                            val currentCellCenter = (index + 0.5f) * (size.width / tabs.size)
                            dragRatio = ((rawX - currentCellCenter) / (size.width / tabs.size)).coerceIn(-1f, 1f)

                            val overscroll = when {
                                rawX < 0f -> rawX
                                rawX > totalW -> rawX - totalW
                                else -> 0f
                            }
                            scope.launch { animatedOverscroll.snapTo(overscroll) }

                            if (next != index) {
                                index = next
                                pressedIndex = next
                                dragRatio = 0f
                                haptics.play(HapticKind.LIGHT)
                                select(tabs[next])
                            }
                            change.consume()
                        }

                        // При отпускании остров пружинно возвращается на место
                        scope.launch {
                            animatedOverscroll.animateTo(
                                0f,
                                animationSpec = spring(dampingRatio = 0.58f, stiffness = 420f)
                            )
                        }
                        pressedIndex = -1
                        dragRatio = 0f
                    }
                }
                .padding(horizontal = 4.dp)
                .drawBehind {
                    if (tabs.isEmpty() || islandAlpha <= 0f) return@drawBehind

                    val cell = size.width / tabs.size
                    val pad = 4.dp.toPx()
                    val basePillWidth = cell - pad * 2
                    val baseHeight = size.height - pad * 2

                    // Центры левого и правого краев капли
                    val leftCenter = (minOf(leftEdge, rightEdge) * cell) + (cell / 2f)
                    val rightCenter = (maxOf(leftEdge, rightEdge) * cell) + (cell / 2f)

                    val dragExtra = dragRatio * (cell * 0.35f)
                    val rawLeft = (leftCenter - basePillWidth / 2f) + minOf(0f, dragExtra)
                    val rawRight = (rightCenter + basePillWidth / 2f) + maxOf(0f, dragExtra)

                    // Капля строго удерживается внутри острова и не улетает за его границы
                    val dynamicLeft = rawLeft.coerceIn(0f, size.width - basePillWidth)
                    val dynamicRight = rawRight.coerceIn(basePillWidth, size.width)

                    val islandWidth = (dynamicRight - dynamicLeft).coerceAtLeast(basePillWidth)
                    val stretchExcess = (islandWidth - basePillWidth) / basePillWidth
                    val squashY = (stretchExcess * 3.5.dp.toPx()).coerceAtMost(baseHeight * 0.2f)

                    val dynamicHeight = baseHeight - squashY
                    val top = (size.height - dynamicHeight) / 2f

                    // Жидкая фоновая капсула
                    drawRoundRect(
                        color = accentColor.copy(alpha = SELECTED_FILL_ALPHA * islandAlpha),
                        topLeft = Offset(dynamicLeft, top),
                        size = Size(islandWidth, dynamicHeight),
                        cornerRadius = CornerRadius(dynamicHeight / 2f)
                    )

                    // Чёткий контур активной зоны
                    drawRoundRect(
                        color = accentColor.copy(alpha = (if (colors.isDark) 0.38f else 0.5f) * islandAlpha),
                        topLeft = Offset(dynamicLeft, top),
                        size = Size(islandWidth, dynamicHeight),
                        cornerRadius = CornerRadius(dynamicHeight / 2f),
                        style = Stroke(width = 1.dp.toPx())
                    )
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tab ->
                TabCell(
                    tab = tab,
                    selected = if (pressedIndex >= 0) index == pressedIndex else tab.route == selectedRoute,
                    pressed = index == pressedIndex,
                    onActivate = { select(tab) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun tabIndexAt(x: Float, width: Int, count: Int): Int {
    if (width <= 0 || count <= 0) return 0
    val cell = width.toFloat() / count
    return (x / cell).toInt().coerceIn(0, count - 1)
}

@Composable
private fun TabCell(
    tab: TabItem,
    selected: Boolean,
    pressed: Boolean,
    onActivate: () -> Unit,
    modifier: Modifier
) {
    val colors = AshTheme.colors
    val reduceMotion = AshTheme.reduceMotion

    val contentColor by animateColorAsState(
        targetValue = if (selected) colors.accent else colors.text2,
        animationSpec = tween(200),
        label = "tab-content-color"
    )

    // Полностью переработанная анимация иконок: упругий iOS Spring Bounce
    // Иконка строго центрирована, без боковых сдвигов (translationX = 0)
    val iconScale by animateFloatAsState(
        targetValue = when {
            reduceMotion -> 1f
            pressed -> 0.85f
            selected -> 1.14f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = if (selected) 0.52f else 0.7f,
            stiffness = if (selected) 460f else 380f
        ),
        label = "tab-icon-scale"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(AshShapes.pill)
            .semantics {
                this.selected = selected
                this.role = Role.Tab
                this.contentDescription = tab.title
                set(SemanticsProperties.Text, listOf(AnnotatedString(tab.title)))
                onClick(label = tab.title) { onActivate(); true }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                }
        )
    }
}
