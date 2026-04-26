package com.epubreader.feature.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.epubreader.core.debug.AppLog
import com.epubreader.core.ui.ShellChromeSpec
import com.epubreader.core.ui.SurfaceId
import com.epubreader.core.ui.SurfacePlugin
import com.epubreader.core.ui.SurfaceRouteDecodeResult
import com.epubreader.core.ui.getStaticWindowInsets
import com.epubreader.data.parser.EpubParser
import com.epubreader.data.settings.SettingsManager

data class ReaderRoute(
    val bookId: String,
)

data class ReaderRouteArgs(
    val bookId: String,
)

data class ReaderDependencies(
    override val parser: EpubParser,
    override val settingsManager: SettingsManager,
    override val engineExtensions: List<ReaderEngineExtension> = builtInReaderEngineExtensions,
    override val overlayExtensions: List<ReaderOverlayExtension> = emptyList(),
    override val toolExtensions: List<ReaderToolExtension> = emptyList(),
) : ReaderDependencyBag

interface ReaderDependencyBag {
    val parser: EpubParser
    val settingsManager: SettingsManager
    val engineExtensions: List<ReaderEngineExtension>
    val overlayExtensions: List<ReaderOverlayExtension>
    val toolExtensions: List<ReaderToolExtension>
}

sealed interface ReaderEvent {
    data object Back : ReaderEvent
}

object ReaderSurfacePlugin : SurfacePlugin<ReaderRoute, ReaderDependencies, ReaderEvent> {
    override val surfaceId: SurfaceId = SurfaceId("reader")
    override val chromeSpec: ShellChromeSpec = ShellChromeSpec(immersiveSystemBars = true)

    override fun decodeRouteArgs(routeArgs: Any?): SurfaceRouteDecodeResult<ReaderRoute> {
        val args = routeArgs as? ReaderRouteArgs
            ?: return SurfaceRouteDecodeResult.Failure("Reader route requires a book ID.")
        if (args.bookId.isBlank()) {
            return SurfaceRouteDecodeResult.Failure("Reader route requires a book ID.")
        }
        return SurfaceRouteDecodeResult.Success(ReaderRoute(bookId = args.bookId))
    }

    @Composable
    override fun Render(
        route: ReaderRoute,
        dependencies: ReaderDependencies,
        onEvent: (ReaderEvent) -> Unit,
    ) {
        val resolvedHostExtensions = remember(route.bookId, dependencies) {
            resolveReaderHostExtensions(
                route = route,
                dependencies = dependencies,
            )
        }
        var engineState by remember(route.bookId) { mutableStateOf<ReaderEngineState?>(null) }

        LaunchedEffect(route.bookId, resolvedHostExtensions.failures) {
            resolvedHostExtensions.failures.forEach { failure ->
                AppLog.w(AppLog.READER, failure.cause) {
                    "Reader extension failed at ${failure.pointId}: ${failure.extensionId}"
                }
            }
        }

        LaunchedEffect(route.bookId, resolvedHostExtensions.engineHost) {
            engineState = resolvedHostExtensions.engineHost?.load()
                ?: ReaderEngineState.Unavailable("No compatible reader engine is available.")
        }

        when (val state = engineState) {
            null -> ReaderPluginStatusScreen(
                title = "Reader",
                message = "Opening book...",
                showProgress = true,
                onBack = { onEvent(ReaderEvent.Back) },
            )

            is ReaderEngineState.Unavailable -> ReaderPluginStatusScreen(
                title = "Reader",
                message = state.message,
                showProgress = false,
                onBack = { onEvent(ReaderEvent.Back) },
            )

            is ReaderEngineState.Ready -> ReaderScreen(
                book = state.book,
                settingsManager = dependencies.settingsManager,
                parser = dependencies.parser,
                hostExtensions = resolvedHostExtensions,
                onBack = { onEvent(ReaderEvent.Back) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderPluginStatusScreen(
    title: String,
    message: String,
    showProgress: Boolean,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    Scaffold(
        contentWindowInsets = getStaticWindowInsets(),
        topBar = {
            TopAppBar(
                windowInsets = getStaticWindowInsets(),
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (showProgress) {
                    CircularProgressIndicator()
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                if (!showProgress) {
                    TextButton(onClick = onBack) {
                        Text("Back to Library")
                    }
                }
            }
        }
    }
}
