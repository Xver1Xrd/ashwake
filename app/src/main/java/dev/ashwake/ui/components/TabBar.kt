package dev.ashwake.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.ashwake.ui.theme.AshShapes
import dev.ashwake.ui.theme.AshTheme

/**
 * Панель вкладок.
 *
 * Панель не приклеена к нижнему краю, а висит над содержимым отдельной
 * таблеткой: у экрана появляется дно, содержимое видно под панелью, и вся
 * нижняя часть интерфейса перестаёт быть прямоугольной плашкой во всю ширину.
 *
 * Активная вкладка подсвечивается заливкой акцентом в 16% — без неё на
 * скруглённой панели остаётся только разница в цвете иконки, а этого мало,
 * чтобы поймать текущий раздел боковым зрением.
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
    val haze = LocalHazeState.current

    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = TabBarInset, vertical = 10.dp)
    ) {
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
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                TabCell(
                    tab = tab,
                    selected = tab.route == selectedRoute,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(tab) }
                )
            }
        }
    }
}

@Composable
private fun TabCell(
    tab: TabItem,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val colors = AshTheme.colors
    val reduceMotion = AshTheme.reduceMotion

    // Цвет и подсветка догоняют переключение, а не перескакивают вместе с ним
    val contentColor by animateColorAsState(
        targetValue = if (selected) colors.accent else colors.text2,
        label = "tab-content"
    )
    val fillAlpha by animateFloatAsState(
        targetValue = if (selected) SELECTED_FILL_ALPHA else 0f,
        label = "tab-fill"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected && !reduceMotion) 1.08f else 1f,
        animationSpec = dev.ashwake.ui.components.ashSpring(),
        label = "tab-icon"
    )

    Column(
        modifier = modifier
            .padding(vertical = 5.dp)
            .clip(AshShapes.pill)
            .background(colors.accent.copy(alpha = fillAlpha), AshShapes.pill)
            .tappable(onClick = onClick)
            .semantics { this.selected = selected }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = null,
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
