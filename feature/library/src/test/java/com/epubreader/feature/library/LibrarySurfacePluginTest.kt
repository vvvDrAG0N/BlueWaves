package com.epubreader.feature.library

import com.epubreader.core.ui.SurfaceId
import com.epubreader.core.ui.SurfaceRouteDecodeResult
import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySurfacePluginTest {

    @Test
    fun surfaceId_matchesLibraryRoute() {
        assertEquals(SurfaceId("library"), LibrarySurfacePlugin.surfaceId)
    }

    @Test
    fun decodeRouteArgs_acceptsNoArgs() {
        val result = LibrarySurfacePlugin.decodeRouteArgs(routeArgs = null)

        assertEquals(SurfaceRouteDecodeResult.Success(LibraryRoute), result)
    }

    @Test
    fun decodeRouteArgs_rejectsUnexpectedArgs() {
        val result = LibrarySurfacePlugin.decodeRouteArgs(routeArgs = Any())

        assertEquals(
            SurfaceRouteDecodeResult.Failure("Library does not accept route arguments."),
            result,
        )
    }
}
