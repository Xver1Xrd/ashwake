package dev.ashwake.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.ashwake.ui.theme.AshTheme

/**
 * Панель вкладок из раздела 5 дизайн-системы.
 *
 * Подписи видны всегда, активная вкладка отличается **только цветом иконки
 * и подписи**. Никаких «пилюль» под иконкой и полосок-индикаторов: это
 * чистый Material, по которому андроид-приложение узнаётся мгновенно.
 *
 * Панель полупрозрачная и размывает то, что уезжает под неё.
 */
data class TabItem(
    val route: String,
    val title: String,
    val icon: ImageVector
)

private val TabBarHeight = 50.dp

@Composable
fun AshTabBar(
    tabs: List<TabItem>,
    selectedRoute: String?,
    modifier: Modifier = Modifier,
    onSelect: (TabItem) -> Unit
) {
    val colors = AshTheme.colors
    val haze = LocalHazeState.current

    Column(
        modifier
            .fillMaxWidth()
            .then(if (haze != null) Modifier.glass(haze) else Modifier.background(colors.surface1))
    ) {
        // Тонкая линия сверху вместо тени: иерархия строится светлотой
        // и разделителями, теней в приложении нет (раздел 2)
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.separator))

        Row(
            Modifier
                .fillMaxWidth()
                .height(TabBarHeight)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val selected = tab.route == selectedRoute
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .tappable(onClick = { onSelect(tab) })
                        .semantics { this.selected = selected }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        tint = if (selected) colors.accent else colors.text2,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = tab.title,
                        style = AshTheme.type.caption,
                        color = if (selected) colors.accent else colors.text2
                    )
                }
            }
        }
    }
}
