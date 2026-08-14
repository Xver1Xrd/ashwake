package dev.ashwake.ui

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.ExternalResource

/**
 * Отключает движение системной настройкой — до того, как поднимется экран.
 *
 * Приложение честно слушает «уменьшить движение», но читает настройку один
 * раз при первой отрисовке. Из `@Before` уже поздно: правило Compose поднимает
 * активность раньше, и персонаж успевает завести бесконечный цикл кадров.
 * Пока цикл идёт, Compose никогда не считается спокойным и `waitForIdle`
 * не возвращается.
 *
 * Правило нужно **каждому** тесту с композицией, даже тому, который сам
 * ничего не ждёт. Тесты модуля идут в общей JVM: класс, оставивший после себя
 * бесконечную анимацию, роняет по таймауту следующий — и падает при этом не
 * он, а сосед, который ни в чём не виноват. Именно так `GlassRenderTest`
 * ронял `NavigationTest`.
 */
class ReduceMotionRule : ExternalResource() {
    override fun before() {
        Settings.Global.putFloat(
            ApplicationProvider.getApplicationContext<Context>().contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
    }
}
