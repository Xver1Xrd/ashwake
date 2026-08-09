package dev.ashwake.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ashwake.ui.theme.AshShapes
import dev.ashwake.ui.theme.AshTheme
import dev.ashwake.ui.theme.HapticKind

/**
 * Кнопки из раздела 5 дизайн-системы.
 *
 * Общее для всех: никакого ripple, никакого капса, никаких теней. Нажатие —
 * прозрачность 0.6 и сжатие 0.97 за 100ms, это единственная обратная связь.
 */

/** Высота главной кнопки. */
private val ButtonHeight = 50.dp

/** Минимальная зона нажатия из раздела 4 — меньше делать нельзя. */
private val MinTouchTarget = 44.dp

/**
 * Главное действие: таблетка во всю ширину с заливкой акцентом.
 * Только внизу листа или экрана — по одной на экран.
 */
@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    haptic: HapticKind = HapticKind.LIGHT,
    onClick: () -> Unit
) {
    val colors = AshTheme.colors
    val fill = if (danger) colors.danger else colors.accent
    FilledPill(
        text = text,
        background = if (enabled) fill else colors.surface2,
        textColor = when {
            !enabled -> colors.text3
            colors.isDark -> Color.Black
            else -> Color.White
        },
        enabled = enabled,
        haptic = haptic,
        modifier = modifier,
        onClick = onClick
    )
}

/** Второстепенное действие: та же таблетка на «Поверхности 2». */
@Composable
fun SecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textColor: Color = AshTheme.colors.accent,
    haptic: HapticKind = HapticKind.LIGHT,
    onClick: () -> Unit
) {
    val colors = AshTheme.colors
    FilledPill(
        text = text,
        background = colors.surface2,
        textColor = if (enabled) textColor else colors.text3,
        enabled = enabled,
        haptic = haptic,
        modifier = modifier,
        onClick = onClick
    )
}

@Composable
private fun FilledPill(
    text: String,
    background: Color,
    textColor: Color,
    enabled: Boolean,
    haptic: HapticKind,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ButtonHeight)
            .tappable(enabled = enabled, haptic = haptic, onClick = onClick)
            .background(background, AshShapes.pill),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = AshTheme.type.body.copy(fontWeight = FontWeight.SemiBold),
            color = textColor
        )
    }
}

/**
 * Текстовое действие: просто текст акцентом, без рамки и фона.
 * Живёт в навбаре и внутри строк списка.
 */
@Composable
fun TextAction(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = AshTheme.colors.accent,
    haptic: HapticKind = HapticKind.LIGHT,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .tappable(enabled = enabled, haptic = haptic, onClick = onClick)
            .sizeIn(minWidth = MinTouchTarget, minHeight = MinTouchTarget)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = AshTheme.type.body,
            color = if (enabled) color else AshTheme.colors.text3
        )
    }
}

/**
 * Иконка-действие в навигационной панели. Зона нажатия всегда 44×44,
 * даже если сама иконка мельче.
 */
@Composable
fun IconAction(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = AshTheme.colors.accent,
    haptic: HapticKind = HapticKind.LIGHT,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .tappable(enabled = enabled, haptic = haptic, onClick = onClick)
            .size(MinTouchTarget),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else AshTheme.colors.text3,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Кнопка-таблетка с иконкой и подписью для второстепенных действий в ряд
 * («Отложить», «Пропустить»). Ширина по содержимому.
 */
@Composable
fun ChipButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val colors = AshTheme.colors
    Row(
        modifier = modifier
            .tappable(onClick = onClick)
            .background(
                if (selected) colors.accent else colors.surface2,
                AshShapes.pill
            )
            .sizeIn(minHeight = 34.dp)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val content = when {
            selected && colors.isDark -> Color.Black
            selected -> Color.White
            else -> colors.text
        }
        icon?.let {
            Icon(it, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
        }
        Text(text = text, style = AshTheme.type.subhead, color = content)
    }
}
