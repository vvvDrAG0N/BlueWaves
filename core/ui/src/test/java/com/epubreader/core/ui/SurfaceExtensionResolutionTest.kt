package com.epubreader.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SurfaceExtensionResolutionTest {
    private val testPoint = ExtensionPoint<TestExtension>("test.point")

    @Test
    fun resolveExtensionPoint_preservesRegistrationOrderAndSkipsDisabledExtensions() {
        val resolution = resolveExtensionPoint(
            point = testPoint,
            extensions = listOf(
                TestExtension("first"),
                TestExtension("second", enabled = false),
                TestExtension("third"),
            ),
        ) { extension ->
            extension.extensionId
        }

        assertEquals(listOf("first", "third"), resolution.values)
        assertEquals(listOf("second"), resolution.disabledExtensionIds)
        assertTrue(resolution.failures.isEmpty())
    }

    @Test
    fun resolveExtensionPoint_containsFailuresWithoutStoppingLaterExtensions() {
        val resolution = resolveExtensionPoint(
            point = testPoint,
            extensions = listOf(
                TestExtension("first"),
                TestExtension("boom"),
                TestExtension("third"),
            ),
        ) { extension ->
            if (extension.extensionId == "boom") {
                error("broken extension")
            }
            extension.extensionId
        }

        assertEquals(listOf("first", "third"), resolution.values)
        assertEquals(listOf("boom"), resolution.disabledExtensionIds)
        assertEquals(1, resolution.failures.size)
        assertEquals("boom", resolution.failures.single().extensionId)
        assertEquals("test.point", resolution.failures.single().pointId)
    }

    @Test
    fun resolveExtensionPoint_rejectsDuplicateExtensionIds() {
        try {
            resolveExtensionPoint(
                point = testPoint,
                extensions = listOf(
                    TestExtension("duplicate"),
                    TestExtension("duplicate"),
                ),
            ) { extension ->
                extension.extensionId
            }
            org.junit.Assert.fail("Expected duplicate extension IDs to be rejected.")
        } catch (error: IllegalArgumentException) {
            assertEquals("Duplicate extensionId for test.point: duplicate", error.message)
        }
    }

    private data class TestExtension(
        override val extensionId: String,
        override val enabled: Boolean = true,
    ) : SurfaceExtension
}
