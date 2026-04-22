package com.epubreader.app

import com.epubreader.Screen
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationContractsTest {

    @Test
    fun shouldRefreshLibraryOnEntry_onlyWhenLibraryIsVisibleAndDataIsMissing() {
        assertTrue(shouldRefreshLibraryOnEntry(Screen.Library, hasBooks = false))
        assertFalse(shouldRefreshLibraryOnEntry(Screen.Library, hasBooks = true))
        assertFalse(shouldRefreshLibraryOnEntry(Screen.Settings, hasBooks = false))
    }
}
