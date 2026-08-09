package dev.ashwake.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.ashwake.ui.theme.AshShapes
import dev.ashwake.ui.theme.AshTheme
import dev.ashwake.ui.theme.HapticKind
import androidx.compose.ui.res.stringResource
import dev.ashwake.R

/**
 * Листы и диалоги, раздел 5 дизайн-системы.
 *
 * Всё, что раньше было модальным диалогом во всю ширину, здесь — лист снизу
 * с ручкой и закрытием свайпом. Alert остаётся только для ошибок,
 * подтверждение опасного действия — Action Sheet.
 */

/** Ручка листа: узкая полоска на «Поверхности 3», как в iOS. */
@Composable
private fun SheetHandle() {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .width(36.dp)
                .height(5.dp)
                .background(AshTheme.colors.surface3, AshShapes.pill)
        )
    }
}

/**
 * Модальный лист. Скругление 20dp сверху, фон «Поверхность 1»,
 * закрытие свайпом и по нажатию мимо.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AshModalSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AshTheme.colors.surface1,
        contentColor = AshTheme.colors.text,
        shape = AshShapes.sheetTop,
        dragHandle = { SheetHandle() },
        modifier = modifier
    ) {
        title?.let {
            Text(
                text = it,
                style = AshTheme.type.title3,
                color = AshTheme.colors.text,
                modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp)
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
            content = content
        )
    }
}

/**
 * Подтверждение опасного действия.
 *
 * Заголовок называет **последствие**, а не спрашивает «вы уверены»; красная
 * кнопка действия; «Отмена» отдельной группой снизу, как в iOS. Возврата
 * к материаловскому диалогу с двумя ссылками в углу здесь нет.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSheet(
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    message: String? = null,
    destructive: Boolean = true
) {
    val colors = AshTheme.colors
    AshModalSheet(onDismiss = onDismiss) {
        Column(
            Modifier.padding(horizontal = ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = AshTheme.type.headline,
                color = colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            message?.let {
                Text(
                    text = it,
                    style = AshTheme.type.footnote,
                    color = colors.text2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Box(Modifier.height(8.dp))
            PrimaryButton(
                text = confirmText,
                danger = destructive,
                haptic = if (destructive) HapticKind.WARNING else HapticKind.LIGHT,
                onClick = onConfirm
            )
            SecondaryButton(text = stringResource(R.string.detail_otmena), onClick = onDismiss)
        }
    }
}

/**
 * Alert — только для ошибок. По центру, радиус 14, две кнопки в ряд,
 * разделённые линией.
 */
@Composable
fun AshAlert(
    title: String,
    message: String?,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissText: String? = null
) {
    val colors = AshTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(270.dp)
                .clip(AshShapes.alert)
                .background(colors.surface2)
        ) {
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = AshTheme.type.headline,
                    color = colors.text,
                    textAlign = TextAlign.Center
                )
                message?.let {
                    Text(
                        text = it,
                        style = AshTheme.type.footnote,
                        color = colors.text2,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.separator))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                if (dismissText != null) {
                    AlertButton(dismissText, Modifier.weight(1f), onClick = onDismiss)
                    Box(Modifier.fillMaxHeight().width(0.5.dp).background(colors.separator))
                }
                AlertButton(confirmText, Modifier.weight(1f), onClick = onConfirm)
            }
        }
    }
}

@Composable
private fun AlertButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .tappable(onClick = onClick)
            .height(44.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = AshTheme.type.body, color = AshTheme.colors.accent)
    }
}
