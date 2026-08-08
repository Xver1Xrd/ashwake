package dev.ashwake.benchmark

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Холодный старт приложения (п. 11 плана).
 *
 * Мерять надо именно холодный: тёплый старт показывает скорость восстановления
 * из памяти, а человек чаще всего открывает приложение после того,
 * как система его выгрузила.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = ITERATIONS,
        startupMode = StartupMode.COLD
    ) {
        pressHome()
        startActivityAndWait()
    }

    private companion object {
        const val PACKAGE = "dev.ashwake"
        const val ITERATIONS = 10
    }
}
