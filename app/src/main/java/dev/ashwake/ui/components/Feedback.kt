package dev.ashwake.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ashwake.ui.theme.AshShapes
import dev.ashwake.ui.theme.AshTheme
import kotlinx.coroutines.delay

/**
 * Уведомление об успешном действии — компактная плашка сверху
 * (раздел 5 дизайн-системы). Snackbar снизу не используется: он перекрывает
 * панель вкладок, тянет за собой материаловскую кнопку действия и живёт
 * по своим правилам анимации.
 */
private const val TOAST_MS = 2000L

@Stable
class ToastState {
    var message: String? by mutableStateOf(null)
        private set

    /** Показывает плашку. Повторный вызов перебивает предыдущую. */
    fun show(text: String) {
        message = text
    }

    internal fun clear() {
        message = null
    }
}

@Composable
fun rememberToastState(): ToastState = remember { ToastState() }

/**
 * Хост плашки. Кладётся в Box поверх содержимого экрана и прижимается
 * к верхнему краю под навигационной панелью.
 */
@Composable
fun BoxScope.ToastHost(state: ToastState, modifier: Modifier = Modifier) {
    val message = state.message

    LaunchedEffect(message) {
        if (message != null) {
            delay(TOAST_MS)
            state.clear()
        }
    }

    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier.align(Alignment.TopCenter)
    ) {
        Box(
            Modifier
                .statusBarsPadding()
                .padding(top = 8.dp, start = ScreenPadding, end = ScreenPadding)
                .widthIn(max = 320.dp)
                .background(AshTheme.colors.surface2, AshShapes.pill)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = message.orEmpty(),
                style = AshTheme.type.subhead,
                color = AshTheme.colors.text,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Пустое состояние (раздел 5): иконка, заголовок, объяснение в две строки
 * максимум и текстовая кнопка. Пустой экран без объяснения — это не
 * «чисто», а тупик, поэтому действие здесь обязательное.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    val colors = AshTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.text3,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = title,
            style = AshTheme.type.title3,
            color = colors.text,
            textAlign = TextAlign.Center
        )
        description?.let {
            Text(
                text = it,
                style = AshTheme.type.body,
                color = colors.text2,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
        if (actionText != null && onAction != null) {
            TextAction(text = actionText, onClick = onAction)
        }
    }
}
