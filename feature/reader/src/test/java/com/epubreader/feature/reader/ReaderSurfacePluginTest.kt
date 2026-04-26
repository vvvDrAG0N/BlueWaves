package com.epubreader.feature.reader

import com.epubreader.core.ui.SurfaceId
import com.epubreader.core.ui.SurfaceRouteDecodeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSurfacePluginTest {

    @Test
    fun surfaceId_matchesReaderRoute() {
        assertEquals(SurfaceId("reader"), ReaderSurfacePlugin.surfaceId)
    }

    @Test
    fun chromeSpec_usesImmersiveSystemBars() {
        assertTrue(ReaderSurfacePlugin.chromeSpec.immersiveSystemBars)
    }

    @Test
    fun decodeRouteArgs_acceptsBookIdArgs() {
        val result = ReaderSurfacePlugin.decodeRouteArgs(ReaderRouteArgs(bookId = "book-123"))

        assertEquals(
            SurfaceRouteDecodeResult.Success(ReaderRoute(bookId = "book-123")),
            result,
        )
    }

    @Test
    fun decodeRouteArgs_rejectsMissingArgs() {
        val result = ReaderSurfacePlugin.decodeRouteArgs(routeArgs = null)

        assertEquals(
            SurfaceRouteDecodeResult.Failure("Reader route requires a book ID."),
            result,
        )
    }

    @Test
    fun decodeRouteArgs_rejectsBlankBookId() {
        val result = ReaderSurfacePlugin.decodeRouteArgs(ReaderRouteArgs(bookId = "   "))

        assertTrue(result is SurfaceRouteDecodeResult.Failure)
        assertEquals(
            "Reader route requires a book ID.",
            (result as SurfaceRouteDecodeResult.Failure).message,
        )
    }
}
