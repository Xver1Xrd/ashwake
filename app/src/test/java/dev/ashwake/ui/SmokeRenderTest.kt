package dev.ashwake.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.ashwake.ui.components.ListGroup
import dev.ashwake.ui.components.ListRow
import dev.ashwake.ui.components.PrimaryButton
import dev.ashwake.ui.theme.AshwakeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Дымовой тест интерфейса.
 *
 * Эмулятора в сборочном окружении нет, а падение при старте приложения иначе
 * видно только на устройстве. Robolectric поднимает настоящую композицию на
 * JVM: если тема, шрифты или компоненты не собираются, тест падает со
 * стектрейсом, а не превращается в «приложение остановлено».
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SmokeRenderTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `тема со своими шрифтами и формами собирается`() {
        rule.setContent {
            AshwakeTheme {
                Column {
                    ListGroup(header = "сегодня") {
                        ListRow(title = "Зарядка", subtitle = "серия 3 дня")
                    }
                    PrimaryButton(text = "Готово") {}
                }
            }
        }

        rule.onNodeWithText("Зарядка").assertExists()
        rule.onNodeWithText("Готово").assertExists()
    }
}
