package com.epubreader.app

import com.epubreader.core.ui.SurfaceId
import com.epubreader.core.ui.SurfaceRouteDefinition

internal class SurfaceRegistry<T : SurfaceRouteDefinition>(
    definitions: List<T>,
) {
    val all: List<T> = definitions.toList()

    private val definitionsById: Map<SurfaceId, T> = buildMap {
        definitions.forEach { definition ->
            val existing = put(definition.surfaceId, definition)
            require(existing == null) {
                "Duplicate surfaceId: ${definition.surfaceId.value}"
            }
        }
    }

    fun find(surfaceId: SurfaceId): T? = definitionsById[surfaceId]
}
