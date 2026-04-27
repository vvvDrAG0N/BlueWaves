package com.epubreader.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.epubreader.core.model.GlobalSettings
import com.epubreader.data.parser.EpubParser
import com.epubreader.data.settings.SettingsManager
import com.epubreader.feature.editbook.EditBookDependencies
import com.epubreader.feature.editbook.EditBookEvent
import com.epubreader.feature.editbook.EditBookRoute
import com.epubreader.feature.library.LibraryDependencies
import com.epubreader.feature.library.LibraryEvent
import com.epubreader.feature.library.LibraryRoute
import com.epubreader.feature.library.LibraryStartupPresentation
import com.epubreader.feature.reader.ReaderDependencies
import com.epubreader.feature.reader.ReaderEvent
import com.epubreader.feature.reader.ReaderRoute
import com.epubreader.feature.settings.SettingsDependencies
import com.epubreader.feature.settings.SettingsEvent
import com.epubreader.feature.settings.SettingsRoute
import kotlinx.coroutines.launch

@Composable
fun AppNavigation(settingsManager: SettingsManager, globalSettings: GlobalSettings) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val parser = remember { EpubParser.create(context) }

    var currentRoute by remember { mutableStateOf<AppRoute>(AppRoute.Library) }
    var startupState by remember { mutableStateOf(AppStartupState(phase = StartupPhase.EvaluatingStartup)) }
    var startupPresentation by remember { mutableStateOf(LibraryStartupPresentation()) }
    var detectedVersionCode by remember { mutableIntStateOf(0) }
    var pendingStartupDecision by remember { mutableStateOf<AppShellStartupDecision?>(null) }
    var hasCompletedInitialLibraryRefresh by remember { mutableStateOf(false) }
    val activeSurface = remember(currentRoute) { AppSurfaceRegistry.resolve(currentRoute) }

    fun commitPendingStartupDecision() {
        val decision = pendingStartupDecision ?: return
        startupPresentation = LibraryStartupPresentation(
            showFirstTimeNote = decision.showFirstTimeNote,
            changelogEntries = decision.changelogEntries,
        )
        pendingStartupDecision = null
    }

    fun goToLibrary() {
        currentRoute = AppRoute.Library
    }

    AppNavigationStartupEffect(
        context = context,
        globalSettings = globalSettings,
        settingsManager = settingsManager,
        onStartupEvaluated = { decision ->
            detectedVersionCode = decision.detectedVersionCode
            pendingStartupDecision = decision
            startupState = AppStartupState(
                phase = resolveStartupPhaseAfterEvaluation(
                    currentSurfaceId = activeSurface.surfaceId,
                    hasCompletedInitialLibraryRefresh = hasCompletedInitialLibraryRefresh,
                ),
            )
            if (activeSurface.surfaceId != com.epubreader.feature.library.LibrarySurfacePlugin.surfaceId) {
                commitPendingStartupDecision()
            }
        },
    )

    LaunchedEffect(startupState.phase, activeSurface.surfaceId, pendingStartupDecision) {
        if (startupState.phase == StartupPhase.Ready && pendingStartupDecision != null) {
            commitPendingStartupDecision()
        }
    }

    AppNavigationSideEffects(
        view = view,
        globalSettings = globalSettings,
        currentSurfaceId = activeSurface.surfaceId,
        chromeSpec = activeSurface.chromeSpec,
    )

    AppNavigationScreenHost(
        currentRoute = currentRoute,
        startupState = startupState,
        globalSettings = globalSettings,
        surfaceHost = AppSurfaceHost(
            libraryDependencies = LibraryDependencies(
                settingsManager = settingsManager,
                globalSettings = globalSettings,
                parser = parser,
                startupPresentation = startupPresentation,
            ),
            settingsDependencies = SettingsDependencies(
                settingsManager = settingsManager,
            ),
            readerDependencies = ReaderDependencies(
                parser = parser,
                settingsManager = settingsManager,
                globalSettings = globalSettings,
            ),
            editBookDependencies = EditBookDependencies(
                parser = parser,
                settingsManager = settingsManager,
            ),
            onLibraryEvent = { event ->
                when (event) {
                    LibraryEvent.InitialLibraryRefreshCompleted -> {
                        hasCompletedInitialLibraryRefresh = true
                        if (startupState.phase == StartupPhase.LoadingLibrary) {
                            startupState = AppStartupState(phase = StartupPhase.Ready)
                            commitPendingStartupDecision()
                        }
                    }

                    LibraryEvent.OpenSettings -> {
                        currentRoute = AppRoute.Settings
                    }

                    LibraryEvent.DismissWelcome -> {
                        scope.launch {
                            settingsManager.setFirstTime(false)
                            startupPresentation = startupPresentation.copy(showFirstTimeNote = false)
                        }
                    }

                    LibraryEvent.DismissChangelog -> {
                        scope.launch {
                            settingsManager.setLastSeenVersionCode(detectedVersionCode)
                            startupPresentation = startupPresentation.copy(changelogEntries = emptyList())
                        }
                    }

                    is LibraryEvent.OpenReader -> {
                        currentRoute = AppRoute.Reader(event.bookId)
                    }

                    is LibraryEvent.OpenEditBook -> {
                        currentRoute = AppRoute.EditBook(event.bookId)
                    }
                }
            },
            onSettingsEvent = { event ->
                when (event) {
                    SettingsEvent.Back -> goToLibrary()
                }
            },
            onReaderEvent = { event ->
                when (event) {
                    ReaderEvent.Back -> goToLibrary()
                }
            },
            onEditBookEvent = { event ->
                when (event) {
                    EditBookEvent.Back -> goToLibrary()
                    is EditBookEvent.Saved -> goToLibrary()
                }
            },
            onBackToLibrary = ::goToLibrary,
        ),
    )
}
