package dev.ashwake.ui.tasks.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.ashwake.core.model.Priority
import dev.ashwake.ui.components.AshIcons
import dev.ashwake.ui.components.tappable
import dev.ashwake.ui.theme.AshShapes
import dev.ashwake.ui.theme.AshTheme
import dev.ashwake.ui.theme.colorTitle
import dev.ashwake.ui.theme.hasMark
import dev.ashwake.ui.theme.meaning
import dev.ashwake.ui.theme.priorityColor

/**
 * Выбор приоритета цветом.
 *
 * Раньше здесь стояли четыре чипа «P1 P2 P3 P4». Аббревиатуру приходится
 * держать в голове и каждый раз вспоминать, где верх шкалы, — а цвет
 * узнаётся без расшифровки. Номер остаётся только в базе.
 *
 * Под каждым цветом подписано, что он значит: цвет сам по себе ничего не
 * сообщает тому, кто различает не все оттенки, да и «зелёный» без пояснения
 * можно понять и как «сделано».
 */
@Composable
fun PriorityPicker(
    selected: Priority,
    onSelect: (Priority) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AshTheme.colors

    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Priority.entries.forEach { priority ->
            val color = colors.priorityColor(priority)
            val isSelected = priority == selected

            Column(
                Modifier
                    .weight(1f)
                    .background(
                        if (isSelected) color.copy(alpha = 0.16f) else colors.surface2,
                        AshShapes.group
                    )
                    .border(
                        width = if (isSelected) 1.5.dp else 0.dp,
                        color = if (isSelected) color else Color.Transparent,
                        shape = AshShapes.group
                    )
                    .tappable(onClick = { onSelect(priority) })
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    Modifier.size(20.dp).background(
                        if (priority.hasMark) color else Color.Transparent,
                        CircleShape
                    ).then(
                        if (priority.hasMark) Modifier
                        else Modifier.border(1.5.dp, colors.text3, CircleShape)
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected && priority.hasMark) {
                        Icon(
                            AshIcons.Check,
                            contentDescription = null,
                            tint = if (colors.isDark) Color.Black else Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Text(
                    text = priority.meaning,
                    style = AshTheme.type.footnote,
                    color = if (isSelected) colors.text else colors.text2,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Метка приоритета в строке списка. У «без метки» ничего не рисуется:
 * четвёртая ступень — это отсутствие приоритета, а не его серый вариант.
 */
@Composable
fun PriorityDot(priority: Priority, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    if (!priority.hasMark) return
    Box(
        modifier
            .size(size)
            .background(AshTheme.colors.priorityColor(priority), CircleShape)
    )
}

/** Название цвета и его смысл одной строкой: для подсказок и подписей. */
fun priorityLabel(priority: Priority): String =
    if (priority.hasMark) "${priority.colorTitle} · ${priority.meaning}" else priority.colorTitle
