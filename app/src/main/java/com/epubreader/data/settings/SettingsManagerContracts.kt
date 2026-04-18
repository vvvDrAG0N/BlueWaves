package com.epubreader.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.epubreader.core.model.BookRepresentation
import com.epubreader.core.model.BookProgress
import com.epubreader.core.model.CustomTheme
import com.epubreader.core.model.GlobalSettings
import com.epubreader.core.model.LightThemeId
import com.epubreader.core.model.ThemePalette
import com.epubreader.core.model.formatThemeColor
import com.epubreader.core.model.normalizeThemeSelection
import com.epubreader.core.model.parseThemeColorOrNull
import com.epubreader.core.model.contrastColor
import org.json.JSONArray
import org.json.JSONObject

/**
 * AI_LOAD_STRATEGY
 * - Read this file when you need the SettingsManager key/default map or the public persistence shape.
 * - Read `SettingsManager.kt` for persistence behavior and edit transactions.
 * - Read `SettingsManagerJson.kt` only when folder/order/group JSON behavior is relevant.
 */
internal val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "epub_settings")

internal const val DefaultLibraryName = "My Library"
internal const val DefaultLibrarySort = "added_desc"
internal const val EmptyJsonObject = "{}"
internal const val EmptyJsonArray = "[]"

internal object SettingsPreferenceKeys {
    val fontSize = intPreferencesKey("global_font_size")
    val fontType = stringPreferencesKey("global_font_type")
    val theme = stringPreferencesKey("global_theme")
    /**
     * JSON registry of custom themes.
     * Stored as a JSON array of objects.
     * Why JSON: Allows for an arbitrary number of themes without bloating the Preferences key space.
     * See `parseCustomThemes` for schema details.
     */
    val customThemes = stringPreferencesKey("custom_themes")
    val lineHeight = floatPreferencesKey("global_line_height")
    val horizontalPadding = intPreferencesKey("global_h_padding")
    val firstTime = booleanPreferencesKey("first_time")
    val lastSeenVersion = intPreferencesKey("last_seen_version")
    val showScrubber = booleanPreferencesKey("show_scrubber")
    val showSystemBar = booleanPreferencesKey("show_system_bar")
    val selectableText = booleanPreferencesKey("selectable_text")
    val hapticFeedback = booleanPreferencesKey("haptic_feedback")
    val allowBlankCovers = booleanPreferencesKey("allow_blank_covers")
    val librarySort = stringPreferencesKey("library_sort")
    val favoriteLibrary = stringPreferencesKey("favorite_library")
    val bookGroups = stringPreferencesKey("book_groups")
    val folderSorts = stringPreferencesKey("folder_sorts")
    val folderOrder = stringPreferencesKey("folder_order")
    val targetTranslationLanguage = stringPreferencesKey("target_translation_language")
    val readerStatusEnabled = booleanPreferencesKey("reader_status_enabled")
    val readerStatusPosition = stringPreferencesKey("reader_status_position")
    val readerStatusShowClock = booleanPreferencesKey("reader_status_show_clock")
    val readerStatusShowBattery = booleanPreferencesKey("reader_status_show_battery")
    val readerStatusShowChapterProgress = booleanPreferencesKey("reader_status_show_chapter_progress")
    val readerStatusShowChapterTitle = booleanPreferencesKey("reader_status_show_chapter_title")
}

internal data class BookProgressPreferenceKeys(
    val scrollIndex: Preferences.Key<Int>,
    val scrollOffset: Preferences.Key<Int>,
    val chapter: Preferences.Key<String>,
)

private fun bookProgressKeyPrefix(bookId: String, representation: BookRepresentation): String {
    return "${bookId}_${representation.name.lowercase()}"
}

internal fun legacyBookProgressPreferenceKeys(bookId: String): BookProgressPreferenceKeys {
    return BookProgressPreferenceKeys(
        scrollIndex = intPreferencesKey("${bookId}_scroll_index"),
        scrollOffset = intPreferencesKey("${bookId}_scroll_offset"),
        chapter = stringPreferencesKey("${bookId}_chapter"),
    )
}

internal fun bookProgressPreferenceKeys(
    bookId: String,
    representation: BookRepresentation,
): BookProgressPreferenceKeys {
    val prefix = bookProgressKeyPrefix(bookId, representation)
    return BookProgressPreferenceKeys(
        scrollIndex = intPreferencesKey("${prefix}_scroll_index"),
        scrollOffset = intPreferencesKey("${prefix}_scroll_offset"),
        chapter = stringPreferencesKey("${prefix}_chapter"),
    )
}

internal fun Preferences.toGlobalSettings(): GlobalSettings {
    val customThemes = parseCustomThemes(this[SettingsPreferenceKeys.customThemes])
    return GlobalSettings(
        fontSize = this[SettingsPreferenceKeys.fontSize] ?: 18,
        fontType = this[SettingsPreferenceKeys.fontType] ?: "serif",
        theme = normalizeThemeSelection(this[SettingsPreferenceKeys.theme] ?: LightThemeId, customThemes),
        customThemes = customThemes,
        lineHeight = this[SettingsPreferenceKeys.lineHeight] ?: 1.6f,
        horizontalPadding = this[SettingsPreferenceKeys.horizontalPadding] ?: 16,
        firstTime = this[SettingsPreferenceKeys.firstTime] ?: true,
        lastSeenVersionCode = this[SettingsPreferenceKeys.lastSeenVersion] ?: 0,
        showScrubber = this[SettingsPreferenceKeys.showScrubber] ?: false,
        showSystemBar = this[SettingsPreferenceKeys.showSystemBar] ?: false,
        selectableText = this[SettingsPreferenceKeys.selectableText] ?: false,
        hapticFeedback = this[SettingsPreferenceKeys.hapticFeedback] ?: true,
        allowBlankCovers = this[SettingsPreferenceKeys.allowBlankCovers] ?: false,
        librarySort = this[SettingsPreferenceKeys.librarySort] ?: DefaultLibrarySort,
        favoriteLibrary = this[SettingsPreferenceKeys.favoriteLibrary] ?: DefaultLibraryName,
        bookGroups = this[SettingsPreferenceKeys.bookGroups] ?: EmptyJsonObject,
        folderSorts = this[SettingsPreferenceKeys.folderSorts] ?: EmptyJsonObject,
        folderOrder = this[SettingsPreferenceKeys.folderOrder] ?: EmptyJsonArray,
        targetTranslationLanguage = this[SettingsPreferenceKeys.targetTranslationLanguage] ?: "ar",
        readerStatusUi = com.epubreader.core.model.ReaderStatusUiState(
            isEnabled = this[SettingsPreferenceKeys.readerStatusEnabled] ?: true,
            position = try {
                com.epubreader.core.model.StatusOverlayPosition.valueOf(
                    this[SettingsPreferenceKeys.readerStatusPosition] ?: com.epubreader.core.model.StatusOverlayPosition.BOTTOM.name
                )
            } catch (_: Exception) {
                com.epubreader.core.model.StatusOverlayPosition.BOTTOM
            },
            showClock = this[SettingsPreferenceKeys.readerStatusShowClock] ?: true,
            showBattery = this[SettingsPreferenceKeys.readerStatusShowBattery] ?: true,
            showChapterProgress = this[SettingsPreferenceKeys.readerStatusShowChapterProgress] ?: false,
            showChapterTitle = this[SettingsPreferenceKeys.readerStatusShowChapterTitle] ?: false,
        ),
    )
}

/**
 * Parses the custom theme registry from its JSON representation.
 *
 * [SCHEMA_DETAILS]
 * Each theme object contains:
 * - id: Unique identifier (String)
 * - name: Display name (String)
 * - primary, secondary, background, surface, surfaceVariant, outline: UI palette (Hex Strings)
 * - readerBackground, readerForeground: Reader-specific colors (Hex Strings)
 * - isAdvanced: Flag for extended customization (Boolean)
 *
 * [RESILIENCE_STRATEGY]
 * - Returns an empty list on any JSON parsing error (corruption fallback).
 * - Skips themes with missing or invalid IDs/names.
 * - Skips themes with invalid color formats.
 * - Deduplicates by ID.
 *
 * [RISK_NOTE]
 * If the JSON is manually edited and becomes invalid, all custom themes will be lost (defaults to empty list).
 */
internal fun parseCustomThemes(raw: String?): List<CustomTheme> {
    if (raw.isNullOrBlank()) {
        return emptyList()
    }

    return try {
        val parsedThemes = mutableListOf<CustomTheme>()
        val seenIds = mutableSetOf<String>()
        val jsonArray = JSONArray(raw)
        for (index in 0 until jsonArray.length()) {
            val item = jsonArray.optJSONObject(index) ?: continue
            val id = item.optString("id").trim()
            val name = item.optString("name").trim()
            if (id.isEmpty() || name.isEmpty() || !seenIds.add(id)) {
                continue
            }

            val primary = item.optString("primary").let(::parseThemeColorOrNull) ?: continue
            val secondary = item.optString("secondary").let(::parseThemeColorOrNull) ?: continue
            val background = item.optString("background").let(::parseThemeColorOrNull) ?: continue
            val surface = item.optString("surface").let(::parseThemeColorOrNull) ?: continue
            val surfaceVariant = item.optString("surfaceVariant").let(::parseThemeColorOrNull) ?: continue
            val outline = item.optString("outline").let(::parseThemeColorOrNull) ?: continue
            val readerBackground = item.optString("readerBackground").let(::parseThemeColorOrNull) ?: continue
            val readerForeground = item.optString("readerForeground").let(::parseThemeColorOrNull) ?: continue
            
            // Fallback to contrastColor(surface) for older themes
            val systemForeground = item.optString("systemForeground").let(::parseThemeColorOrNull) ?: contrastColor(surface)
            
            val isAdvanced = item.optBoolean("isAdvanced", true)

            parsedThemes += CustomTheme(
                id = id,
                name = name,
                palette = ThemePalette(
                    primary = primary,
                    secondary = secondary,
                    background = background,
                    surface = surface,
                    surfaceVariant = surfaceVariant,
                    outline = outline,
                    readerBackground = readerBackground,
                    readerForeground = readerForeground,
                    systemForeground = systemForeground,
                ),
                isAdvanced = isAdvanced,
            )
        }
        parsedThemes
    } catch (_: Exception) {
        emptyList()
    }
}

/**
 * Serializes the list of custom themes into a JSON string for persistence.
 *
 * Matches the schema expected by [parseCustomThemes].
 * Colors are formatted using [formatThemeColor] to ensure consistent hex representation.
 */
internal fun List<CustomTheme>.toCustomThemesJson(): String {
    return JSONArray().apply {
        forEach { theme ->
            put(
                JSONObject().apply {
                    put("id", theme.id)
                    put("name", theme.name)
                    put("primary", formatThemeColor(theme.palette.primary))
                    put("secondary", formatThemeColor(theme.palette.secondary))
                    put("background", formatThemeColor(theme.palette.background))
                    put("surface", formatThemeColor(theme.palette.surface))
                    put("surfaceVariant", formatThemeColor(theme.palette.surfaceVariant))
                    put("outline", formatThemeColor(theme.palette.outline))
                    put("readerBackground", formatThemeColor(theme.palette.readerBackground))
                    put("readerForeground", formatThemeColor(theme.palette.readerForeground))
                    put("systemForeground", formatThemeColor(theme.palette.systemForeground))
                    put("isAdvanced", theme.isAdvanced)
                }
            )
        }
    }.toString()
}

internal fun Preferences.toBookProgress(
    bookId: String,
    representation: BookRepresentation,
): BookProgress {
    val keys = bookProgressPreferenceKeys(bookId, representation)
    val legacyKeys = legacyBookProgressPreferenceKeys(bookId)
    return BookProgress(
        scrollIndex = this[keys.scrollIndex]
            ?: if (representation == BookRepresentation.EPUB) this[legacyKeys.scrollIndex] ?: 0 else 0,
        scrollOffset = this[keys.scrollOffset]
            ?: if (representation == BookRepresentation.EPUB) this[legacyKeys.scrollOffset] ?: 0 else 0,
        lastChapterHref = this[keys.chapter]
            ?: if (representation == BookRepresentation.EPUB) this[legacyKeys.chapter] else null,
    )
}
