package com.epubreader.app

import com.epubreader.core.ui.SurfaceId
import com.epubreader.feature.reader.ReaderRouteArgs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppRouteTest {

    @Test
    fun appRoute_carriesSurfaceIdentityAndInMemoryArgs() {
        val route = AppRoute(
            surfaceId = SurfaceId("reader"),
            routeArgs = ReaderRouteArgs(bookId = "book-123"),
        )

        assertEquals(SurfaceId("reader"), route.surfaceId)
        assertEquals("book-123", (route.routeArgs as ReaderRouteArgs).bookId)
    }

    @Test
    fun appRoute_allowsArgumentFreeSurfaces() {
        val route = AppRoute(surfaceId = SurfaceId("library"))

        assertEquals(SurfaceId("library"), route.surfaceId)
        assertNull(route.routeArgs)
    }
}
