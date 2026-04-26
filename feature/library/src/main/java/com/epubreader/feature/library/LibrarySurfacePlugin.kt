package com.epubreader.feature.library

import androidx.compose.runtime.Composable
import com.epubreader.core.model.GlobalSettings
import com.epubreader.core.ui.SurfaceId
import com.epubreader.core.ui.SurfacePlugin
import com.epubreader.core.ui.SurfaceRouteDecodeResult
import com.epubreader.data.parser.EpubParser
import com.epubreader.data.settings.SettingsManager
import com.epubreader.feature.library.internal.LibraryFeatureContent
import org.json.JSONObject

data object LibraryRoute

data class LibraryStartupPresentation(
    val showFirstTimeNote: Boolean = false,
    val changelogEntries: List<JSONObject> = emptyList(),
)

data class LibraryDependencies(
    val settingsManager: SettingsManager,
    val globalSettings: GlobalSettings,
    val parser: EpubParser,
    val startupPresentation: LibraryStartupPresentation = LibraryStartupPresentation(),
    val importExtensions: List<LibraryImportExtension> = emptyList(),
    val actionExtensions: List<LibraryActionExtension> = emptyList(),
    val decorationExtensions: List<LibraryDecorationExtension> = emptyList(),
)

sealed interface LibraryEvent {
    data object InitialLibraryRefreshCompleted : LibraryEvent
    data object OpenSettings : LibraryEvent
    data object DismissWelcome : LibraryEvent
    data object DismissChangelog : LibraryEvent
    data class OpenReader(val bookId: String) : LibraryEvent
    data class OpenEditBook(val bookId: String) : LibraryEvent
}

object LibrarySurfacePlugin : SurfacePlugin<LibraryRoute, LibraryDependencies, LibraryEvent> {
    override val surfaceId: SurfaceId = SurfaceId("library")

    override fun decodeRouteArgs(routeArgs: Any?): SurfaceRouteDecodeResult<LibraryRoute> {
        return if (routeArgs == null) {
            SurfaceRouteDecodeResult.Success(LibraryRoute)
        } else {
            SurfaceRouteDecodeResult.Failure("Library does not accept route arguments.")
        }
    }

    @Composable
    override fun Render(
        route: LibraryRoute,
        dependencies: LibraryDependencies,
        onEvent: (LibraryEvent) -> Unit,
    ) {
        LibraryFeatureContent(
            route = route,
            dependencies = dependencies,
            onEvent = onEvent,
        )
    }
}
