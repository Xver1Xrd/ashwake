package dev.ashwake.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dev.ashwake.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Навигация по вкладкам на настоящем приложении.
 *
 * Тест появился из-за жалобы: человек открывал экран поверх вкладки, жал
 * вкладку и оставался там же, откуда пытался уйти. Причина была в
 * `popUpTo(saveState) + restoreState` — стек с открытым сверху экраном
 * сохранялся вместе с вкладкой и восстанавливался обратно.
 *
 * Ошибка тихая: приложение не падает, тесты логики её не видят, а
 * пользоваться приложением нельзя. Ловится только отсюда.
 *
 * Активность берётся настоящая: `hiltViewModel()` требует активность с
 * графом Hilt, а подставная `ComponentActivity` из тестового правила его
 * не имеет.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [33])
class NavigationTest {

    /**
     * Порядок правил здесь — не оформление, а условие работы теста.
     *
     * Персонаж дышит бесконечным циклом кадров, и пока он крутится,
     * Compose никогда не считается спокойным: `waitForIdle` ждёт, пока
     * никто не просит следующий кадр. Приложение само отключает движение
     * по системной настройке, но читает её один раз при первой отрисовке —
     * значит выставить её надо до того, как правило поднимет активность,
     * то есть снаружи него. Из `@Before` уже поздно: там экран собран.
     */
    @get:Rule(order = 0)
    val reduceMotion = object : ExternalResource() {
        override fun before() {
            Settings.Global.putFloat(
                ApplicationProvider.getApplicationContext<Context>().contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                0f
            )
        }
    }

    @get:Rule(order = 1)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 2)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun `вкладка возвращает с экрана, открытого поверх неё`() {
        skipOnboardingIfShown()
        openTab("Ещё")
        // «Отказы» открываются поверх вкладки «Ещё» — ровно тот случай,
        // на котором приложение застревало
        compose.onNodeWithText("Отказы").performClick()
        awaitText("Отказы")

        openTab("Сегодня")
        assertTabOpen("Сегодня")
        // Главное в этом тесте: экран поверх вкладки действительно закрылся,
        // а не остался под панелью. Заголовок «Сегодня» мог бы появиться и
        // рядом с ним, если бы вкладка просто перерисовалась
        assertEquals(
            "экран отказов остался открытым после нажатия вкладки",
            0,
            compose.onAllNodesWithText("Отказы").fetchSemanticsNodes().size
        )
    }

    @Test
    fun `все четыре вкладки открываются`() {
        skipOnboardingIfShown()
        listOf("Задачи", "Привычки", "Ещё", "Сегодня").forEach(::openTabAndAssert)
    }

    private fun openTabAndAssert(title: String) {
        openTab(title)
        assertTabOpen(title)
    }

    /**
     * Вкладка открыта, если её название видно дважды: подписью на панели и
     * заголовком раздела. Одного вхождения мало — подпись на панели есть
     * всегда, и проверка прошла бы даже на чужом экране.
     */
    private fun assertTabOpen(title: String) {
        val nodes = compose.onAllNodesWithText(title)
        assertEquals(
            "раздел «$title» не открылся: заголовка нет, видна только вкладка",
            2,
            nodes.fetchSemanticsNodes().size
        )
        nodes[0].assertIsDisplayed()
    }

    private fun openTab(title: String) {
        awaitText(title)
        // Заголовок раздела и подпись вкладки совпадают, поэтому берём
        // последний узел: панель вкладок лежит поверх содержимого
        val nodes = compose.onAllNodesWithText(title)
        nodes[nodes.fetchSemanticsNodes().lastIndex].performClick()
        compose.waitForIdle()
    }

    /**
     * Ждём появления текста, а не полагаемся на `waitForIdle`.
     *
     * Экран поднимается на данных из базы и настроек: они приходят
     * корутинами, и «очередь пуста» не означает «данные пришли».
     */
    private fun awaitText(text: String) {
        compose.waitUntil(AWAIT_MS) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun skipOnboardingIfShown() {
        compose.waitForIdle()
        val skip = compose.onAllNodesWithText("Пропустить")
        if (skip.fetchSemanticsNodes().isNotEmpty()) {
            skip[0].performClick()
        }
        // После знакомства флаг пишется в DataStore, и вкладки появляются
        // только следующим кадром
        awaitText("Сегодня")
    }

    private companion object {
        const val AWAIT_MS = 10_000L
    }
}
