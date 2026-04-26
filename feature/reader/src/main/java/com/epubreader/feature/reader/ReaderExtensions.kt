package com.epubreader.feature.reader

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import com.epubreader.core.debug.AppLog
import com.epubreader.core.model.BookFormat
import com.epubreader.core.model.EpubBook
import com.epubreader.core.model.GlobalSettings
import com.epubreader.core.ui.ExtensionFailure
import com.epubreader.core.ui.ExtensionPoint
import com.epubreader.core.ui.RenderSurfaceBoxExtension
import com.epubreader.core.ui.RenderSurfaceExtension
import com.epubreader.core.ui.SurfaceExtension
import com.epubreader.core.ui.resolveExtensionPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ReaderEngineExtension : SurfaceExtension {
    fun createHost(
        route: ReaderRoute,
        dependencies: ReaderDependencyBag,
    ): ReaderEngineHost?
}

interface ReaderEngineHost {
    suspend fun load(): ReaderEngineState
}

sealed interface ReaderEngineState {
    data class Ready(val book: EpubBook) : ReaderEngineState
    data class Unavailable(val message: String) : ReaderEngineState
}

interface ReaderOverlayExtension : SurfaceExtension {
    fun createHost(
        route: ReaderRoute,
        dependencies: ReaderDependencyBag,
    ): ReaderOverlayHost?
}

interface ReaderOverlayHost {
    val extensionId: String

    @Composable
    fun BoxScope.Render(context: ReaderOverlayRenderContext)
}

data class ReaderOverlayRenderContext(
    val book: EpubBook,
    val settings: GlobalSettings,
    val themeColors: ReaderTheme,
    val showControls: Boolean,
    val currentChapterIndex: Int,
    val progressPercentageState: State<Float>,
)

interface ReaderToolExtension : SurfaceExtension {
    fun createHost(
        route: ReaderRoute,
        dependencies: ReaderDependencyBag,
    ): ReaderToolHost?
}

interface ReaderToolHost {
    val extensionId: String

    @Composable
    fun Render(context: ReaderToolRenderContext)
}

data class ReaderToolRenderContext(
    val book: EpubBook,
    val settings: GlobalSettings,
    val themeColors: ReaderTheme,
    val currentChapterIndex: Int,
)

data class ReaderResolvedHostExtensions(
    val engineHost: ReaderEngineHost? = null,
    val overlayHosts: List<ReaderOverlayHost> = emptyList(),
    val toolHosts: List<ReaderToolHost> = emptyList(),
    val disabledExtensionIds: List<String> = emptyList(),
    val failures: List<ExtensionFailure> = emptyList(),
)

private val ReaderEngineExtensionPoint = ExtensionPoint<ReaderEngineExtension>("reader.engine")
private val ReaderOverlayExtensionPoint = ExtensionPoint<ReaderOverlayExtension>("reader.overlay")
private val ReaderToolExtensionPoint = ExtensionPoint<ReaderToolExtension>("reader.tool")

internal fun resolveReaderHostExtensions(
    route: ReaderRoute,
    dependencies: ReaderDependencyBag,
    engineExtensions: List<ReaderEngineExtension> = dependencies.engineExtensions,
    overlayExtensions: List<ReaderOverlayExtension> = dependencies.overlayExtensions,
    toolExtensions: List<ReaderToolExtension> = dependencies.toolExtensions,
): ReaderResolvedHostExtensions {
    val engineResolution = resolveExtensionPoint(
        point = ReaderEngineExtensionPoint,
        extensions = engineExtensions,
    ) { extension ->
        extension.createHost(route, dependencies)
    }
    val overlayResolution = resolveExtensionPoint(
        point = ReaderOverlayExtensionPoint,
        extensions = overlayExtensions,
    ) { extension ->
        extension.createHost(route, dependencies)
    }
    val toolResolution = resolveExtensionPoint(
        point = ReaderToolExtensionPoint,
        extensions = toolExtensions,
    ) { extension ->
        extension.createHost(route, dependencies)
    }

    return ReaderResolvedHostExtensions(
        engineHost = engineResolution.values.firstOrNull(),
        overlayHosts = overlayResolution.values.map(::wrapReaderOverlayHost),
        toolHosts = toolResolution.values.map(::wrapReaderToolHost),
        disabledExtensionIds = buildList {
            addAll(engineResolution.disabledExtensionIds)
            addAll(overlayResolution.disabledExtensionIds)
            addAll(toolResolution.disabledExtensionIds)
        },
        failures = buildList {
            addAll(engineResolution.failures)
            addAll(overlayResolution.failures)
            addAll(toolResolution.failures)
        },
    )
}

private fun wrapReaderOverlayHost(host: ReaderOverlayHost): ReaderOverlayHost {
    return object : ReaderOverlayHost {
        override val extensionId: String = host.extensionId

        @Composable
        override fun BoxScope.Render(context: ReaderOverlayRenderContext) {
            RenderSurfaceBoxExtension(
                pointId = ReaderOverlayExtensionPoint.id,
                extensionId = extensionId,
                onFailure = ::logReaderExtensionFailure,
            ) {
                with(host) {
                    Render(context)
                }
            }
        }
    }
}

private fun wrapReaderToolHost(host: ReaderToolHost): ReaderToolHost {
    return object : ReaderToolHost {
        override val extensionId: String = host.extensionId

        @Composable
        override fun Render(context: ReaderToolRenderContext) {
            RenderSurfaceExtension(
                pointId = ReaderToolExtensionPoint.id,
                extensionId = extensionId,
                onFailure = ::logReaderExtensionFailure,
            ) {
                host.Render(context)
            }
        }
    }
}

private fun logReaderExtensionFailure(failure: ExtensionFailure) {
    AppLog.w(AppLog.READER, failure.cause) {
        "Reader extension failed at ${failure.pointId}: ${failure.extensionId}"
    }
}

internal val builtInReaderEngineExtensions: List<ReaderEngineExtension> =
    listOf(BuiltInReaderEngineExtension)

private object BuiltInReaderEngineExtension : ReaderEngineExtension {
    override val extensionId: String = "reader.engine.builtin.epub"

    override fun createHost(
        route: ReaderRoute,
        dependencies: ReaderDependencyBag,
    ): ReaderEngineHost {
        return object : ReaderEngineHost {
            override suspend fun load(): ReaderEngineState {
                return loadBuiltInReaderEngineState(
                    route = route,
                    loadBookById = { bookId ->
                        withContext(Dispatchers.IO) {
                            dependencies.parser.loadBookById(bookId)
                        }
                    },
                    prepareBookForReading = { book ->
                        withContext(Dispatchers.IO) {
                            dependencies.parser.prepareBookForReading(book)
                        }
                    },
                    updateLastRead = { book ->
                        withContext(Dispatchers.IO) {
                            dependencies.parser.updateLastRead(book)
                        }
                    },
                )
            }
        }
    }
}

internal suspend fun loadBuiltInReaderEngineState(
    route: ReaderRoute,
    loadBookById: suspend (String) -> EpubBook?,
    prepareBookForReading: suspend (EpubBook) -> EpubBook,
    updateLastRead: suspend (EpubBook) -> Unit,
    currentTimeMillis: () -> Long = { System.currentTimeMillis() },
): ReaderEngineState {
    val loadedBook = loadBookById(route.bookId)
        ?: return ReaderEngineState.Unavailable("This book is no longer available.")

    if (loadedBook.sourceFormat == BookFormat.PDF) {
        return ReaderEngineState.Unavailable("PDF reading remains disabled in the active shell.")
    }

    val updatedBook = prepareBookForReading(loadedBook).copy(lastRead = currentTimeMillis())
    updateLastRead(updatedBook)
    return ReaderEngineState.Ready(updatedBook)
}
