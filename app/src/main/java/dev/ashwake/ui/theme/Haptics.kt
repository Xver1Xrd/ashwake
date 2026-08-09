package dev.ashwake.ui.theme

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * Тактильный отклик, раздел 6 дизайн-системы.
 *
 * Отклик обязателен и **различается по смыслу**: одинаковая вибрация на
 * отметке привычки и на срыве обесценивает и то, и другое. Поэтому здесь
 * не одна функция, а четыре именованных события.
 */
enum class HapticKind {
    /** Отметка, переключатель, выбор в сегментированном контроле. */
    LIGHT,

    /** Удаление, свайп до конца, долгое нажатие. */
    MEDIUM,

    /** Достижение вехи, закрытие всех задач дня. */
    SUCCESS,

    /** Срыв, отмена необратимого действия. */
    WARNING
}

/**
 * Проигрывает отклик. Короткие события идут через [View], потому что так их
 * уважает системная настройка «тактильный отклик»; составные — через
 * предопределённые эффекты вибратора, которых в [HapticFeedbackConstants] нет.
 */
class Haptics(
    private val view: View,
    private val context: Context
) {
    fun play(kind: HapticKind) {
        when (kind) {
            HapticKind.LIGHT -> view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            HapticKind.MEDIUM -> view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            HapticKind.SUCCESS -> predefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
            HapticKind.WARNING -> predefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        }
    }

    private fun predefined(effect: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // До Android 10 предопределённых эффектов нет — падать назад
            // на длинное нажатие честнее, чем молча не дать отклика
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            return
        }
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
    return remember(view, context) { Haptics(view, context) }
}
