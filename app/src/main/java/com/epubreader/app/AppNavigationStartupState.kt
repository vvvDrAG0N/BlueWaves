package com.epubreader.app

import com.epubreader.Screen

internal enum class StartupPhase {
    WaitingForSettings,
    EvaluatingStartup,
    LoadingLibrary,
    Ready,
}

internal data class AppStartupState(
    val phase: StartupPhase,
) {
    val isWarmUpVisible: Boolean
        get() = phase != StartupPhase.Ready
}

internal fun resolveStartupPhaseAfterEvaluation(currentScreen: Screen): StartupPhase {
    return if (currentScreen == Screen.Library) {
        StartupPhase.LoadingLibrary
    } else {
        StartupPhase.Ready
    }
}

internal fun shouldRunInitialLibraryRefresh(
    currentScreen: Screen,
    startupState: AppStartupState,
): Boolean {
    return currentScreen == Screen.Library && startupState.phase == StartupPhase.LoadingLibrary
}
