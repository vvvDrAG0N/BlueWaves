package com.epubreader.feature.library.internal

import com.epubreader.core.debug.AppLog
import com.epubreader.core.ui.ExtensionFailure
import com.epubreader.core.ui.ExtensionPoint
import com.epubreader.core.ui.RenderSurfaceBoxExtension
import com.epubreader.core.ui.RenderSurfaceExtension
import com.epubreader.core.ui.resolveExtensionPoint
import com.epubreader.feature.library.LibraryActionExtension
import com.epubreader.feature.library.LibraryActionSlot
import com.epubreader.feature.library.LibraryDecorationExtension
import com.epubreader.feature.library.LibraryDecorationSlot
import com.epubreader.feature.library.LibraryExtensionContext
import com.epubreader.feature.library.LibraryImportExtension
import com.epubreader.feature.library.LibraryImportHook

private val LibraryImportPoint = ExtensionPoint<LibraryImportExtension>(id = "library.import")
private val LibraryActionPoint = ExtensionPoint<LibraryActionExtension>(id = "library.action")
private val LibraryDecorationPoint = ExtensionPoint<LibraryDecorationExtension>(id = "library.decoration")

internal data class LibraryResolvedHostExtensions(
    val importHooks: List<LibraryImportHook>,
    val actionSlots: List<LibraryActionSlot>,
    val decorationSlots: List<LibraryDecorationSlot>,
    val disabledExtensionIds: List<String>,
    val failures: List<com.epubreader.core.ui.ExtensionFailure>,
)

internal fun resolveLibraryHostExtensions(
    importExtensions: List<LibraryImportExtension>,
    actionExtensions: List<LibraryActionExtension>,
    decorationExtensions: List<LibraryDecorationExtension>,
    context: LibraryExtensionContext,
    fallbackImportHook: LibraryImportHook,
): LibraryResolvedHostExtensions {
    val importResolution = resolveExtensionPoint(
        point = LibraryImportPoint,
        extensions = importExtensions,
    ) { extension ->
        extension.createImportHook(context)
    }
    val actionResolution = resolveExtensionPoint(
        point = LibraryActionPoint,
        extensions = actionExtensions,
    ) { extension ->
        extension.createActionSlot(context)
    }
    val decorationResolution = resolveExtensionPoint(
        point = LibraryDecorationPoint,
        extensions = decorationExtensions,
    ) { extension ->
        extension.createDecorationSlot(context)
    }

    return LibraryResolvedHostExtensions(
        importHooks = importResolution.values + fallbackImportHook,
        actionSlots = actionResolution.values.map(::wrapLibraryActionSlot),
        decorationSlots = decorationResolution.values.map(::wrapLibraryDecorationSlot),
        disabledExtensionIds = importResolution.disabledExtensionIds +
            actionResolution.disabledExtensionIds +
            decorationResolution.disabledExtensionIds,
        failures = importResolution.failures + actionResolution.failures + decorationResolution.failures,
    )
}

internal fun dispatchLibraryImportRequest(importHooks: List<LibraryImportHook>): Boolean {
    importHooks.forEach { hook ->
        val handled = runCatching { hook.onImportRequested() }
            .onFailure { cause ->
                logLibraryExtensionFailure(
                    ExtensionFailure(
                        pointId = LibraryImportPoint.id,
                        extensionId = hook.id,
                        cause = cause,
                    ),
                )
            }
            .getOrDefault(false)
        if (handled) {
            return true
        }
    }
    return false
}

private fun wrapLibraryActionSlot(slot: LibraryActionSlot): LibraryActionSlot {
    return slot.copy(
        content = {
            RenderSurfaceExtension(
                pointId = LibraryActionPoint.id,
                extensionId = slot.id,
                onFailure = ::logLibraryExtensionFailure,
                content = slot.content,
            )
        },
    )
}

private fun wrapLibraryDecorationSlot(slot: LibraryDecorationSlot): LibraryDecorationSlot {
    return slot.copy(
        content = {
            RenderSurfaceBoxExtension(
                pointId = LibraryDecorationPoint.id,
                extensionId = slot.id,
                onFailure = ::logLibraryExtensionFailure,
                content = slot.content,
            )
        },
    )
}

private fun logLibraryExtensionFailure(failure: ExtensionFailure) {
    AppLog.w(AppLog.LIBRARY, failure.cause) {
        "Library extension failed at ${failure.pointId}: ${failure.extensionId}"
    }
}
