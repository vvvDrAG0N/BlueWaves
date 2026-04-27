package com.epubreader.feature.reader

import androidx.compose.runtime.Composable
import com.epubreader.core.model.BookFormat
import com.epubreader.core.model.EpubBook
import com.epubreader.core.model.GlobalSettings
import com.epubreader.data.parser.EpubParser
import com.epubreader.data.settings.SettingsManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderExtensionHostTest {

    @Test
    fun resolveReaderHostExtensions_usesFirstResolvedEngineInDeterministicListOrder() {
        val resolution = resolveReaderHostExtensions(
            route = ReaderRoute(bookId = "book-1"),
            dependencies = unusedDependencies,
            engineExtensions = listOf(
                FakeEngineExtension("first") { FakeEngineHost("first") },
                FakeEngineExtension("second") { FakeEngineHost("second") },
            ),
        )

        val chosenHost = resolution.engineHost as FakeEngineHost

        assertEquals("first", chosenHost.label)
    }

    @Test
    fun resolveReaderHostExtensions_containsOverlayAndToolHostsWhileContainingFailures() {
        val resolution = resolveReaderHostExtensions(
            route = ReaderRoute(bookId = "book-1"),
            dependencies = unusedDependencies,
            engineExtensions = listOf(
                FakeEngineExtension("broken-engine") { error("engine boom") },
                FakeEngineExtension("fallback-engine") { FakeEngineHost("fallback") },
            ),
            overlayExtensions = listOf(
                FakeOverlayExtension("overlay-a"),
                FakeOverlayExtension("broken-overlay") { error("overlay boom") },
            ),
            toolExtensions = listOf(
                FakeToolExtension("tool-a"),
                FakeToolExtension("disabled-tool", enabled = false),
            ),
        )

        val chosenHost = resolution.engineHost as FakeEngineHost

        assertEquals("fallback", chosenHost.label)
        assertEquals(listOf("overlay-a"), resolution.overlayHosts.map { it.extensionId })
        assertEquals(listOf("tool-a"), resolution.toolHosts.map { it.extensionId })
        assertEquals(
            listOf("broken-engine", "broken-overlay", "disabled-tool"),
            resolution.disabledExtensionIds,
        )
        assertEquals(
            listOf("broken-engine", "broken-overlay"),
            resolution.failures.map { it.extensionId },
        )
    }

    @Test
    fun loadBuiltInReaderEngineState_returnsUnavailableWhenBookIsMissing() = runBlocking {
        val state = loadBuiltInReaderEngineState(
            route = ReaderRoute(bookId = "missing"),
            loadBookById = { null },
            prepareBookForReading = { error("should not prepare missing book") },
            updateLastRead = { error("should not update missing book") },
        )

        assertEquals(
            ReaderEngineState.Unavailable("This book is no longer available."),
            state,
        )
    }

    @Test
    fun loadBuiltInReaderEngineState_returnsUnavailableWhenPdfReadingIsDisabled() = runBlocking {
        val state = loadBuiltInReaderEngineState(
            route = ReaderRoute(bookId = "pdf-book"),
            loadBookById = {
                sampleBook(
                    id = "pdf-book",
                    format = BookFormat.PDF,
                    sourceFormat = BookFormat.PDF,
                )
            },
            prepareBookForReading = { error("should not prepare PDF-disabled book") },
            updateLastRead = { error("should not update PDF-disabled book") },
        )

        assertEquals(
            ReaderEngineState.Unavailable("PDF reading remains disabled in the active shell."),
            state,
        )
    }

    @Test
    fun loadBuiltInReaderEngineState_preparesAndTouchesLastReadForEpubBooks() = runBlocking {
        var updatedBook: EpubBook? = null

        val state = loadBuiltInReaderEngineState(
            route = ReaderRoute(bookId = "epub-book"),
            loadBookById = { sampleBook(id = "epub-book") },
            prepareBookForReading = { book -> book.copy(title = "Prepared ${book.title}") },
            updateLastRead = { book -> updatedBook = book },
        )

        assertTrue(state is ReaderEngineState.Ready)
        val readyBook = (state as ReaderEngineState.Ready).book
        assertEquals("Prepared Title", readyBook.title)
        assertEquals(readyBook, updatedBook)
        assertTrue(readyBook.lastRead > 0L)
    }

    private fun sampleBook(
        id: String,
        format: BookFormat = BookFormat.EPUB,
        sourceFormat: BookFormat = format,
    ): EpubBook {
        return EpubBook(
            id = id,
            title = "Title",
            author = "Author",
            coverPath = null,
            rootPath = "/books/$id",
            format = format,
            sourceFormat = sourceFormat,
            spineHrefs = listOf("chapter-1.xhtml"),
        )
    }

    private val unusedDependencies = object : ReaderDependencyBag {
        override val parser: EpubParser
            get() = error("unused in resolver tests")
        override val settingsManager: SettingsManager
            get() = error("unused in resolver tests")
        override val globalSettings: GlobalSettings = GlobalSettings()
        override val engineExtensions: List<ReaderEngineExtension> = emptyList()
        override val overlayExtensions: List<ReaderOverlayExtension> = emptyList()
        override val toolExtensions: List<ReaderToolExtension> = emptyList()
    }

    private class FakeEngineExtension(
        override val extensionId: String,
        override val enabled: Boolean = true,
        private val factory: () -> ReaderEngineHost?,
    ) : ReaderEngineExtension {
        override fun createHost(
            route: ReaderRoute,
            dependencies: ReaderDependencyBag,
        ): ReaderEngineHost? = factory()
    }

    private data class FakeEngineHost(
        val label: String,
    ) : ReaderEngineHost {
        override suspend fun load(): ReaderEngineState {
            error("load should not run in pure resolution tests")
        }
    }

    private class FakeOverlayExtension(
        override val extensionId: String,
        override val enabled: Boolean = true,
        private val factory: (String) -> ReaderOverlayHost? = { resolvedExtensionId ->
            object : ReaderOverlayHost {
                override val extensionId: String = resolvedExtensionId

                @Composable
                override fun androidx.compose.foundation.layout.BoxScope.Render(
                    context: ReaderOverlayRenderContext,
                ) = Unit
            }
        },
    ) : ReaderOverlayExtension {
        override fun createHost(
            route: ReaderRoute,
            dependencies: ReaderDependencyBag,
        ): ReaderOverlayHost? = factory(extensionId)
    }

    private class FakeToolExtension(
        override val extensionId: String,
        override val enabled: Boolean = true,
    ) : ReaderToolExtension {
        override fun createHost(
            route: ReaderRoute,
            dependencies: ReaderDependencyBag,
        ): ReaderToolHost {
            return object : ReaderToolHost {
                override val extensionId: String = this@FakeToolExtension.extensionId

                @Composable
                override fun Render(context: ReaderToolRenderContext) = Unit
            }
        }
    }
}
