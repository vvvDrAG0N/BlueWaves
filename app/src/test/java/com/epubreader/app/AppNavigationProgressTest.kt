package com.epubreader.app

import com.epubreader.Screen
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationProgressTest {

    @Test
    fun shouldObserveLibraryProgress_onlyWhileLibraryIsVisible() {
        assertTrue(shouldObserveLibraryProgress(Screen.Library))
        assertFalse(shouldObserveLibraryProgress(Screen.Reader))
        assertFalse(shouldObserveLibraryProgress(Screen.Settings))
        assertFalse(shouldObserveLibraryProgress(Screen.EditBook))
    }
}
