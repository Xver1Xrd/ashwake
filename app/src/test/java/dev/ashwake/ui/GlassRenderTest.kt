package dev.ashwake.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import dev.ashwake.ui.components.AshIcons
import dev.ashwake.ui.components.AshTabBar
import dev.ashwake.ui.components.LocalHazeState
import dev.ashwake.ui.components.TabItem
import dev.ashwake.ui.components.appHazeSource
import dev.ashwake.ui.components.rememberHazeState
import dev.ashwake.ui.theme.AshwakeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Интерфейс собирается: тема со своими шрифтами и формами, компоненты,
 * панель вкладок с размытием поверх прокручиваемого содержимого.
 *
 * Размытие — единственная часть интерфейса, которая тянет за собой внешнюю
 * библиотеку и работает с графическими слоями, то есть самая вероятная
 * причина падения при старте. Тест собирает ровно ту связку, что и
 * настоящий экран: источник размытия на контенте, панель поверх него.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GlassRenderTest {

    @get:Rule(order = 0)
    val reduceMotion = ReduceMotionRule()

    @get:Rule(order = 1)
    val rule = createComposeRule()

    private val tabs = listOf(
        TabItem("today", "Сегодня", AshIcons.Sun),
        TabItem("tasks", "Задачи", AshIcons.CheckCircle),
        TabItem("habits", "Привычки", AshIcons.Repeat),
        TabItem("more", "Ещё", AshIcons.DotsThree)
    )

    @Test
    fun `панель вкладок поверх размытого контента собирается`() {
        rule.setContent {
            AshwakeTheme {
                val haze = rememberHazeState()
                CompositionLocalProvider(LocalHazeState provides haze) {
                    Scaffold(
                        bottomBar = {
                            AshTabBar(tabs = tabs, selectedRoute = "today", onSelect = {})
                        }
                    ) { padding ->
                        // Отступы Scaffold здесь намеренно не применяются к
                        // содержимому: проверяется как раз то, что оно уезжает
                        // ПОД панель и просвечивает сквозь неё. Но параметр
                        // читается — иначе анализатор справедливо считает, что
                        // про отступы просто забыли
                        check(padding.calculateBottomPadding().value >= 0f)
                        Column(
                            Modifier
                                .fillMaxSize()
                                .appHazeSource()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Box(Modifier.height(1000.dp)) {
                                Text("содержимое под панелью")
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText("Сегодня").assertExists()
        rule.onNodeWithText("Задачи").assertExists()
        rule.onNodeWithText("содержимое под панелью").assertExists()
    }
}
