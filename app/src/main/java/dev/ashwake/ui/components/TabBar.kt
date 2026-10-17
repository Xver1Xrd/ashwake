package dev.ashwake.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import kotlin.math.abs

/** Вкладка нижней панели навигации. */
data class TabItem(
    val route: String,
    val title: String,
    val icon: ImageVector
)

private val TabBarHeight = 52.dp
private val TabBarInset = 24.dp
private const val SELECTED_FILL_ALPHA = 0.18f

private fun rubberBandOffset(offsetPx: Float, maxStretchPx: Float): Float {
    if (offsetPx == 0f) return 0f
    val sign = if (offsetPx < 0f) -1f else 1f
    val distance = abs(offsetPx)
    return sign * (maxStretchPx * (1f - 1f / (1f + distance * 0.35f / maxStretchPx)))
}

/**
 * Нижняя навигация-остров.
 *
 * Сохраняет перетаскивание по вкладкам и резиновое растяжение у краёв. Для
 * плавности положение капсулы и растяжение считаются отдельно и каждый
 * эффект имеет только одну короткую анимацию — без каскадов пружин.
 */
@Composable
fun AshTabBar(
    tabs: List<TabItem>,
    selectedRoute: String?,
    modifier: Modifier = Modifier,
    onSelect: (TabItem) -> Unit
) {
    if (tabs.isEmpty()) return

    val colors = AshTheme.colors
    val haptics = rememberHaptics()
    val select by rememberUpdatedState(onSelect)
    val reduceMotion = AshTheme.reduceMotion
    val density = LocalDensity.current
    val insetPx = with(density) { TabBarInset.toPx() }

    val selectedIndex = tabs.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)
    var touchedIndex by remember { mutableIntStateOf(-1) }
    var pendingIndex by remember { mutableIntStateOf(-1) }
    var rawOverscrollPx by remember { mutableFloatStateOf(0f) }
    val activeIndex = when {
        touchedIndex >= 0 -> touchedIndex
        pendingIndex >= 0 -> pendingIndex
        else -> selectedIndex
    }

    LaunchedEffect(selectedIndex) {
        if (pendingIndex == selectedIndex) pendingIndex = -1
    }

    val indicatorPosition by animateFloatAsState(
        targetValue = activeIndex.toFloat(),
        animationSpec = tween(
            durationMillis = if (reduceMotion) 0 else 240,
            easing = FastOutSlowInEasing
        ),
        label = "island-indicator-position"
    )
    val islandAlpha by animateFloatAsState(
        targetValue = if (selectedRoute == null && touchedIndex < 0) 0f else 1f,
        animationSpec = tween(if (reduceMotion) 0 else 160),
        label = "island-alpha"
    )
    val smoothOverscroll by animateFloatAsState(
        targetValue = if (reduceMotion) 0f else rawOverscrollPx,
        animationSpec = tween(
            durationMillis = if (reduceMotion) 0 else 90,
            easing = FastOutSlowInEasing
        ),
        label = "island-overscroll"
    )

    fun choose(index: Int) {
        val bounded = index.coerceIn(0, tabs.lastIndex)
        if (pendingIndex != bounded) haptics.play(HapticKind.LIGHT)
        pendingIndex = bounded
        select(tabs[bounded])
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = TabBarInset, vertical = 8.dp)
            .pointerInput(tabs) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val left = insetPx
                    val right = (size.width - insetPx).coerceAtLeast(left + 1f)
                    val cellWidth = (right - left) / tabs.size.toFloat()
                    var current = ((down.position.x - left) / cellWidth)
                        .toInt()
                        .coerceIn(0, tabs.lastIndex)
                    touchedIndex = current
                    rawOverscrollPx = 0f
                    choose(current)

                    drag(down.id) { change ->
                        val x = change.position.x
                        rawOverscrollPx = when {
                            x < left -> x - left
                            x > right -> x - right
                            else -> 0f
                        }
                        if (x in left..right) {
                            val next = ((x - left) / cellWidth).toInt().coerceIn(0, tabs.lastIndex)
                            if (next != current) {
                                current = next
                                touchedIndex = next
                                choose(next)
                            }
                        }
                        change.consume()
                    }
                    touchedIndex = -1
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
                    val rubber = rubberBandOffset(smoothOverscroll, 70.dp.toPx())
                    val stretch = (abs(rubber) / 320.dp.toPx()).coerceAtMost(0.12f)
                    scaleX = 1f + stretch
                    scaleY = 1f - stretch * 0.45f
                    translationX = rubber * 0.28f
                }
                .clip(AshShapes.pill)
                .background(colors.surface1, AshShapes.pill)
                .border(
                    width = if (colors.isDark) 0.75.dp else 1.dp,
                    color = if (colors.isDark) colors.surface3 else colors.separator,
                    shape = AshShapes.pill
                )
                .padding(horizontal = 4.dp)
                .drawBehind {
                    if (islandAlpha <= 0f) return@drawBehind
                    val cellWidth = size.width / tabs.size
                    val horizontalPadding = 4.dp.toPx()
                    val indicatorWidth = cellWidth - horizontalPadding * 2f
                    val indicatorHeight = size.height - horizontalPadding * 2f
                    val baseLeft = (
                        indicatorPosition * cellWidth +
                            (cellWidth - indicatorWidth) / 2f
                        ).coerceIn(horizontalPadding, size.width - horizontalPadding - indicatorWidth)
                    val rubber = rubberBandOffset(smoothOverscroll, 70.dp.toPx())
                    val stretch = (abs(rubber) / 220.dp.toPx()).coerceAtMost(0.24f)
                    val stretchedWidth = indicatorWidth * (1f + stretch)
                    val stretchedHeight = indicatorHeight * (1f - stretch * 0.28f)
                    val left = (baseLeft - (stretchedWidth - indicatorWidth) / 2f)
                        .coerceIn(horizontalPadding, size.width - horizontalPadding - stretchedWidth)
                    val top = (size.height - stretchedHeight) / 2f

                    drawRoundRect(
                        color = colors.accent.copy(alpha = SELECTED_FILL_ALPHA * islandAlpha),
                        topLeft = Offset(left, top),
                        size = Size(stretchedWidth, stretchedHeight),
                        cornerRadius = CornerRadius(stretchedHeight / 2f)
                    )
                    drawRoundRect(
                        color = colors.accent.copy(
                            alpha = (if (colors.isDark) 0.38f else 0.5f) * islandAlpha
                        ),
                        topLeft = Offset(left, top),
                        size = Size(stretchedWidth, stretchedHeight),
                        cornerRadius = CornerRadius(stretchedHeight / 2f),
                        style = Stroke(width = 1.dp.toPx())
                    )
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tab ->
                TabCell(
                    tab = tab,
                    selected = index == activeIndex,
                    onActivate = { choose(index) },
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
    onActivate: () -> Unit,
    modifier: Modifier
) {
    val colors = AshTheme.colors
    val reduceMotion = AshTheme.reduceMotion
    val contentColor by animateColorAsState(
        targetValue = if (selected) colors.accent else colors.text2,
        animationSpec = tween(if (reduceMotion) 0 else 160),
        label = "tab-content-color"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = tween(
            durationMillis = if (reduceMotion) 0 else 180,
            easing = FastOutSlowInEasing
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
                .size(23.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                    transformOrigin = TransformOrigin.Center
                }
        )
    }
}
