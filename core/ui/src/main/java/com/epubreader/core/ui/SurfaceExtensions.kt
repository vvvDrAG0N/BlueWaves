package com.epubreader.core.ui

interface SurfaceExtension {
    val extensionId: String
    val enabled: Boolean
        get() = true
}

data class ExtensionPoint<E : SurfaceExtension>(
    val id: String,
)

data class ExtensionFailure(
    val pointId: String,
    val extensionId: String,
    val cause: Throwable,
)

data class ExtensionResolution<T>(
    val values: List<T>,
    val disabledExtensionIds: List<String>,
    val failures: List<ExtensionFailure>,
)

inline fun <E : SurfaceExtension, T> resolveExtensionPoint(
    point: ExtensionPoint<E>,
    extensions: List<E>,
    build: (E) -> T?,
): ExtensionResolution<T> {
    val resolvedValues = mutableListOf<T>()
    val disabledExtensionIds = mutableListOf<String>()
    val failures = mutableListOf<ExtensionFailure>()
    val seenIds = mutableSetOf<String>()

    extensions.forEach { extension ->
        require(seenIds.add(extension.extensionId)) {
            "Duplicate extensionId for ${point.id}: ${extension.extensionId}"
        }

        if (!extension.enabled) {
            disabledExtensionIds += extension.extensionId
            return@forEach
        }

        runCatching { build(extension) }
            .onSuccess { value ->
                if (value != null) {
                    resolvedValues += value
                }
            }
            .onFailure { cause ->
                disabledExtensionIds += extension.extensionId
                failures += ExtensionFailure(
                    pointId = point.id,
                    extensionId = extension.extensionId,
                    cause = cause,
                )
            }
    }

    return ExtensionResolution(
        values = resolvedValues,
        disabledExtensionIds = disabledExtensionIds,
        failures = failures,
    )
}
