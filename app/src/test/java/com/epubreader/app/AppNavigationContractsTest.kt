package com.epubreader.app

import com.epubreader.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationContractsTest {

    @Test
    fun resolveStartupPhaseAfterEvaluation_routesColdLaunchThroughLibraryWarmUp() {
        assertEquals(
            StartupPhase.LoadingLibrary,
            resolveStartupPhaseAfterEvaluation(
                currentScreen = Screen.Library,
                hasCompletedInitialLibraryRefresh = false,
            ),
        )
        assertEquals(
            StartupPhase.Ready,
            resolveStartupPhaseAfterEvaluation(
                currentScreen = Screen.Library,
                hasCompletedInitialLibraryRefresh = true,
            ),
        )
        assertEquals(
            StartupPhase.Ready,
            resolveStartupPhaseAfterEvaluation(
                currentScreen = Screen.Settings,
                hasCompletedInitialLibraryRefresh = false,
            ),
        )
    }

    @Test
    fun shouldRunInitialLibraryRefresh_onlyWhileStartupOwnsTheColdLaunchLoad() {
        assertTrue(shouldRunInitialLibraryRefresh(Screen.Library, AppStartupState(StartupPhase.LoadingLibrary)))
        assertFalse(shouldRunInitialLibraryRefresh(Screen.Library, AppStartupState(StartupPhase.EvaluatingStartup)))
        assertFalse(shouldRunInitialLibraryRefresh(Screen.Library, AppStartupState(StartupPhase.Ready)))
        assertFalse(shouldRunInitialLibraryRefresh(Screen.Settings, AppStartupState(StartupPhase.LoadingLibrary)))
    }

    @Test
    fun appStartupState_hidesWarmUpOnlyAfterReady() {
        assertTrue(AppStartupState(StartupPhase.WaitingForSettings).isWarmUpVisible)
        assertTrue(AppStartupState(StartupPhase.LoadingLibrary).isWarmUpVisible)
        assertFalse(AppStartupState(StartupPhase.Ready).isWarmUpVisible)
    }
}
