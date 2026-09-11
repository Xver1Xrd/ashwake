package dev.ashwake.ui.tasks.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ashwake.ui.tasks.TaskDayGroup
import dev.ashwake.ui.theme.AshShapes
import dev.ashwake.ui.theme.AshTheme

/**
 * Заголовок дня в списке задач: название дня (просрочено, сегодня, завтра, день недели)
 * и бейдж с количеством задач.
 */
@Composable
fun TaskDayHeader(
    group: TaskDayGroup,
    modifier: Modifier = Modifier
) {
    val colors = AshTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = group.title,
            style = AshTheme.type.headline,
            color = when {
                group.isOverdue -> colors.danger
                group.isToday -> colors.accent
                else -> colors.text
            }
        )
        Text(
            text = "${group.tasks.size}",
            style = AshTheme.type.caption,
            color = if (group.isOverdue) colors.danger else colors.text3,
            modifier = Modifier
                .background(
                    if (group.isOverdue) colors.danger.copy(alpha = 0.14f)
                    else colors.surface2,
                    AshShapes.pill
                )
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
