package com.netzone.app

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeChangeCoordinatorTest {

    @Test
    fun requestThemeChange_is_noop_when_target_matches_current_theme() = runTest {
        val events = mutableListOf<String>()
        val coordinator = ThemeChangeCoordinator(
            prepareTransition = { events += "prepare:$it" },
            attachTransition = { events += "attach" },
            persistTheme = { events += "persist:$it" }
        )

        val shouldAttach = coordinator.requestThemeChange(
            currentIsDark = true,
            targetIsDark = true
        )

        assertFalse(shouldAttach)
        assertTrue(events.isEmpty())
    }

    @Test
    fun requestThemeChange_prepares_target_theme_before_persisting_it() = runTest {
        val events = mutableListOf<String>()
        val coordinator = ThemeChangeCoordinator(
            prepareTransition = { events += "prepare:$it" },
            attachTransition = { events += "attach" },
            persistTheme = { events += "persist:$it" }
        )

        val shouldAttach = coordinator.requestThemeChange(
            currentIsDark = false,
            targetIsDark = true
        )

        assertTrue(shouldAttach)
        assertEquals(listOf("prepare:true", "attach", "persist:true"), events)
    }
}
