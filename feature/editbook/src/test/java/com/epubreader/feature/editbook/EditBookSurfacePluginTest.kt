package com.epubreader.feature.editbook

import com.epubreader.core.ui.SurfaceId
import com.epubreader.core.ui.SurfaceRouteDecodeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditBookSurfacePluginTest {

    @Test
    fun surfaceId_matchesEditBookRoute() {
        assertEquals(SurfaceId("edit_book"), EditBookSurfacePlugin.surfaceId)
    }

    @Test
    fun decodeRouteArgs_acceptsBookIdArgs() {
        val result = EditBookSurfacePlugin.decodeRouteArgs(EditBookRouteArgs(bookId = "book-123"))

        assertEquals(
            SurfaceRouteDecodeResult.Success(EditBookRoute(bookId = "book-123")),
            result,
        )
    }

    @Test
    fun decodeRouteArgs_rejectsMissingArgs() {
        val result = EditBookSurfacePlugin.decodeRouteArgs(routeArgs = null)

        assertEquals(
            SurfaceRouteDecodeResult.Failure("Edit Book route requires a book ID."),
            result,
        )
    }

    @Test
    fun decodeRouteArgs_rejectsBlankBookId() {
        val result = EditBookSurfacePlugin.decodeRouteArgs(EditBookRouteArgs(bookId = "   "))

        assertTrue(result is SurfaceRouteDecodeResult.Failure)
        assertEquals(
            "Edit Book route requires a book ID.",
            (result as SurfaceRouteDecodeResult.Failure).message,
        )
    }
}
