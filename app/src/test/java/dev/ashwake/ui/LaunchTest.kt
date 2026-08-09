package dev.ashwake.ui

import android.os.Looper
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dev.ashwake.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Приложение должно открываться.
 *
 * Самый дешёвый из возможных тестов и самый ценный: он ловит падение при
 * старте — то, что без устройства не видно вообще, а с устройством видно
 * как «приложение остановлено» без единой подсказки.
 *
 * Исключения ловятся через обработчик необработанных: падение при запуске
 * прилетело из корутины загрузки каталога, то есть мимо потока теста.
 * Без этого обработчика тест был бы зелёным при неработающем приложении —
 * ровно это и произошло в первый раз.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [33])
class LaunchTest {

    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Test
    fun `главный экран открывается без падения`() {
        val crashes = CopyOnWriteArrayList<Throwable>()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, error -> crashes += error }

        // Персонаж на главном экране дышит бесконечным циклом кадров.
        // На устройстве это ровно то, что нужно, но под Robolectric очередь
        // главного потока из-за него никогда не пустеет. Тест запускается
        // с системной настройкой «уменьшить движение», при которой цикл
        // не стартует вовсе — это штатный путь приложения, а не подпорка.
        Settings.Global.putFloat(
            ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )

        try {
            hilt.inject()
            Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
                checkNotNull(controller.get()) { "активность не создалась" }
                // Стартовый экран подтягивает привычки, задачи и каталог:
                // без прокрутки очереди эти корутины просто не успеют упасть.
                //
                // Прокрутка ограничена по времени, а не idle(): персонаж дышит
                // бесконечным циклом кадров, очередь главного потока никогда
                // не пустеет, и idle() крутился бы до нехватки памяти.
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
            }
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }

        assertTrue(
            "при запуске упало:\n" + crashes.joinToString("\n") { it.stackTraceToString() },
            crashes.isEmpty()
        )
    }
}
