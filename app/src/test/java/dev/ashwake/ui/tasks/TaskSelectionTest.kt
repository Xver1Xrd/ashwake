package dev.ashwake.ui.tasks

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskSelectionTest {
    @Test
    fun `select all selects visible tasks even when counts match but identities differ`() {
        assertEquals(setOf(1L, 2L, 3L, 4L), toggleVisibleSelection(setOf(1L, 2L), setOf(3L, 4L)))
    }

    @Test
    fun `select all clears visible selection without touching other ids`() {
        assertEquals(setOf(9L), toggleVisibleSelection(setOf(1L, 2L, 9L), setOf(1L, 2L)))
    }

    @Test
    fun `empty visible list leaves selection unchanged`() {
        assertEquals(setOf(1L), toggleVisibleSelection(setOf(1L), emptySet()))
    }
}
