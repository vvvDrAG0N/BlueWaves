package com.epubreader.feature.settings

import com.epubreader.core.model.CustomTheme
import com.epubreader.core.model.GlobalSettings
import com.epubreader.core.model.GuidedThemePaletteInput
import com.epubreader.core.model.ThemePalette
import com.epubreader.core.model.formatThemeColor
import com.epubreader.core.model.generatePaletteFromBase
import com.epubreader.core.model.generatePaletteFromGuidedInput
import com.epubreader.core.model.isPaletteReaderLinked
import com.epubreader.core.model.parseThemeColorOrNull

internal enum class ThemeEditorMode(val label: String) {
    BASIC("Basic"),
    EXTENDED("Extended"),
    ADVANCED("Advanced"),
}

internal data class ThemeEditorSession(
    val themeId: String,
    val isNew: Boolean,
    val draft: ThemeEditorDraft,
    val settings: GlobalSettings,
)

internal fun inferThemeEditorMode(theme: CustomTheme): ThemeEditorMode {
    val palette = theme.palette
    val basicPalette = generatePaletteFromBase(
        primary = palette.accent,
        background = palette.appBackground,
    )
    if (palette == basicPalette) {
        return ThemeEditorMode.BASIC
    }

    val readerLinked = isPaletteReaderLinked(palette)
    val extendedPalette = generatePaletteFromGuidedInput(
        GuidedThemePaletteInput(
            accent = palette.accent,
            appBackground = palette.appBackground,
            chromeAccent = palette.chromeAccent,
            appSurface = palette.appSurface,
            appForeground = palette.appForeground,
            appForegroundMuted = palette.appForegroundMuted,
            readerBackground = if (readerLinked) null else palette.readerBackground,
            readerForeground = if (readerLinked) null else palette.readerForeground,
            overlayScrim = palette.overlayScrim,
            readerLinked = readerLinked,
        ),
    )
    if (palette == extendedPalette) {
        return ThemeEditorMode.EXTENDED
    }

    return ThemeEditorMode.ADVANCED
}

internal data class ThemeEditorDraft(
    val name: String,
    val accent: String,
    val chromeAccent: String,
    val appBackground: String,
    val appSurface: String,
    val appSurfaceVariant: String,
    val appOutline: String,
    val appForeground: String,
    val appForegroundMuted: String,
    val readerBackground: String,
    val readerForeground: String,
    val readerForegroundMuted: String,
    val readerAccent: String,
    val overlayScrim: String,
    val startupBackground: String,
    val startupForeground: String,
    val favoriteAccent: String,
    val coverOverlayScrim: String,
    val mode: ThemeEditorMode = ThemeEditorMode.EXTENDED,
    val readerLinked: Boolean = true,
    val legacyIsAdvanced: Boolean = true,
) {
    fun toCustomTheme(themeId: String): CustomTheme? {
        val parsedAccent = parseThemeColorOrNull(accent) ?: return null
        val parsedChromeAccent = parseThemeColorOrNull(chromeAccent) ?: return null
        val parsedAppBackground = parseThemeColorOrNull(appBackground) ?: return null
        val parsedAppSurface = parseThemeColorOrNull(appSurface) ?: return null
        val parsedAppSurfaceVariant = parseThemeColorOrNull(appSurfaceVariant) ?: return null
        val parsedAppOutline = parseThemeColorOrNull(appOutline) ?: return null
        val parsedAppForeground = parseThemeColorOrNull(appForeground) ?: return null
        val parsedAppForegroundMuted = parseThemeColorOrNull(appForegroundMuted) ?: return null
        val parsedReaderBackground = parseThemeColorOrNull(readerBackground) ?: return null
        val parsedReaderForeground = parseThemeColorOrNull(readerForeground) ?: return null
        val parsedReaderForegroundMuted = parseThemeColorOrNull(readerForegroundMuted) ?: return null
        val parsedReaderAccent = parseThemeColorOrNull(readerAccent) ?: return null
        val parsedOverlayScrim = parseThemeColorOrNull(overlayScrim) ?: return null
        val parsedStartupBackground = parseThemeColorOrNull(startupBackground) ?: return null
        val parsedStartupForeground = parseThemeColorOrNull(startupForeground) ?: return null
        val parsedFavoriteAccent = parseThemeColorOrNull(favoriteAccent) ?: return null
        val parsedCoverOverlayScrim = parseThemeColorOrNull(coverOverlayScrim) ?: return null

        return CustomTheme(
            id = themeId,
            name = name.trim(),
            palette = ThemePalette(
                primary = parsedAccent,
                secondary = parsedChromeAccent,
                background = parsedAppBackground,
                surface = parsedAppSurface,
                surfaceVariant = parsedAppSurfaceVariant,
                outline = parsedAppOutline,
                readerBackground = parsedReaderBackground,
                readerForeground = parsedReaderForeground,
                systemForeground = parsedAppForeground,
                appForegroundMuted = parsedAppForegroundMuted,
                readerForegroundMuted = parsedReaderForegroundMuted,
                readerAccent = parsedReaderAccent,
                overlayScrim = parsedOverlayScrim,
                startupBackground = parsedStartupBackground,
                startupForeground = parsedStartupForeground,
                favoriteAccent = parsedFavoriteAccent,
                coverOverlayScrim = parsedCoverOverlayScrim,
            ),
            isAdvanced = legacyIsAdvanced,
        )
    }

    fun rebalanceGuidedFields(): ThemeEditorDraft? {
        return when (mode) {
            ThemeEditorMode.BASIC -> rebalanceBasic()
            ThemeEditorMode.EXTENDED -> rebalanceExtended()
            ThemeEditorMode.ADVANCED -> this
        }
    }

    fun rebalanceBasic(): ThemeEditorDraft? {
        val parsedAccent = parseThemeColorOrNull(accent) ?: return null
        val parsedAppBackground = parseThemeColorOrNull(appBackground) ?: return null
        return fromPalette(
            name = name,
            palette = generatePaletteFromBase(
                primary = parsedAccent,
                background = parsedAppBackground,
            ),
            mode = ThemeEditorMode.BASIC,
            readerLinked = true,
            legacyIsAdvanced = legacyIsAdvanced,
        )
    }

    fun rebalanceExtended(): ThemeEditorDraft? {
        val parsedAccent = parseThemeColorOrNull(accent) ?: return null
        val parsedAppBackground = parseThemeColorOrNull(appBackground) ?: return null
        val parsedChromeAccent = parseThemeColorOrNull(chromeAccent) ?: return null
        val parsedAppSurface = parseThemeColorOrNull(appSurface) ?: return null
        val parsedAppForeground = parseThemeColorOrNull(appForeground) ?: return null
        val parsedAppForegroundMuted = parseThemeColorOrNull(appForegroundMuted) ?: return null
        val parsedOverlayScrim = parseThemeColorOrNull(overlayScrim) ?: return null
        val parsedReaderBackground = parseThemeColorOrNull(readerBackground)
        val parsedReaderForeground = parseThemeColorOrNull(readerForeground)

        val palette = generatePaletteFromGuidedInput(
            GuidedThemePaletteInput(
                accent = parsedAccent,
                appBackground = parsedAppBackground,
                chromeAccent = parsedChromeAccent,
                appSurface = parsedAppSurface,
                appForeground = parsedAppForeground,
                appForegroundMuted = parsedAppForegroundMuted,
                readerBackground = if (readerLinked) null else parsedReaderBackground,
                readerForeground = if (readerLinked) null else parsedReaderForeground,
                overlayScrim = parsedOverlayScrim,
                readerLinked = readerLinked,
            ),
        )

        return fromPalette(
            name = name,
            palette = palette,
            mode = ThemeEditorMode.EXTENDED,
            readerLinked = readerLinked,
            legacyIsAdvanced = legacyIsAdvanced,
        )
    }

    companion object {
        fun fromTheme(
            theme: CustomTheme,
            mode: ThemeEditorMode = inferThemeEditorMode(theme),
        ): ThemeEditorDraft {
            return fromPalette(
                name = theme.name,
                palette = theme.palette,
                mode = mode,
                readerLinked = isPaletteReaderLinked(theme.palette),
                legacyIsAdvanced = theme.isAdvanced,
            )
        }

        fun fromPalette(
            name: String,
            palette: ThemePalette,
            mode: ThemeEditorMode,
            readerLinked: Boolean = true,
            legacyIsAdvanced: Boolean = true,
        ): ThemeEditorDraft {
            return ThemeEditorDraft(
                name = name,
                accent = formatThemeColor(palette.accent),
                chromeAccent = formatThemeColor(palette.chromeAccent),
                appBackground = formatThemeColor(palette.appBackground),
                appSurface = formatThemeColor(palette.appSurface),
                appSurfaceVariant = formatThemeColor(palette.appSurfaceVariant),
                appOutline = formatThemeColor(palette.appOutline),
                appForeground = formatThemeColor(palette.appForeground),
                appForegroundMuted = formatThemeColor(palette.appForegroundMuted),
                readerBackground = formatThemeColor(palette.readerBackground),
                readerForeground = formatThemeColor(palette.readerForeground),
                readerForegroundMuted = formatThemeColor(palette.readerForegroundMuted),
                readerAccent = formatThemeColor(palette.readerAccent),
                overlayScrim = formatThemeColor(palette.overlayScrim),
                startupBackground = formatThemeColor(palette.startupBackground),
                startupForeground = formatThemeColor(palette.startupForeground),
                favoriteAccent = formatThemeColor(palette.favoriteAccent),
                coverOverlayScrim = formatThemeColor(palette.coverOverlayScrim),
                mode = mode,
                readerLinked = readerLinked,
                legacyIsAdvanced = legacyIsAdvanced,
            )
        }
    }
}
