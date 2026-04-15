package com.epubreader.app

import com.epubreader.Screen
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationBackHandlingTest {

    @Test
    fun shouldUseShellBackHandler_disablesShellBackOnEditBookScreen() {
        val result = shouldUseShellBackHandler(
            currentScreen = Screen.EditBook,
            isDrawerOpen = false,
            isFolderSelectionMode = false,
            isBookSelectionMode = false,
        )

        assertFalse(result)
    }

    @Test
    fun shouldUseShellBackHandler_disablesShellBackOnReaderScreen() {
        val result = shouldUseShellBackHandler(
            currentScreen = Screen.Reader,
            isDrawerOpen = false,
            isFolderSelectionMode = false,
            isBookSelectionMode = false,
        )

        assertFalse(result)
    }

    @Test
    fun shouldUseShellBackHandler_keepsShellBackForLibraryTransientStates() {
        assertTrue(
            shouldUseShellBackHandler(
                currentScreen = Screen.Library,
                isDrawerOpen = true,
                isFolderSelectionMode = false,
                isBookSelectionMode = false,
            ),
        )
        assertTrue(
            shouldUseShellBackHandler(
                currentScreen = Screen.Library,
                isDrawerOpen = false,
                isFolderSelectionMode = true,
                isBookSelectionMode = false,
            ),
        )
        assertTrue(
            shouldUseShellBackHandler(
                currentScreen = Screen.Library,
                isDrawerOpen = false,
                isFolderSelectionMode = false,
                isBookSelectionMode = true,
            ),
        )
    }
}
