package dev.ashwake.ui.tasks.components

import dev.ashwake.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.ashwake.domain.engine.nlp.ParsedQuickInput
import dev.ashwake.ui.components.AshIcons
import dev.ashwake.ui.components.AshTextField
import dev.ashwake.ui.components.tappable
import dev.ashwake.ui.theme.AshShapes
import dev.ashwake.ui.theme.AshTheme
import dev.ashwake.ui.theme.colorTitle
import dev.ashwake.ui.theme.priorityColor
import java.time.format.DateTimeFormatter

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Быстрый ввод одной строкой (п. 1, 11).
 *
 * Под полем показываются чипы того, что распозналось: без них пользователь
 * не понимает, сработал парсер или «завтра» осталось частью названия.
 * Приоритет в чипе называется цветом, а не «P2»: в поле его по-прежнему
 * можно задать как `p2`, но обратная связь идёт на языке интерфейса.
 */
@Composable
fun QuickAddBar(
    value: String,
    parsed: ParsedQuickInput?,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onVoiceClick: () -> Unit,
    modifier: Modifier = Modifier,
    listening: Boolean = false
) {
    val colors = AshTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface1, AshShapes.sheetTop)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        if (parsed != null && parsed.hasAnyMarkup) {
            ParsedChips(parsed)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AshTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = stringResource(R.string.components_kupit_moloko_zavtra_18_00_p2_dom_30m_2),
                modifier = Modifier.weight(1f)
            )
            RoundAction(
                icon = AshIcons.Mic,
                description = stringResource(R.string.components_golosovoy_vvod),
                // Во время записи микрофон подсвечен: иначе непонятно,
                // слушает приложение или нет
                tint = if (listening) colors.danger else colors.text2,
                background = colors.surface2,
                onClick = onVoiceClick
            )
            RoundAction(
                icon = AshIcons.ArrowUpward,
                description = stringResource(R.string.routines_dobavit),
                tint = if (value.isBlank()) colors.text3
                else if (colors.isDark) Color.Black else Color.White,
                background = if (value.isBlank()) colors.surface2 else colors.accent,
                enabled = value.isNotBlank(),
                onClick = onSubmit
            )
        }
    }
}

@Composable
private fun RoundAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color,
    background: Color,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Box(
        Modifier
            .size(44.dp)
            .background(background, AshShapes.pill)
            .tappable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ParsedChips(parsed: ParsedQuickInput) {
    val colors = AshTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        parsed.date?.let { Chip(it.format(DATE_FORMAT)) }
        parsed.time?.let { Chip(it.format(TIME_FORMAT)) }
        parsed.priority?.let { Chip(it.colorTitle, colors.priorityColor(it)) }
        parsed.estimateMinutes?.let { Chip(stringResource(R.string.components_1_s_min, it)) }
        parsed.tagNames.forEach { Chip("#$it") }
    }
}

@Composable
private fun Chip(label: String, color: Color? = null) {
    val colors = AshTheme.colors
    val tint = color ?: colors.text2
    Text(
        text = label,
        style = AshTheme.type.caption,
        color = tint,
        modifier = Modifier
            .background(tint.copy(alpha = 0.14f), AshShapes.pill)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}
