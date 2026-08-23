package dev.ashwake.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dev.ashwake.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * У каждого нажимаемого элемента есть имя.
 *
 * Кнопка без подписи и без `contentDescription` для скринридера — это
 * «кнопка» и больше ничего. Человек, который пользуется приложением на слух,
 * слышит четыре одинаковых «кнопки» подряд и не знает, какая из них удаляет
 * задачу.
 *
 * Проверяется деревом семантики, а не глазами: значков в приложении под
 * сотню, и уследить за ними вручную нельзя — очередная безымянная иконка
 * появляется вместе с очередным экраном.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [33])
class AccessibilityTest {

    @get:Rule(order = 0)
    val reduceMotion = ReduceMotionRule()

    @get:Rule(order = 1)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 2)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun `на каждой вкладке все нажимаемые элементы названы`() {
        skipOnboardingIfShown()

        val unnamed = mutableListOf<String>()
        listOf("Сегодня", "Задачи", "Привычки", "Ещё").forEach { tab ->
            openTab(tab)
            unnamed += namelessClickables().map { "$tab: $it" }
        }

        assertTrue(
            "нажимаемые элементы без имени — скринридер прочитает их как " +
                "«кнопка» и ничего больше:\n" + unnamed.joinToString("\n"),
            unnamed.isEmpty()
        )
    }

    /**
     * Узлы, по которым можно нажать, но которые нечем назвать.
     *
     * Слитые узлы пропускаются: если подпись лежит внутри строки, а нажатие
     * висит на строке целиком, имя у элемента есть — просто не на нём самом.
     */
    private fun namelessClickables(): List<String> {
        val root = compose.onRoot().fetchSemanticsNode()
        val out = mutableListOf<String>()
        fun walk(node: SemanticsNode) {
            val clickable = node.config.getOrNull(SemanticsActions.OnClick) != null
            if (clickable && name(node) == null && node.children.none { name(it) != null }) {
                val role = node.config.getOrNull(SemanticsProperties.Role)
                // Соседний текст — единственная зацепка, по которой находят
                // безымянную иконку: id узла сам по себе ничего не говорит
                val nearby = generateSequence(node.parent) { it.parent }
                    .take(ANCESTORS_IN_REPORT)
                    .flatMap { parent -> parent.children.asSequence().mapNotNull(::name) }
                    .firstOrNull()
                out += "узел ${node.id} (${role ?: "без роли"}) в ${node.boundsInRoot}" +
                    (nearby?.let { ", рядом «$it»" } ?: ", рядом ничего подписанного")
            }
            node.children.forEach(::walk)
        }
        walk(root)
        return out
    }

    private fun name(node: SemanticsNode): String? {
        val text = node.config.getOrNull(SemanticsProperties.Text)
            ?.joinToString(" ") { it.text }
            ?.takeIf { it.isNotBlank() }
        val description = node.config.getOrNull(SemanticsProperties.ContentDescription)
            ?.joinToString(" ")
            ?.takeIf { it.isNotBlank() }
        return text ?: description
    }

    private fun openTab(title: String) {
        awaitText(title)
        val nodes = compose.onAllNodesWithText(title)
        nodes[nodes.fetchSemanticsNodes().lastIndex].performClick()
        compose.waitForIdle()
    }

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
        awaitText("Сегодня")
    }

    private companion object {
        const val AWAIT_MS = 10_000L

        /** На сколько уровней вверх искать подпись для отчёта. */
        const val ANCESTORS_IN_REPORT = 4
    }
}
