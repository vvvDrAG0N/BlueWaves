package com.epubreader.app

import com.epubreader.core.ui.ShellChromeSpec
import com.epubreader.core.ui.SurfaceId
import com.epubreader.core.ui.SurfaceRouteDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SurfaceRegistryTest {

    @Test
    fun surfaceRegistry_rejectsDuplicateSurfaceIds() {
        val duplicateId = SurfaceId("reader")

        try {
            SurfaceRegistry(
                listOf(
                    FakeSurfaceDefinition(surfaceId = duplicateId),
                    FakeSurfaceDefinition(surfaceId = duplicateId),
                ),
            )
            org.junit.Assert.fail("Expected duplicate surface IDs to be rejected.")
        } catch (error: IllegalArgumentException) {
            assertEquals("Duplicate surfaceId: reader", error.message)
        }
    }

    @Test
    fun surfaceRegistry_returnsMatchingDefinitionWhenRegistered() {
        val library = FakeSurfaceDefinition(surfaceId = SurfaceId("library"))
        val registry = SurfaceRegistry(listOf(library))

        assertEquals(library, registry.find(SurfaceId("library")))
        assertNull(registry.find(SurfaceId("missing")))
    }

    @Test
    fun surfaceRegistry_preservesRegistrationOrder() {
        val first = FakeSurfaceDefinition(surfaceId = SurfaceId("first"))
        val second = FakeSurfaceDefinition(surfaceId = SurfaceId("second"))

        val registry = SurfaceRegistry(listOf(first, second))

        assertEquals(listOf(first, second), registry.all)
    }

    private data class FakeSurfaceDefinition(
        override val surfaceId: SurfaceId,
        override val chromeSpec: ShellChromeSpec = ShellChromeSpec(),
    ) : SurfaceRouteDefinition
}
