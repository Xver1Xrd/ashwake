package dev.ashwake.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import dev.ashwake.ui.theme.AshTheme
import dev.ashwake.ui.theme.HapticKind
import dev.ashwake.ui.theme.rememberHaptics

/**
 * Базовая пружина из раздела 6 дизайн-системы. Соответствует ощущению iOS;
 * линейных и материаловских кривых в приложении нет.
 */
fun <T> ashSpring() = spring<T>(dampingRatio = 0.85f, stiffness = 400f)

/** Длительность отклика на нажатие, раздел 5. */
private const val PRESS_MS = 100

/**
 * Нажатие по-эппловски: затемнение до 0.6 и сжатие до 0.97, без ripple.
 *
 * Ripple — самый заметный материаловский признак, поэтому [combinedClickable]
 * здесь всегда вызывается с `indication = null`, а обратная связь рисуется
 * трансформацией самого элемента.
 *
 * Долгое нажатие открывает контекстное меню и всегда дублирует свайпы:
 * работа без жестов должна быть возможна полностью (раздел 9).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.tappable(
    enabled: Boolean = true,
    haptic: HapticKind? = HapticKind.LIGHT,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = AshTheme.reduceMotion
    val haptics = rememberHaptics()

    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) 0.97f else 1f,
        animationSpec = tween(PRESS_MS),
        label = "press-scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (pressed) 0.6f else 1f,
        animationSpec = tween(PRESS_MS),
        label = "press-alpha"
    )

    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
        .combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onLongClick = onLongClick?.let { action ->
                {
                    haptics.play(HapticKind.MEDIUM)
                    action()
                }
            },
            onClick = {
                haptic?.let(haptics::play)
                onClick()
            }
        )
}
