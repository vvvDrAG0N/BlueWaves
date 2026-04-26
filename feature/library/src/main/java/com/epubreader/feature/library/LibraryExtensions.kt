package com.epubreader.feature.library

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import com.epubreader.core.ui.SurfaceExtension

data class LibraryExtensionContext(
    val selectedFolderName: String,
    val isBookSelectionMode: Boolean,
    val isImportInFlight: Boolean,
)

data class LibraryImportHook(
    val id: String,
    val onImportRequested: () -> Boolean,
)

data class LibraryActionSlot(
    val id: String,
    val content: @Composable () -> Unit,
)

data class LibraryDecorationSlot(
    val id: String,
    val content: @Composable BoxScope.() -> Unit,
)

interface LibraryImportExtension : SurfaceExtension {
    fun createImportHook(context: LibraryExtensionContext): LibraryImportHook?
}

interface LibraryActionExtension : SurfaceExtension {
    fun createActionSlot(context: LibraryExtensionContext): LibraryActionSlot?
}

interface LibraryDecorationExtension : SurfaceExtension {
    fun createDecorationSlot(context: LibraryExtensionContext): LibraryDecorationSlot?
}
