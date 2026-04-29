package com.epubreader.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class ThemeColorPickerSafeZoneState(
    val zone: ThemeColorPickerSafeZone?,
    val isLoading: Boolean,
    val isHueCached: (Float) -> Boolean = { false },
)

@Composable
internal fun rememberGuidedSafeZoneState(
    isGuided: Boolean,
    pickerHue: Float,
    guidedPreviewValueChange: ((String) -> ThemeColorPickerPreviewResult)?,
    safeZoneResolverOverride: ThemeColorPickerSafeZoneResolver?,
): ThemeColorPickerSafeZoneState {
    val safeZoneCache = remember(guidedPreviewValueChange) {
        guidedPreviewValueChange?.let { ThemeColorPickerSafeZoneCache() }
    }
    val safeZoneResolver = remember(guidedPreviewValueChange, safeZoneCache, safeZoneResolverOverride) {
        when {
            guidedPreviewValueChange == null || safeZoneCache == null -> null
            safeZoneResolverOverride != null -> safeZoneResolverOverride
            else -> defaultThemeColorPickerSafeZoneResolver(safeZoneCache)
        }
    }
    val isHueCached = remember(isGuided, safeZoneCache, guidedPreviewValueChange, safeZoneResolver) {
        { hue: Float ->
            isGuided &&
                guidedPreviewValueChange != null &&
                safeZoneResolver != null &&
                safeZoneCache?.cachedZoneForHue(hue) != null
        }
    }
    val hueBucket = pickerHue.toSafeZoneHueBucket()

    val guidedSafeZoneState by key(isGuided, guidedPreviewValueChange, hueBucket) {
        produceState(
            initialValue = initialGuidedSafeZoneState(
                isGuided = isGuided,
                pickerHue = pickerHue,
                guidedPreviewValueChange = guidedPreviewValueChange,
                safeZoneResolver = safeZoneResolver,
                safeZoneCache = safeZoneCache,
            ),
        ) {
            if (!isGuided || guidedPreviewValueChange == null || safeZoneResolver == null) {
                value = ThemeColorPickerSafeZoneState(
                    zone = null,
                    isLoading = false,
                    isHueCached = isHueCached,
                )
                return@produceState
            }

            val cachedZone = safeZoneCache?.cachedZoneForHue(pickerHue)
            if (cachedZone != null) {
                value = ThemeColorPickerSafeZoneState(
                    zone = cachedZone,
                    isLoading = false,
                    isHueCached = isHueCached,
                )
                return@produceState
            }

            value = ThemeColorPickerSafeZoneState(
                zone = null,
                isLoading = true,
                isHueCached = isHueCached,
            )
            value = ThemeColorPickerSafeZoneState(
                zone = withContext(Dispatchers.Default) {
                    safeZoneResolver.resolve(pickerHue, guidedPreviewValueChange)
                },
                isLoading = false,
                isHueCached = isHueCached,
            )
        }
    }

    return guidedSafeZoneState
}

private fun initialGuidedSafeZoneState(
    isGuided: Boolean,
    pickerHue: Float,
    guidedPreviewValueChange: ((String) -> ThemeColorPickerPreviewResult)?,
    safeZoneResolver: ThemeColorPickerSafeZoneResolver?,
    safeZoneCache: ThemeColorPickerSafeZoneCache?,
): ThemeColorPickerSafeZoneState {
    if (!isGuided || guidedPreviewValueChange == null || safeZoneResolver == null) {
        return ThemeColorPickerSafeZoneState(
            zone = null,
            isLoading = false,
            isHueCached = { false },
        )
    }
    val cache = safeZoneCache ?: return ThemeColorPickerSafeZoneState(
        zone = null,
        isLoading = true,
        isHueCached = { false },
    )

    return cache.cachedZoneForHue(pickerHue)?.let { cachedZone ->
        ThemeColorPickerSafeZoneState(
            zone = cachedZone,
            isLoading = false,
            isHueCached = { hue -> cache.cachedZoneForHue(hue) != null },
        )
    } ?: ThemeColorPickerSafeZoneState(
        zone = null,
        isLoading = true,
        isHueCached = { hue -> cache.cachedZoneForHue(hue) != null },
    )
}
