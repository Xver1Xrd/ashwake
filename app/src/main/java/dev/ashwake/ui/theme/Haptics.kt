package dev.ashwake.ui.theme

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import dev.ashwake.platform.audio.SoundEffects

/**
 * Тактильный отклик и звуковые эффекты, раздел 6 дизайн-системы.
 */
enum class HapticKind {
    /** Отметка, переключатель, выбор в сегментированном контроле. */
    LIGHT,

    /** Удаление, свайп до конца, долгое нажатие. */
    MEDIUM,

    /** Достижение вехи, закрытие всех задач дня. */
    SUCCESS,

    /** Срыв, отмена необратимого действия. */
    WARNING,

    /** Мягкий щелчок выполнения задачи. */
    TASK_COMPLETE,

    /** Глухой стук заморозки привычки. */
    FREEZE_THUD,

    /** Потрескивание пламени при тапе по огоньку. */
    FLAME_CRACKLE
}

class Haptics(
    private val view: View,
    private val context: Context,
    private val sounds: SoundEffects? = null
) {
    fun play(kind: HapticKind) {
        when (kind) {
            HapticKind.LIGHT -> view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            HapticKind.MEDIUM -> view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            HapticKind.SUCCESS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    predefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                } else {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
            }
            HapticKind.WARNING -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    predefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                } else {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
            }
            HapticKind.TASK_COMPLETE -> {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                sounds?.playClick()
            }
            HapticKind.FREEZE_THUD -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    predefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                } else {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
                sounds?.playThud()
            }
            HapticKind.FLAME_CRACKLE -> {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                sounds?.playCampfire()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun predefined(effect: Int) {
        vibrator()?.takeIf { it.hasVibrator() }
            ?.vibrate(VibrationEffect.createPredefined(effect))
    }

    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    val context = LocalContext.current
    val sounds = remember(context) { SoundEffects(context.applicationContext) }
    return remember(view, context, sounds) { Haptics(view, context, sounds) }
}
