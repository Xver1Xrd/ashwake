package dev.ashwake.ui.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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

/**
 * Парящий тянущийся остров навигации в стиле iOS Dynamic Island.
 *
 * Особенности физики Dynamic Island:
 * - Двухточечный жидкостный стрейч (Liquid Elasticity): ведущий край вытягивается первым,
 *   остров растягивается в эластичную капсулу, а задний край подтягивается с упругим хлопком.
 * - Сохранение объёма (Squash & Stretch): при растяжении по X высота острова слегка сжимается по Y.
 * - Жест протяжки: остров натягивается вслед за пальцем как резина.
 * - Переработанная анимация стрелочек (Habits): упругий механический щелчок цикла со сдвигом по X вместо вращения.
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

    var pressedIndex by remember { mutableIntStateOf(-1) }
    var dragShiftPx by remember { mutableFloatStateOf(0f) }

    val activeIndex = when {
        pressedIndex >= 0 -> pressedIndex
        else -> tabs.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)
    }

    var prevIndex by remember { mutableIntStateOf(activeIndex) }
    LaunchedEffect(activeIndex) {
        prevIndex = activeIndex
    }

    val movingRight = activeIndex >= prevIndex

    // Быстрая ведущая пружина Dynamic Island
    val leadSpring = remember(reduceMotion) {
        if (reduceMotion) tween<Float>(0)
        else spring<Float>(dampingRatio = 0.62f, stiffness = 420f)
    }
    // Догоняющая хвостовая пружина
    val trailSpring = remember(reduceMotion) {
        if (reduceMotion) tween<Float>(0)
        else spring<Float>(dampingRatio = 0.74f, stiffness = 260f)
    }

    val targetFloat = activeIndex.toFloat()

    val leftEdgeTarget = if (movingRight) targetFloat else targetFloat
    val rightEdgeTarget = if (movingRight) targetFloat else targetFloat

    val leftEdge by animateFloatAsState(
        targetValue = leftEdgeTarget,
        animationSpec = if (movingRight) trailSpring else leadSpring,
        label = "island-left-edge"
    )
    val rightEdge by animateFloatAsState(
        targetValue = rightEdgeTarget,
        animationSpec = if (movingRight) leadSpring else trailSpring,
        label = "island-right-edge"
    )

    val islandAlpha by animateFloatAsState(
        targetValue = if (selectedRoute == null && pressedIndex < 0) 0f else 1f,
        animationSpec = tween(250),
        label = "island-alpha"
    )

    val isTouching = pressedIndex >= 0

    val barScaleX by animateFloatAsState(
        targetValue = if (isTouching) 1.015f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "bar-scale-x"
    )
    val barScaleY by animateFloatAsState(
        targetValue = if (isTouching) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "bar-scale-y"
    )

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
                    scaleX = barScaleX
                    scaleY = barScaleY
                }
                .clip(AshShapes.pill)
                .then(
                    if (haze != null) Modifier.glass(haze, AshShapes.pill)
                    else Modifier.background(colors.surface1, AshShapes.pill)
                )
                .border(0.75.dp, colors.surface3, AshShapes.pill)
                .pointerInput(tabs) {
                    if (tabs.isEmpty()) return@pointerInput

                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var index = tabIndexAt(down.position.x, size.width, tabs.size)
                        pressedIndex = index
                        dragShiftPx = 0f
                        haptics.play(HapticKind.LIGHT)
                        select(tabs[index])

                        drag(down.id) { change ->
                            val next = tabIndexAt(change.position.x, size.width, tabs.size)
                            val currentCellCenter = (index + 0.5f) * (size.width / tabs.size)
                            dragShiftPx = (change.position.x - currentCellCenter) * 0.45f

                            if (next != index) {
                                index = next
                                pressedIndex = next
                                dragShiftPx = 0f
                                haptics.play(HapticKind.LIGHT)
                                select(tabs[next])
                            }
                            change.consume()
                        }
                        pressedIndex = -1
                        dragShiftPx = 0f
                    }
                }
                .padding(horizontal = 4.dp)
                .drawBehind {
                    if (tabs.isEmpty() || islandAlpha <= 0f) return@drawBehind

                    val cell = size.width / tabs.size
                    val pad = 4.dp.toPx()
                    val basePillWidth = cell - pad * 2
                    val baseHeight = size.height - pad * 2

                    // Вычисляем координаты краев островка с учётом жидкостной физики
                    val leftCenter = (minOf(leftEdge, rightEdge) * cell) + (cell / 2f)
                    val rightCenter = (maxOf(leftEdge, rightEdge) * cell) + (cell / 2f)

                    // Эластичное растяжение при движении и перетягивании пальцем
                    val dynamicLeft = (leftCenter - basePillWidth / 2f) + minOf(0f, dragShiftPx)
                    val dynamicRight = (rightCenter + basePillWidth / 2f) + maxOf(0f, dragShiftPx)

                    val islandWidth = (dynamicRight - dynamicLeft).coerceAtLeast(basePillWidth)
                    val stretchExcess = (islandWidth - basePillWidth) / basePillWidth
                    val squashY = (stretchExcess * 3.5.dp.toPx()).coerceAtMost(baseHeight * 0.22f)

                    val dynamicHeight = baseHeight - squashY
                    val top = (size.height - dynamicHeight) / 2f

                    // Жидкая фоновая заливка Dynamic Island
                    drawRoundRect(
                        color = accentColor.copy(alpha = SELECTED_FILL_ALPHA * islandAlpha),
                        topLeft = Offset(dynamicLeft, top),
                        size = Size(islandWidth, dynamicHeight),
                        cornerRadius = CornerRadius(dynamicHeight / 2f)
                    )

                    // Тонкое контрастное окаймление островка
                    drawRoundRect(
                        color = accentColor.copy(alpha = 0.38f * islandAlpha),
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
        animationSpec = tween(220),
        label = "tab-content-color"
    )

    val iconScale by animateFloatAsState(
        targetValue = when {
            reduceMotion -> 1f
            pressed -> 0.86f
            selected -> 1.12f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.52f, stiffness = 420f),
        label = "tab-icon-scale"
    )

    val density = androidx.compose.ui.platform.LocalDensity.current.density

    // Новая анимация для стрелочек (Habits/Repeat) с нуля:
    // Вместо вертолетного вращения на 360° — упругий горизонтальный щелчок цикла (Cycle Spring Nudge)
    val cycleShiftX by animateFloatAsState(
        targetValue = when {
            reduceMotion -> 0f
            selected && tab.route == "habits" -> 3f
            else -> 0f
        },
        animationSpec = spring(dampingRatio = 0.42f, stiffness = 500f),
        label = "tab-cycle-shift"
    )

    // Мягкий деликатный наклон только для солнца (Today)
    val iconRotation by animateFloatAsState(
        targetValue = when {
            reduceMotion -> 0f
            !selected -> 0f
            tab.route == "today" -> 20f
            tab.route == "tasks" -> -6f
            else -> 0f // Никакого вращения для стрелочек и остальных!
        },
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 360f),
        label = "tab-icon-rotation"
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
                    rotationZ = iconRotation
                    translationX = cycleShiftX * density
                }
        )
    }
}
