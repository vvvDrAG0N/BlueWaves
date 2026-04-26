package com.epubreader.app

import com.epubreader.core.ui.SurfaceId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSurfaceRegistryTest {

    @Test
    fun chromeSpecFor_readerUsesImmersiveChrome() {
        val chromeSpec = AppSurfaceRegistry.resolve(AppRoute.Reader(bookId = "book-123")).chromeSpec

        assertTrue(chromeSpec.immersiveSystemBars)
    }

    @Test
    fun chromeSpecFor_unknownSurfaceFallsBackToDefaultChrome() {
        val chromeSpec = AppSurfaceRegistry.resolve(
            AppRoute(surfaceId = SurfaceId("missing-surface")),
        ).chromeSpec

        assertFalse(chromeSpec.immersiveSystemBars)
    }

    @Test
    fun resolve_decodeInvalidRouteFallsBackToUnavailableSurfaceIdentity() {
        val resolved = AppSurfaceRegistry.resolve(
            AppRoute(surfaceId = SurfaceId("reader")),
        )

        assertEquals(SurfaceId("app.unavailable"), resolved.surfaceId)
        assertFalse(resolved.chromeSpec.immersiveSystemBars)
    }
}
