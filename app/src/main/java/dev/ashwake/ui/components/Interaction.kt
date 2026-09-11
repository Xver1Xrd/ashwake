package dev.ashwake.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
 * 3D-наклон (Parallax/Tilt) карточки при касании в стиле iOS/tvOS.
 * Использует надежный [MutableInteractionSource], работающий внутри любых списков и скроллов.
 */
@Composable
fun Modifier.parallaxTilt(
    maxTiltDegrees: Float = 5f,
    enabled: Boolean = true
): Modifier {
    if (!enabled || AshTheme.reduceMotion) return this

    var targetTiltX by remember { mutableFloatStateOf(0f) }
    var targetTiltY by remember { mutableFloatStateOf(0f) }
    var isTouching by remember { mutableStateOf(false) }

    val animTiltX by animateFloatAsState(targetValue = targetTiltX, animationSpec = responseSpring(), label = "tilt-x")
    val animTiltY by animateFloatAsState(targetValue = targetTiltY, animationSpec = responseSpring(), label = "tilt-y")
    val animScale by animateFloatAsState(targetValue = if (isTouching) 0.965f else 1f, animationSpec = responseSpring(), label = "tilt-scale")
    val density = LocalDensity.current

    return this
        .graphicsLayer {
            rotationX = animTiltX
            rotationY = animTiltY
            scaleX = animScale
            scaleY = animScale
            cameraDistance = 12f * density.density
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                isTouching = true
                val width = size.width.toFloat()
                val height = size.height.toFloat()
                if (width > 0 && height > 0) {
                    val normX = ((down.position.x / width) - 0.5f) * 2f
                    val normY = ((down.position.y / height) - 0.5f) * 2f
                    targetTiltY = (normX * maxTiltDegrees).coerceIn(-maxTiltDegrees, maxTiltDegrees)
                    targetTiltX = (-normY * maxTiltDegrees).coerceIn(-maxTiltDegrees, maxTiltDegrees)
                }

                do {
                    val event = awaitPointerEvent()
                    val current = event.changes.firstOrNull { it.id == down.id }
                    if (current != null && current.pressed && width > 0 && height > 0) {
                        val normX = ((current.position.x / width) - 0.5f) * 2f
                        val normY = ((current.position.y / height) - 0.5f) * 2f
                        targetTiltY = (normX * maxTiltDegrees).coerceIn(-maxTiltDegrees, maxTiltDegrees)
                        targetTiltX = (-normY * maxTiltDegrees).coerceIn(-maxTiltDegrees, maxTiltDegrees)
                    }
                } while (event.changes.any { it.pressed })

                isTouching = false
                targetTiltX = 0f
                targetTiltY = 0f
            }
        }
}

/**
 * Нажатие по-эппловски: тактильное сжатие, лёгкий 3D-наклон, без дешёвого ripple.
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
    val density = LocalDensity.current

    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) 0.965f else 1f,
        animationSpec = responseSpring(),
        label = "press-scale"
    )
    val tiltX by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) -2.5f else 0f,
        animationSpec = responseSpring(),
        label = "press-tilt-x"
    )
    val alpha by animateFloatAsState(
        targetValue = if (pressed) 0.82f else 1f,
        animationSpec = tween(PRESS_MS),
        label = "press-alpha"
    )

    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            rotationX = tiltX
            this.alpha = alpha
            cameraDistance = 12f * density.density
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

