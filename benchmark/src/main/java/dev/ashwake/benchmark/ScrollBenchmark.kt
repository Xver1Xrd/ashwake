package dev.ashwake.benchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Плавность прокрутки и анимации персонажа (цель — 60 fps, п. 15.11).
 *
 * `FrameTimingMetric` даёт распределение времени кадра. Смотреть надо
 * не среднее, а P95 и P99: одна просадка в секунду заметна человеку,
 * а в среднем не видна.
 */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun scrollCharacterShop() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            startActivityAndWait()
            // Экран персонажа: там и анимация дыхания, и длинный список магазина
            device.findObject(By.text("Персонаж"))?.click()
            device.waitForIdle()
        }
    ) {
        val list = device.findObject(By.scrollable(true)) ?: return@measureRepeated
        list.setGestureMargin(device.displayWidth / MARGIN_DIVISOR)

        repeat(SCROLLS) {
            list.scroll(Direction.DOWN, SCROLL_PERCENT)
            device.waitForIdle()
        }
    }

    private companion object {
        const val PACKAGE = "dev.ashwake"
        const val ITERATIONS = 5
        const val SCROLLS = 3
        const val SCROLL_PERCENT = 0.8f

        /** Отступ от края: иначе жест перехватывает системная навигация. */
        const val MARGIN_DIVISOR = 5
    }
}
