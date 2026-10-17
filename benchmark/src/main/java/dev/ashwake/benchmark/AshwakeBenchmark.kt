package dev.ashwake.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Замер реального запуска и переходов по вкладкам на телефоне.
 *
 * Метрики не угадывают «плавность»: CI или разработчик получает время первого
 * кадра и процент медленных кадров для сценария, который видит пользователь.
 */
@RunWith(AndroidJUnit4::class)
class AshwakeBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupAndPrimaryTabs() = benchmarkRule.measureRepeated(
        packageName = "dev.ashwake",
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.COLD,
        iterations = 5,
        setupBlock = {
            pressHome()
            startActivityAndWait()
        }
    ) {
        val device = this.device
        listOf("Задачи", "Привычки", "Ещё", "Сегодня").forEach { tab ->
            device.wait(Until.hasObject(By.desc(tab)), 2_000)
            device.findObject(By.desc(tab)).click()
            device.waitForIdle()
        }
    }
}
