package com.epubreader.core.model

import androidx.compose.runtime.Immutable
import java.util.Locale

const val LightThemeId = "light"
const val SepiaThemeId = "sepia"
const val AzureThemeId = "azure"
const val DarkThemeId = "dark"
const val OledThemeId = "oled"
const val CustomThemeIdPrefix = "custom-"

@Immutable
data class ReaderStatusUiState(
    val isEnabled: Boolean = true,
    val showClock: Boolean = true,
    val showBattery: Boolean = true,
    val showChapterProgress: Boolean = false,
    val showChapterNumber: Boolean = true,
)

/**
 * Shared color seed used to derive app-wide Material colors and explicit reader colors.
 * The reader colors stay explicit so content contrast does not depend on generic Material tokens.
 */
@Immutable
data class ThemePalette(
    val primary: Long,
    val secondary: Long,
    val background: Long,
    val surface: Long,
    val surfaceVariant: Long,
    val outline: Long,
    val readerBackground: Long,
    val readerForeground: Long,
    val systemForeground: Long,
)

@Immutable
data class CustomTheme(
    val id: String,
    val name: String,
    val palette: ThemePalette,
    val isAdvanced: Boolean = true,
)

@Immutable
data class ThemeOption(
    val id: String,
    val name: String,
    val palette: ThemePalette,
    val isCustom: Boolean,
)

val BuiltInThemeOptions = listOf(
    ThemeOption(
        id = LightThemeId,
        name = "Paper White",
        palette = ThemePalette(
            primary = 0xFF4F46E5,      // Indigo 600
            secondary = 0xFF475569,    // Slate 600
            background = 0xFFFAF9F6,   // Soft Paper
            surface = 0xFFFFFFFF,
            surfaceVariant = 0xFFF3F4F6,
            outline = 0xFFD1D5DB,
            readerBackground = 0xFFFFFFFF,
            readerForeground = 0xFF000000,
            systemForeground = 0xFF18181B,
        ),
        isCustom = false,
    ),
    ThemeOption(
        id = SepiaThemeId,
        name = "Sepia",
        palette = ThemePalette(
            primary = 0xFF8B5E3C,
            secondary = 0xFF6D4C41,
            background = 0xFFF4ECD8,
            surface = 0xFFEADDC6,
            surfaceVariant = 0xFFEADDC6,
            outline = 0xFFC4AE8B,
            readerBackground = 0xFFF4ECD8,
            readerForeground = 0xFF5B4636,
            systemForeground = 0xFF5B4636,
        ),
        isCustom = false,
    ),
    ThemeOption(
        id = DarkThemeId,
        name = "Midnight",
        palette = ThemePalette(
            primary = 0xFFA5B4FC,      // Indigo 300
            secondary = 0xFF94A3B8,    // Slate 400
            background = 0xFF18181B,   // Zinc 900
            surface = 0xFF27272A,      // Zinc 800
            surfaceVariant = 0xFF3F3F46,
            outline = 0xFF52525B,
            readerBackground = 0xFF121212,
            readerForeground = 0xFFFFFFFF,
            systemForeground = 0xFFE4E4E7,
        ),
        isCustom = false,
    ),
    ThemeOption(
        id = OledThemeId,
        name = "Onyx",
        palette = ThemePalette(
            primary = 0xFF60A5FA,      // Blue 400
            secondary = 0xFF94A3B8,
            background = 0xFF000000,
            surface = 0xFF000000,
            surfaceVariant = 0xFF111827,
            outline = 0xFF374151,
            readerBackground = 0xFF000000,
            readerForeground = 0xFFFFFFFF,
            systemForeground = 0xFFFFFFFF,
        ),
        isCustom = false,
    ),
)

fun isBuiltInTheme(themeId: String): Boolean {
    return BuiltInThemeOptions.any { it.id == themeId }
}

fun availableThemeOptions(customThemes: List<CustomTheme>): List<ThemeOption> {
    return BuiltInThemeOptions + customThemes.map { theme ->
        ThemeOption(
            id = theme.id,
            name = theme.name,
            palette = theme.palette,
            isCustom = true,
        )
    }
}

fun findThemeOption(themeId: String, customThemes: List<CustomTheme>): ThemeOption? {
    return availableThemeOptions(customThemes).firstOrNull { it.id == themeId }
}

fun normalizeThemeSelection(themeId: String?, customThemes: List<CustomTheme>): String {
    val trimmed = themeId?.trim().orEmpty()
    val migrated = if (trimmed == AzureThemeId) SepiaThemeId else trimmed
    return if (findThemeOption(migrated, customThemes) != null) migrated else LightThemeId
}

fun themePaletteSeed(themeId: String, customThemes: List<CustomTheme>): ThemePalette {
    val normalizedThemeId = normalizeThemeSelection(themeId, customThemes)
    return findThemeOption(normalizedThemeId, customThemes)?.palette ?: BuiltInThemeOptions.first().palette
}

fun parseThemeColorOrNull(raw: String): Long? {
    val sanitized = raw.trim().removePrefix("#")
    val hex = when (sanitized.length) {
        6 -> "FF$sanitized"
        8 -> sanitized
        else -> return null
    }

    return hex
        .takeIf { candidate -> candidate.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' } }
        ?.toLongOrNull(16)
}

fun calculateLuminance(color: Long): Float {
    val r = ((color shr 16) and 0xFF) / 255f
    val g = ((color shr 8) and 0xFF) / 255f
    val b = (color and 0xFF) / 255f

    fun transform(c: Float): Float = if (c <= 0.03928f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()

    return 0.2126f * transform(r) + 0.7152f * transform(g) + 0.0722f * transform(b)
}

fun lerpColor(start: Long, stop: Long, fraction: Float): Long {
    val a1 = ((start shr 24) and 0xFF)
    val r1 = ((start shr 16) and 0xFF)
    val g1 = ((start shr 8) and 0xFF)
    val b1 = (start and 0xFF)

    val a2 = ((stop shr 24) and 0xFF)
    val r2 = ((stop shr 16) and 0xFF)
    val g2 = ((stop shr 8) and 0xFF)
    val b2 = (stop and 0xFF)

    val a = (a1 + (a2 - a1) * fraction).toInt().coerceIn(0, 255)
    val r = (r1 + (r2 - r1) * fraction).toInt().coerceIn(0, 255)
    val g = (g1 + (g2 - g1) * fraction).toInt().coerceIn(0, 255)
    val b = (b1 + (b2 - b1) * fraction).toInt().coerceIn(0, 255)

    return (a.toLong() shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
}

fun contrastColor(background: Long): Long {
    return if (calculateLuminance(background) > 0.5f) 0xFF000000L else 0xFFFFFFFFL
}

fun generatePaletteFromBase(
    primary: Long,
    background: Long,
): ThemePalette {
    val isDark = calculateLuminance(background) < 0.5f
    
    // Derived colors
    val secondary = lerpColor(primary, if (isDark) 0xFFFFFFFFL else 0xFF000000L, 0.2f)
    val surface = background
    val surfaceVariant = if (isDark) {
        lerpColor(background, 0xFFFFFFFFL, 0.1f)
    } else {
        lerpColor(background, 0xFF000000L, 0.05f)
    }
    val outline = if (isDark) {
        lerpColor(background, 0xFFFFFFFFL, 0.3f)
    } else {
        lerpColor(background, 0xFF000000L, 0.2f)
    }
    
    val readerBackground = background
    val readerForeground = contrastColor(readerBackground)
    val systemForeground = contrastColor(surface)
    
    return ThemePalette(
        primary = primary,
        secondary = secondary,
        background = background,
        surface = surface,
        surfaceVariant = surfaceVariant,
        outline = outline,
        readerBackground = readerBackground,
        readerForeground = readerForeground,
        systemForeground = systemForeground,
    )
}

fun formatThemeColor(color: Long): String {
    return "#%06X".format(Locale.US, color and 0x00FFFFFF)
}

fun themeButtonLabel(themeName: String, themeId: String): String {
    if (themeId == OledThemeId) {
        return "O"
    }

    val parts = themeName
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }

    if (parts.size >= 2) {
        return parts
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
    }

    return themeName
        .trim()
        .take(2)
        .uppercase(Locale.getDefault())
        .ifBlank { "A" }
}

/**
 * Data class representing the global state of the application.
 * Adding new fields here requires updating SettingsManager.updateGlobalSettings
 * and the SettingsManager.globalSettings mapping logic.
 */
@Immutable
data class GlobalSettings(
    val fontSize: Int = 18,
    val fontType: String = "serif",
    val theme: String = LightThemeId,
    val customThemes: List<CustomTheme> = emptyList(),
    val lineHeight: Float = 1.6f,
    val horizontalPadding: Int = 16,
    val firstTime: Boolean = true,
    val lastSeenVersionCode: Int = 0,
    val showScrubber: Boolean = false,
    val showSystemBar: Boolean = false,
    val selectableText: Boolean = false,
    val hapticFeedback: Boolean = true,
    val allowBlankCovers: Boolean = false,
    val librarySort: String = "added_desc",
    val favoriteLibrary: String = "My Library",
    val bookGroups: String = "{}",
    val folderSorts: String = "{}",
    val folderOrder: String = "[]",
    val targetTranslationLanguage: String = "ar",
    val showScrollToTop: Boolean = true,
    val readerStatusUi: ReaderStatusUiState = ReaderStatusUiState()
)

/**
 * Data class for per-book reading progress.
 * scrollIndex refers to the first visible item in the reader LazyColumn.
 */
@Immutable
data class BookProgress(
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
    val lastChapterHref: String? = null
)

/**
 * Checks if a proposed theme name is unique across all built-in and custom themes.
 * Normalizes by trimming, lowercase conversion, and collapsing duplicate spaces.
 *
 * @param proposedName The name being validated.
 * @param excludeThemeId The ID of the theme being edited (null if new).
 * @param customThemes The list of existing custom themes.
 * @return True if the name is unique and valid.
 */
fun isThemeNameUnique(
    proposedName: String,
    excludeThemeId: String?,
    customThemes: List<CustomTheme>
): Boolean {
    val normalizedProposed = proposedName.trim().lowercase(Locale.US).replace(Regex("\\s+"), " ")
    if (normalizedProposed.isEmpty()) return false
    
    // Check built-ins
    val builtInConflict = BuiltInThemeOptions.any {
        it.id != excludeThemeId && it.name.trim().lowercase(Locale.US).replace(Regex("\\s+"), " ") == normalizedProposed
    }
    if (builtInConflict) return false
    
    // Check custom themes
    val customConflict = customThemes.any {
        it.id != excludeThemeId && it.name.trim().lowercase(Locale.US).replace(Regex("\\s+"), " ") == normalizedProposed
    }
    
    return !customConflict
}
