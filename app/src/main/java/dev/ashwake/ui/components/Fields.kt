package dev.ashwake.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.ashwake.ui.theme.AshShapes
import dev.ashwake.ui.theme.AshTheme

/**
 * Поле ввода.
 *
 * Материаловское `OutlinedTextField` тянет за собой рамку с вырезом под
 * подпись, плавающий label и собственные отступы — три детали, по которым
 * экран мгновенно опознаётся как чужой. Здесь поле — это просто скруглённая
 * поверхность с текстом внутри, а подпись стоит над ней и никуда не ездит.
 *
 * В фокусе появляется контур акцентом: без него не видно, куда именно
 * попадёт набранное, когда полей на экране несколько.
 */
@Composable
fun AshTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    textStyle: TextStyle = AshTheme.type.body,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    enabled: Boolean = true
) {
    val colors = AshTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    Column(modifier.fillMaxWidth()) {
        label?.let { FieldLabel(it) }

        Box(
            Modifier
                .fillMaxWidth()
                .background(colors.surface2, AshShapes.group)
                .border(
                    width = if (focused) 1.5.dp else 0.dp,
                    color = if (focused) colors.accent else androidx.compose.ui.graphics.Color.Transparent,
                    shape = AshShapes.group
                )
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            if (value.isEmpty() && placeholder != null) {
                Text(placeholder, style = textStyle, color = colors.text3)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = singleLine,
                minLines = minLines,
                textStyle = textStyle.copy(color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = keyboardOptions,
                interactionSource = interactionSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = textStyle.fontSize.value.dp)
            )
        }
    }
}

/** Подпись над полем и над секцией формы. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = AshTheme.type.footnote,
        color = AshTheme.colors.text2,
        modifier = modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}
