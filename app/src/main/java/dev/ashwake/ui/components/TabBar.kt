package dev.ashwake.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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

/**
 * Безупречная функция резинового растяжения (iOS Rubber Band Physics).
 * Не даёт резких перегибов, монотонно сопротивляется и полностью исключает дрожание.
 */
private fun rubberBandOffset(offsetPx: Float, maxStretchPx: Float = 75f): Float {
    if (offsetPx == 0f) return 0f
    val sign = if (offsetPx < 0f) -1f else 1f
    val absOffset = abs(offsetPx)
    return sign * (maxStretchPx * (1f - (1f / (absOffset * 0.35f / maxStretchPx + 1f))))
}

/**
 * Парящий тянущийся остров навигации в стиле iOS Dynamic Island.
 *
 * Особенности:
 * - Обработка жестов вынесена на стабильный контейнер — нулевая тряска и дрожание даже при сильном оттягивании.
 * - При оттягивании пальцем за края бара сам остров плавно тянется резиной (Rubber-Band Jelly Stretch).
 * - Капля строго удерживается в границах острова и не улетает в никуда.
 * - Иконки центрированы с каплей (соосность 100%), анимация стрелочек — упругий iOS Spring Bounce.
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
    val density = LocalDensity.current
    val insetPx = with(density) { TabBarInset.toPx() }

    var pressedIndex by remember { mutableIntStateOf(-1) }
    var rawOverscrollPx by remember { mutableFloatStateOf(0f) }

    val activeIndex = when {
        pressedIndex >= 0 -> pressedIndex
        else -> tabs.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)
    }

    var prevIndex by remember { mutableIntStateOf(activeIndex) }
    val tabSwitchBounceX = remember { Animatable(1f) }
    val tabSwitchBounceY = remember { Animatable(1f) }

    // Эластичный микровсплеск острова при смене вкладки
    LaunchedEffect(activeIndex) {
        val distance = abs(activeIndex - prevIndex)
        if (distance > 0 && !reduceMotion) {
            val stretch = 1f + (distance * 0.025f).coerceAtMost(0.06f)
            val squash = 1f - (distance * 0.018f).coerceAtMost(0.04f)

            launch {
                tabSwitchBounceX.animateTo(stretch, spring(dampingRatio = 0.5f, stiffness = 700f))
                tabSwitchBounceX.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 400f))
            }
            launch {
                tabSwitchBounceY.animateTo(squash, spring(dampingRatio = 0.5f, stiffness = 700f))
                tabSwitchBounceY.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 400f))
            }
        }
        prevIndex = activeIndex
    }

    // Двухточечная жидкостная физика внутренней капли
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
        animationSpec = tween(220),
        label = "island-alpha"
    )

    // Плавный амортизированный оверскролл без рывков
    val smoothOverscroll by animateFloatAsState(
        targetValue = if (reduceMotion) 0f else rawOverscrollPx,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 550f),
        label = "island-overscroll"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = TabBarInset, vertical = 8.dp)
            .pointerInput(tabs) {
                if (tabs.isEmpty()) return@pointerInput

                awaitEachGesture {
                    val down = awaitFirstDown()
                    val barLeft = insetPx
                    val barRight = (size.width - insetPx).coerceAtLeast(barLeft + 1f)
                    val barWidth = barRight - barLeft
                    val cellWidth = barWidth / tabs.size
                    var currentTab = ((down.position.x - barLeft) / cellWidth).toInt().coerceIn(0, tabs.size - 1)

                    pressedIndex = currentTab
                    rawOverscrollPx = 0f
                    haptics.play(HapticKind.LIGHT)
                    select(tabs[currentTab])

                    drag(down.id) { change ->
                        val currentX = change.position.x

                        // Расчёт оверскролла строго от физических краёв плашки
                        rawOverscrollPx = when {
                            currentX < barLeft -> currentX - barLeft
                            currentX > barRight -> currentX - barRight
                            else -> 0f
                        }

                        // Выбор вкладки только внутри границ острова
                        if (currentX in barLeft..barRight) {
                            val newTab = ((currentX - barLeft) / cellWidth).toInt().coerceIn(0, tabs.size - 1)
                            if (newTab != currentTab) {
                                currentTab = newTab
                                pressedIndex = newTab
                                haptics.play(HapticKind.LIGHT)
                                select(tabs[newTab])
                            }
                        }

                        change.consume()
                    }

                    // При отпускании пружина плавно возвращает остров на место
                    pressedIndex = -1
                    rawOverscrollPx = 0f
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TabBarHeight)
                .graphicsLayer {
                    val currentOverscroll = smoothOverscroll
                    val rDist = rubberBandOffset(currentOverscroll, maxStretchPx = 70.dp.toPx())
                    val eStretchX = (abs(rDist) / 260.dp.toPx()).coerceAtMost(0.22f)
                    val eSquashY = (abs(rDist) / 520.dp.toPx()).coerceAtMost(0.11f)

                    scaleX = tabSwitchBounceX.value + eStretchX
                    scaleY = tabSwitchBounceY.value - eSquashY
                    translationX = rDist * 0.55f
                    transformOrigin = TransformOrigin.Center
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

                    val rawLeft = leftCenter - basePillWidth / 2f
                    val rawRight = rightCenter + basePillWidth / 2f

                    // Капля ВСЕГДА строго удерживается внутри острова и не вылетает за границы
                    val dynamicLeft = rawLeft.coerceIn(pad, size.width - pad - basePillWidth)
                    val dynamicRight = rawRight.coerceIn(pad + basePillWidth, size.width - pad)

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

    // Упругий iOS Spring Bounce — иконка строго центрирована, без сдвигов
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
