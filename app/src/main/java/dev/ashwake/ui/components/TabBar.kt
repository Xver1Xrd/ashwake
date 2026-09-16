package dev.ashwake.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.getValue
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
private const val SELECTED_FILL_ALPHA = 0.16f

/**
 * Парящий тянущийся остров навигации (Island Tab Bar).
 *
 * Особенности:
 * - Без текстовых подписей: чистый, современный минимализм.
 * - Тянущийся бегунок (Squash & Stretch fluid physics) при переключении между экранами.
 * - Детальные микроанимации для каждой иконки при выборе (поворот солнца, спин стрелок, наклон галочки).
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = TabBarInset, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        val targetIndex = when {
            pressedIndex >= 0 -> pressedIndex.toFloat()
            else -> tabs.indexOfFirst { it.route == selectedRoute }
                .takeIf { it >= 0 }?.toFloat() ?: -1f
        }

        val indicatorPos by animateFloatAsState(
            targetValue = targetIndex.coerceAtLeast(0f),
            animationSpec = spring(dampingRatio = 0.68f, stiffness = 340f),
            label = "island-indicator"
        )
        val indicatorAlpha by animateFloatAsState(
            targetValue = if (targetIndex < 0f) 0f else 1f,
            label = "island-indicator-alpha"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TabBarHeight)
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
                        haptics.play(HapticKind.LIGHT)
                        select(tabs[index])

                        drag(down.id) { change ->
                            val next = tabIndexAt(change.position.x, size.width, tabs.size)
                            if (next != index) {
                                index = next
                                pressedIndex = next
                                haptics.play(HapticKind.LIGHT)
                                select(tabs[next])
                            }
                            change.consume()
                        }
                        pressedIndex = -1
                    }
                }
                .padding(horizontal = 4.dp)
                .drawBehind {
                    if (tabs.isEmpty() || indicatorAlpha <= 0f) return@drawBehind

                    val cell = size.width / tabs.size
                    val inset = 4.dp.toPx()
                    val baseWidth = cell - inset * 2
                    val baseHeight = size.height - inset * 2

                    // Физика растяжения (Squash & Stretch): при движении индикатор удлиняется по X и сжимается по Y
                    val delta = if (reduceMotion) 0f else (targetIndex - indicatorPos)
                    val stretchFactor = (abs(delta) * 1.5f).coerceIn(0f, 1f)
                    val elongation = stretchFactor * cell * 0.42f
                    val squash = stretchFactor * 4.dp.toPx()

                    val dynamicWidth = baseWidth + elongation
                    val dynamicHeight = baseHeight - squash

                    val centerX = (indicatorPos * cell) + (cell / 2f)
                    val left = centerX - (dynamicWidth / 2f)
                    val top = (size.height - dynamicHeight) / 2f

                    // Подложка активного острова
                    drawRoundRect(
                        color = accentColor.copy(alpha = SELECTED_FILL_ALPHA * indicatorAlpha),
                        topLeft = Offset(left, top),
                        size = Size(dynamicWidth, dynamicHeight),
                        cornerRadius = CornerRadius(dynamicHeight / 2f)
                    )

                    // Тонкий контур островка
                    drawRoundRect(
                        color = accentColor.copy(alpha = 0.35f * indicatorAlpha),
                        topLeft = Offset(left, top),
                        size = Size(dynamicWidth, dynamicHeight),
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
        label = "tab-content-color"
    )

    val iconScale by animateFloatAsState(
        targetValue = when {
            reduceMotion -> 1f
            pressed -> 0.88f
            selected -> 1.12f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f),
        label = "tab-icon-scale"
    )

    // Детальные микроанимации для каждой иконки при выборе
    val iconRotation by animateFloatAsState(
        targetValue = when {
            reduceMotion -> 0f
            !selected -> 0f
            tab.route == "today" -> 45f      // Солнце делает плавный доворот
            tab.route == "tasks" -> -8f      // Чекбокс делает лёгкий наклон
            tab.route == "habits" -> 360f    // Повторы делают оборот цикла
            else -> 0f
        },
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 320f),
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
                }
        )
    }
}
