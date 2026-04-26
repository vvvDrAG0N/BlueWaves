package com.epubreader.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.epubreader.core.ui.ShellChromeSpec
import com.epubreader.core.ui.SurfaceId
import com.epubreader.core.ui.SurfacePlugin
import com.epubreader.core.ui.SurfaceRouteDecodeResult
import com.epubreader.core.ui.SurfaceRouteDefinition
import com.epubreader.core.ui.getStaticWindowInsets
import com.epubreader.feature.editbook.EditBookDependencies
import com.epubreader.feature.editbook.EditBookEvent
import com.epubreader.feature.editbook.EditBookSurfacePlugin
import com.epubreader.feature.library.LibraryDependencies
import com.epubreader.feature.library.LibraryEvent
import com.epubreader.feature.library.LibrarySurfacePlugin
import com.epubreader.feature.reader.ReaderDependencies
import com.epubreader.feature.reader.ReaderEvent
import com.epubreader.feature.reader.ReaderSurfacePlugin
import com.epubreader.feature.settings.SettingsDependencies
import com.epubreader.feature.settings.SettingsEvent
import com.epubreader.feature.settings.SettingsSurfacePlugin

internal object AppSurfaceRegistry {
    private val fallbackSurfaceId = SurfaceId("app.unavailable")
    private val fallbackChromeSpec = ShellChromeSpec()

    private val registry = SurfaceRegistry(
        listOf(
            boundSurface(
                plugin = LibrarySurfacePlugin,
                dependencies = { libraryDependencies },
                eventHandler = { onLibraryEvent },
            ),
            boundSurface(
                plugin = SettingsSurfacePlugin,
                dependencies = { settingsDependencies },
                eventHandler = { onSettingsEvent },
            ),
            boundSurface(
                plugin = ReaderSurfacePlugin,
                dependencies = { readerDependencies },
                eventHandler = { onReaderEvent },
            ),
            boundSurface(
                plugin = EditBookSurfacePlugin,
                dependencies = { editBookDependencies },
                eventHandler = { onEditBookEvent },
            ),
        ),
    )

    fun resolve(route: AppRoute): AppResolvedSurface {
        val surface = registry.find(route.surfaceId)
        return if (surface == null) {
            fallbackSurface("The ${route.surfaceId.value} surface is not registered in this build.")
        } else {
            surface.resolve(route)
        }
    }

    private fun <Args, Dependencies, Event> boundSurface(
        plugin: SurfacePlugin<Args, Dependencies, Event>,
        dependencies: AppSurfaceHost.() -> Dependencies,
        eventHandler: AppSurfaceHost.() -> ((Event) -> Unit),
    ): AppSurfaceEntry {
        return BoundSurface(
            plugin = plugin,
            dependencies = dependencies,
            eventHandler = eventHandler,
        )
    }

    private interface AppSurfaceEntry : SurfaceRouteDefinition {
        fun resolve(route: AppRoute): AppResolvedSurface
    }

    private class BoundSurface<Args, Dependencies, Event>(
        private val plugin: SurfacePlugin<Args, Dependencies, Event>,
        private val dependencies: AppSurfaceHost.() -> Dependencies,
        private val eventHandler: AppSurfaceHost.() -> ((Event) -> Unit),
    ) : AppSurfaceEntry {
        override val surfaceId = plugin.surfaceId
        override val chromeSpec = plugin.chromeSpec

        override fun resolve(route: AppRoute): AppResolvedSurface {
            when (val decoded = plugin.decodeRouteArgs(route.routeArgs)) {
                is SurfaceRouteDecodeResult.Success -> return AppResolvedSurface(
                    surfaceId = plugin.surfaceId,
                    chromeSpec = plugin.chromeSpec,
                ) { host ->
                    plugin.Render(
                        route = decoded.args,
                        dependencies = host.dependencies(),
                        onEvent = host.eventHandler(),
                    )
                }

                is SurfaceRouteDecodeResult.Failure -> return fallbackSurface(decoded.message)
            }
        }
    }

    private fun fallbackSurface(message: String): AppResolvedSurface {
        return AppResolvedSurface(
            surfaceId = fallbackSurfaceId,
            chromeSpec = fallbackChromeSpec,
        ) { host ->
            UnknownSurfaceScreen(
                title = "Unavailable Surface",
                message = message,
                onBackToLibrary = host.onBackToLibrary,
            )
        }
    }
}

internal class AppResolvedSurface(
    val surfaceId: SurfaceId,
    val chromeSpec: ShellChromeSpec,
    private val render: @Composable (AppSurfaceHost) -> Unit,
) {
    @Composable
    fun Render(host: AppSurfaceHost) {
        render(host)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnknownSurfaceScreen(
    title: String,
    message: String,
    onBackToLibrary: () -> Unit,
) {
    BackHandler(onBack = onBackToLibrary)

    Scaffold(
        contentWindowInsets = getStaticWindowInsets(),
        topBar = {
            TopAppBar(
                windowInsets = getStaticWindowInsets(),
                title = { Text(title) },
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
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = onBackToLibrary) {
                    Text("Back to Library")
                }
            }
        }
    }
}
