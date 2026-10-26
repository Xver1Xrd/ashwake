package dev.ashwake.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.ashwake.ui.theme.AshwakeTheme
import dev.ashwake.ui.components.AshIcons
import dev.ashwake.ui.components.AshTabBar
import dev.ashwake.ui.components.TabItem
import dev.ashwake.ui.today.DateStrip
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w320dp-h480dp")
class AdaptiveLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `selected date centers in a narrow viewport with large system text`() {
        val selected = LocalDate.of(2026, 10, 15)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                AshwakeTheme {
                    Box(Modifier.width(280.dp).testTag("viewport")) {
                        DateStrip(today = selected, selectedDate = selected, onSelectDate = {})
                    }
                }
            }
        }
        compose.waitForIdle()

        val viewport = compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
        val selectedDay = compose.onNodeWithText("15").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "The selected day should stay centered after screen or font scaling",
            kotlin.math.abs(selectedDay.center.x - viewport.center.x) < viewport.width * 0.08f
        )
    }

    @Test fun `island selects the second tab at its visual position on a narrow screen`() {
        val tabs = listOf(
            TabItem("today", "Сегодня", AshIcons.Sun),
            TabItem("tasks", "Задачи", AshIcons.CheckCircle),
            TabItem("habits", "Привычки", AshIcons.Repeat),
            TabItem("more", "Ещё", AshIcons.DotsThree)
        )
        var selected = ""
        compose.setContent {
            AshwakeTheme {
                AshTabBar(tabs, "today", Modifier.width(280.dp).testTag("island")) {
                    selected = it.route
                }
            }
        }
        val island = compose.onNodeWithTag("island")
        val bounds = island.fetchSemanticsNode().boundsInRoot
        island.performTouchInput {
            click(Offset(bounds.width * 0.30f, bounds.height / 2f))
        }
        assertTrue("Touch position should match the visible second segment", selected == "tasks")
    }
}
