package com.epubreader.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SurfaceExtensionRenderTest {

    @Test
    fun containSurfaceExtensionFailure_returnsTrueWhenRenderSucceeds() {
        val failures = mutableListOf<ExtensionFailure>()

        val rendered = containSurfaceExtensionFailure(
            pointId = "reader.tool",
            extensionId = "tool-ok",
            onFailure = failures::add,
        ) {
            Unit
        }

        assertTrue(rendered)
        assertTrue(failures.isEmpty())
    }

    @Test
    fun containSurfaceExtensionFailure_reportsFailureAndReturnsFalse() {
        val failures = mutableListOf<ExtensionFailure>()

        val rendered = containSurfaceExtensionFailure(
            pointId = "library.action",
            extensionId = "action-boom",
            onFailure = failures::add,
        ) {
            error("boom")
        }

        assertFalse(rendered)
        assertEquals(listOf("library.action" to "action-boom"), failures.map { it.pointId to it.extensionId })
        assertEquals("boom", failures.single().cause.message)
    }
}
