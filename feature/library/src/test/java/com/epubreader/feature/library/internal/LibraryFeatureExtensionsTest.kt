package com.epubreader.feature.library.internal

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import com.epubreader.core.ui.ExtensionFailure
import com.epubreader.feature.library.LibraryActionExtension
import com.epubreader.feature.library.LibraryActionSlot
import com.epubreader.feature.library.LibraryDecorationExtension
import com.epubreader.feature.library.LibraryDecorationSlot
import com.epubreader.feature.library.LibraryExtensionContext
import com.epubreader.feature.library.LibraryImportExtension
import com.epubreader.feature.library.LibraryImportHook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFeatureExtensionsTest {

    private val extensionContext = LibraryExtensionContext(
        selectedFolderName = RootLibraryName,
        isBookSelectionMode = false,
        isImportInFlight = false,
    )

    @Test
    fun resolveLibraryHostExtensions_mergesEnabledExtensionsInDeterministicOrder() {
        val resolution = resolveLibraryHostExtensions(
            importExtensions = listOf(
                importExtension("import-a"),
                importExtension("import-b"),
            ),
            actionExtensions = listOf(
                actionExtension("action-a"),
                actionExtension("action-b"),
            ),
            decorationExtensions = listOf(
                decorationExtension("decoration-a"),
                decorationExtension("decoration-b"),
            ),
            context = extensionContext,
            fallbackImportHook = LibraryImportHook(id = "host-default") { true },
        )

        assertEquals(
            listOf("import-a", "import-b", "host-default"),
            resolution.importHooks.map(LibraryImportHook::id),
        )
        assertEquals(
            listOf("action-a", "action-b"),
            resolution.actionSlots.map(LibraryActionSlot::id),
        )
        assertEquals(
            listOf("decoration-a", "decoration-b"),
            resolution.decorationSlots.map(LibraryDecorationSlot::id),
        )
        assertTrue(resolution.disabledExtensionIds.isEmpty())
        assertTrue(resolution.failures.isEmpty())
    }

    @Test
    fun resolveLibraryHostExtensions_tracksDisabledExtensionsWithoutResolvingThem() {
        val resolution = resolveLibraryHostExtensions(
            importExtensions = listOf(
                importExtension("import-disabled", enabled = false),
                importExtension("import-enabled"),
            ),
            actionExtensions = listOf(
                actionExtension("action-disabled", enabled = false),
                actionExtension("action-enabled"),
            ),
            decorationExtensions = listOf(
                decorationExtension("decoration-disabled", enabled = false),
                decorationExtension("decoration-enabled"),
            ),
            context = extensionContext,
            fallbackImportHook = LibraryImportHook(id = "host-default") { true },
        )

        assertEquals(
            listOf("import-enabled", "host-default"),
            resolution.importHooks.map(LibraryImportHook::id),
        )
        assertEquals(listOf("action-enabled"), resolution.actionSlots.map(LibraryActionSlot::id))
        assertEquals(
            listOf("decoration-enabled"),
            resolution.decorationSlots.map(LibraryDecorationSlot::id),
        )
        assertEquals(
            listOf("import-disabled", "action-disabled", "decoration-disabled"),
            resolution.disabledExtensionIds,
        )
        assertTrue(resolution.failures.isEmpty())
    }

    @Test
    fun resolveLibraryHostExtensions_containsFailuresAndKeepsRemainingExtensions() {
        val resolution = resolveLibraryHostExtensions(
            importExtensions = listOf(
                importExtension("import-ok"),
                failingImportExtension("import-boom"),
            ),
            actionExtensions = listOf(
                failingActionExtension("action-boom"),
                actionExtension("action-ok"),
            ),
            decorationExtensions = listOf(
                decorationExtension("decoration-ok"),
                failingDecorationExtension("decoration-boom"),
            ),
            context = extensionContext,
            fallbackImportHook = LibraryImportHook(id = "host-default") { true },
        )

        assertEquals(
            listOf("import-ok", "host-default"),
            resolution.importHooks.map(LibraryImportHook::id),
        )
        assertEquals(listOf("action-ok"), resolution.actionSlots.map(LibraryActionSlot::id))
        assertEquals(
            listOf("decoration-ok"),
            resolution.decorationSlots.map(LibraryDecorationSlot::id),
        )
        assertEquals(
            listOf("import-boom", "action-boom", "decoration-boom"),
            resolution.disabledExtensionIds,
        )
        assertEquals(
            listOf(
                ExtensionFailure(pointId = "library.import", extensionId = "import-boom", cause = boom("import-boom")),
                ExtensionFailure(pointId = "library.action", extensionId = "action-boom", cause = boom("action-boom")),
                ExtensionFailure(
                    pointId = "library.decoration",
                    extensionId = "decoration-boom",
                    cause = boom("decoration-boom"),
                ),
            ).map { it.pointId to it.extensionId },
            resolution.failures.map { it.pointId to it.extensionId },
        )
    }

    private fun importExtension(
        id: String,
        enabled: Boolean = true,
    ): LibraryImportExtension {
        return object : LibraryImportExtension {
            override val extensionId: String = id
            override val enabled: Boolean = enabled

            override fun createImportHook(context: LibraryExtensionContext): LibraryImportHook {
                return LibraryImportHook(id = id) { false }
            }
        }
    }

    private fun failingImportExtension(id: String): LibraryImportExtension {
        return object : LibraryImportExtension {
            override val extensionId: String = id

            override fun createImportHook(context: LibraryExtensionContext): LibraryImportHook {
                throw boom(id)
            }
        }
    }

    private fun actionExtension(
        id: String,
        enabled: Boolean = true,
    ): LibraryActionExtension {
        return object : LibraryActionExtension {
            override val extensionId: String = id
            override val enabled: Boolean = enabled

            override fun createActionSlot(context: LibraryExtensionContext): LibraryActionSlot {
                return LibraryActionSlot(id = id, content = {})
            }
        }
    }

    private fun failingActionExtension(id: String): LibraryActionExtension {
        return object : LibraryActionExtension {
            override val extensionId: String = id

            override fun createActionSlot(context: LibraryExtensionContext): LibraryActionSlot {
                throw boom(id)
            }
        }
    }

    private fun decorationExtension(
        id: String,
        enabled: Boolean = true,
    ): LibraryDecorationExtension {
        return object : LibraryDecorationExtension {
            override val extensionId: String = id
            override val enabled: Boolean = enabled

            override fun createDecorationSlot(context: LibraryExtensionContext): LibraryDecorationSlot {
                return LibraryDecorationSlot(id = id, content = {})
            }
        }
    }

    private fun failingDecorationExtension(id: String): LibraryDecorationExtension {
        return object : LibraryDecorationExtension {
            override val extensionId: String = id

            override fun createDecorationSlot(context: LibraryExtensionContext): LibraryDecorationSlot {
                throw boom(id)
            }
        }
    }

    private fun boom(id: String): IllegalStateException = IllegalStateException("boom:$id")
}
