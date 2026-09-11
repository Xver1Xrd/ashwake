package dev.ashwake.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.ashwake.ui.theme.AshShapes
import dev.ashwake.ui.theme.AshTheme

data class ContextMenuItem(
    val title: String,
    val icon: ImageVector? = null,
    val isDestructive: Boolean = false,
    val onClick: () -> Unit
)

@Composable
fun AshContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<ContextMenuItem>,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp)
) {
    val colors = AshTheme.colors
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = offset,
        modifier = modifier
            .background(colors.surface2, AshShapes.card)
            .padding(vertical = 4.dp)
    ) {
        items.forEach { item ->
            val itemColor = if (item.isDestructive) colors.danger else colors.text
            DropdownMenuItem(
                text = {
                    Text(
                        text = item.title,
                        style = AshTheme.type.body,
                        color = itemColor
                    )
                },
                leadingIcon = item.icon?.let { icon ->
                    {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = itemColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                onClick = {
                    onDismissRequest()
                    item.onClick()
                },
                colors = MenuDefaults.itemColors(
                    textColor = itemColor,
                    leadingIconColor = itemColor
                )
            )
        }
    }
}
