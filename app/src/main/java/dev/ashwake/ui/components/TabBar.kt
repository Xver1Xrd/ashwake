package dev.ashwake.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.ashwake.ui.theme.AshShapes
import dev.ashwake.ui.theme.AshTheme
import dev.ashwake.ui.theme.HapticKind
import dev.ashwake.ui.theme.rememberHaptics

/**
 * Панель вкладок.
 *
 * Панель не приклеена к нижнему краю, а висит над содержимым отдельной
 * таблеткой: у экрана появляется дно, содержимое видно под панелью, и вся
 * нижняя часть интерфейса перестаёт быть прямоугольной плашкой во всю ширину.
 *
 * По панели можно **вести пальцем**: вкладка переключается на ту, над которой
 * палец сейчас находится. Это быстрее, чем целиться в каждую по очереди, и
 * работает одним жестом с любого места панели.
 *
 * Жест обрабатывается панелью целиком, а не каждой вкладкой по отдельности.
 * Если бы у ячеек был свой обработчик нажатия, он забирал бы касание себе, и
 * ведение пальцем разваливалось бы на серию отдельных тапов по соседям.
 *
 * Панель полупрозрачная и размывает то, что уезжает под неё.
 */
data class TabItem(
    val route: String,
    val title: String,
    val icon: ImageVector
)

private val TabBarHeight = 58.dp

/** Поля вокруг панели: воздух, из-за которого она читается как объект. */
private val TabBarInset = 14.dp

/** Заливка под активной вкладкой. */
private const val SELECTED_FILL_ALPHA = 0.16f

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

    // Ячейка под пальцем: по ней рисуется отклик на нажатие, пока палец
    // не отпустили. Индекс, а не ссылка на вкладку, чтобы сравнение было
    // дешёвым на каждом событии перемещения
    var pressedIndex by remember { mutableIntStateOf(-1) }

    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = TabBarInset, vertical = 10.dp)
    ) {
        // Бегунок под вкладками: единый объект, который едет между ними.
        // Раньше подсветка гасла у одной ячейки и загоралась у другой —
        // это читается как две вспышки, а не как переход
        val target = when {
            pressedIndex >= 0 -> pressedIndex.toFloat()
            else -> tabs.indexOfFirst { it.route == selectedRoute }
                .takeIf { it >= 0 }?.toFloat() ?: -1f
        }
        val indicator by animateFloatAsState(
            targetValue = target.coerceAtLeast(0f),
            animationSpec = responseSpring(),
            label = "tab-indicator"
        )
        val indicatorAlpha by animateFloatAsState(
            targetValue = if (target < 0f) 0f else 1f,
            label = "tab-indicator-alpha"
        )

        Row(
            Modifier
                .fillMaxWidth()
                .height(TabBarHeight)
                .clip(AshShapes.pill)
                .then(
                    if (haze != null) Modifier.glass(haze, AshShapes.pill)
                    else Modifier.background(colors.surface1, AshShapes.pill)
                )
                // Тонкий контур вместо тени: на светлой теме стекло почти
                // сливается с фоном, и без границы панель теряет края
                .border(0.5.dp, colors.separator, AshShapes.pill)
                .pointerInput(tabs) {
                    if (tabs.isEmpty()) return@pointerInput

                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var index = tabIndexAt(down.position.x, size.width, tabs.size)
                        pressedIndex = index
                        haptics.play(HapticKind.LIGHT)
                        select(tabs[index])

                        // Ведение: вкладка меняется, как только палец
                        // переходит границу соседней. Отдача — на каждой
                        // смене, чтобы переключение чувствовалось вслепую
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
                .padding(horizontal = 6.dp)
                .drawBehind {
                    if (tabs.isEmpty() || indicatorAlpha <= 0f) return@drawBehind
                    val cell = size.width / tabs.size
                    val inset = 3.dp.toPx()
                    drawRoundRect(
                        color = accentColor.copy(alpha = SELECTED_FILL_ALPHA * indicatorAlpha),
                        topLeft = androidx.compose.ui.geometry.Offset(
                            x = indicator * cell + inset,
                            y = inset
                        ),
                        size = androidx.compose.ui.geometry.Size(
                            width = cell - inset * 2,
                            height = size.height - inset * 2
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                            (size.height - inset * 2) / 2f
                        )
                    )
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tab ->
                TabCell(
                    tab = tab,
                    // Пока палец на панели, подсветка идёт за ним, а не за
                    // страницей: пейджер долистывает с задержкой, и ждать
                    // его — значит вести пальцем по неподсвеченным вкладкам
                    selected = if (pressedIndex >= 0) {
                        index == pressedIndex
                    } else {
                        tab.route == selectedRoute
                    },
                    pressed = index == pressedIndex,
                    onActivate = { select(tab) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Вкладка под точкой [x]. Ячейки одной ширины, поэтому индекс считается
 * делением, а не поиском по разметке: разметку тут знать неоткуда, а ширина
 * панели известна.
 */
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

    // Цвет и подсветка догоняют переключение, а не перескакивают вместе с ним
    val contentColor by animateColorAsState(
        targetValue = if (selected) colors.accent else colors.text2,
        label = "tab-content"
    )
    val iconScale by animateFloatAsState(
        targetValue = when {
            reduceMotion -> 1f
            pressed -> 0.92f
            selected -> 1.08f
            else -> 1f
        },
        animationSpec = ashSpring(),
        label = "tab-icon"
    )

    Column(
        modifier = modifier
            .padding(vertical = 5.dp)
            .clip(AshShapes.pill)
            // Жест живёт на панели целиком, поэтому у ячейки нет своего
            // обработчика нажатия — а озвучке нужно и то, что это вкладка,
            // и способ её активировать
            .semantics {
                this.selected = selected
                this.role = Role.Tab
                onClick(label = tab.title) { onActivate(); true }
            }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.title,
            tint = contentColor,
            modifier = Modifier.size(22.dp).scale(iconScale)
        )
        Text(
            text = tab.title,
            style = AshTheme.type.caption,
            color = contentColor
        )
    }
}
