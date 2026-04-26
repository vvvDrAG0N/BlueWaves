package com.epubreader.app

import com.epubreader.core.ui.SurfaceId
import com.epubreader.feature.library.LibrarySurfacePlugin

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

internal fun resolveStartupPhaseAfterEvaluation(
    currentSurfaceId: SurfaceId,
    hasCompletedInitialLibraryRefresh: Boolean = false,
): StartupPhase {
    return if (currentSurfaceId == LibrarySurfacePlugin.surfaceId && !hasCompletedInitialLibraryRefresh) {
        StartupPhase.LoadingLibrary
    } else {
        StartupPhase.Ready
    }
}

internal fun shouldRunInitialLibraryRefresh(
    currentSurfaceId: SurfaceId,
    startupState: AppStartupState,
): Boolean {
    return currentSurfaceId == LibrarySurfacePlugin.surfaceId && startupState.phase == StartupPhase.LoadingLibrary
}
