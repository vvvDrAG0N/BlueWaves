package com.epubreader.core.model

import java.util.Locale

const val LightThemeId = "light"
const val SepiaThemeId = "sepia"
const val DarkThemeId = "dark"
const val OledThemeId = "oled"
const val CustomThemeIdPrefix = "custom-"

/**
 * Shared color seed used to derive app-wide Material colors and explicit reader colors.
 * The reader colors stay explicit so content contrast does not depend on generic Material tokens.
 */
data class ThemePalette(
    val primary: Long,
    val secondary: Long,
    val background: Long,
    val surface: Long,
    val surfaceVariant: Long,
    val outline: Long,
    val readerBackground: Long,
    val readerForeground: Long,
)

data class CustomTheme(
    val id: String,
    val name: String,
    val palette: ThemePalette,
    val isAdvanced: Boolean = true,
)

data class ThemeOption(
    val id: String,
    val name: String,
    val palette: ThemePalette,
    val isCustom: Boolean,
)

val BuiltInThemeOptions = listOf(
    ThemeOption(
        id = LightThemeId,
        name = "Light",
        palette = ThemePalette(
            primary = 0xFF6750A4,
            secondary = 0xFF625B71,
            background = 0xFFFFFFFF,
            surface = 0xFFFFFFFF,
            surfaceVariant = 0xFFE7E0EC,
            outline = 0xFF79747E,
            readerBackground = 0xFFFFFFFF,
            readerForeground = 0xFF000000,
        ),
        isCustom = false,
    ),
    ThemeOption(
        id = SepiaThemeId,
        name = "Sepia",
        palette = ThemePalette(
            primary = 0xFF8A5A44,
            secondary = 0xFF7A6657,
            background = 0xFFF4ECD8,
            surface = 0xFFF4ECD8,
            surfaceVariant = 0xFFE8DCC9,
            outline = 0xFF8F7C6C,
            readerBackground = 0xFFF4ECD8,
            readerForeground = 0xFF5B4636,
        ),
        isCustom = false,
    ),
    ThemeOption(
        id = DarkThemeId,
        name = "Dark",
        palette = ThemePalette(
            primary = 0xFFD0BCFF,
            secondary = 0xFFCCC2DC,
            background = 0xFF1C1B1F,
            surface = 0xFF1C1B1F,
            surfaceVariant = 0xFF49454F,
            outline = 0xFF938F99,
            readerBackground = 0xFF121212,
            readerForeground = 0xFFFFFFFF,
        ),
        isCustom = false,
    ),
    ThemeOption(
        id = OledThemeId,
        name = "OLED",
        palette = ThemePalette(
            primary = 0xFFD0BCFF,
            secondary = 0xFFCCC2DC,
            background = 0xFF000000,
            surface = 0xFF000000,
            surfaceVariant = 0xFF000000,
            outline = 0xFF333333,
            readerBackground = 0xFF000000,
            readerForeground = 0xFFFFFFFF,
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
    val normalized = themeId?.trim().orEmpty()
    return if (findThemeOption(normalized, customThemes) != null) normalized else LightThemeId
}

fun themePaletteSeed(themeId: String, customThemes: List<CustomTheme>): ThemePalette {
    return findThemeOption(themeId, customThemes)?.palette ?: BuiltInThemeOptions.first().palette
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
    
    return ThemePalette(
        primary = primary,
        secondary = secondary,
        background = background,
        surface = surface,
        surfaceVariant = surfaceVariant,
        outline = outline,
        readerBackground = readerBackground,
        readerForeground = readerForeground,
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
    val librarySort: String = "added_desc",
    val favoriteLibrary: String = "My Library",
    val bookGroups: String = "{}",
    val folderSorts: String = "{}",
    val folderOrder: String = "[]"
)

/**
 * Data class for per-book reading progress.
 * scrollIndex refers to the first visible item in the reader LazyColumn.
 */
data class BookProgress(
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
    val lastChapterHref: String? = null
)
