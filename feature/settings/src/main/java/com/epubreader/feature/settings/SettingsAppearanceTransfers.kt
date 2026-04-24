package com.epubreader.feature.settings

import android.content.Context
import android.net.Uri
import com.epubreader.core.model.BuiltInThemeOptions
import com.epubreader.core.model.CustomTheme
import com.epubreader.core.model.CustomThemeIdPrefix
import com.epubreader.core.model.GuidedThemePaletteInput
import com.epubreader.core.model.ThemePalette
import com.epubreader.core.model.formatThemeColor
import com.epubreader.core.model.generatePaletteFromGuidedInput
import com.epubreader.core.model.parseThemeColorOrNull
import com.epubreader.data.settings.SettingsManager
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal fun normalizeThemeName(name: String): String {
    return name.trim().lowercase(Locale.US).replace(Regex("\\s+"), " ")
}

internal suspend fun importThemesFromUris(
    context: Context,
    uris: List<Uri>,
    existingThemes: List<CustomTheme>,
    settingsManager: SettingsManager,
): String = withContext(Dispatchers.IO) {
    var importedCount = 0
    var skippedCount = 0
    val seenPalettes: MutableSet<ThemePalette> = existingThemes.map { it.palette }.toMutableSet()
    val seenNames: MutableSet<String> = (BuiltInThemeOptions.map { it.name } + existingThemes.map { it.name })
        .map(::normalizeThemeName)
        .toMutableSet()

    for (uri in uris) {
        try {
            val size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0
            val isZip = context.contentResolver.getType(uri)?.contains("zip") == true ||
                uri.toString().lowercase().endsWith(".zip")

            if (isZip) {
                if (size > 5 * 1024 * 1024) {
                    skippedCount++
                    continue
                }

                context.contentResolver.openInputStream(uri)?.use { input ->
                    ZipInputStream(input).use { zis ->
                        var entry: ZipEntry? = zis.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory && entry.name.lowercase().endsWith(".json") && !entry.name.contains("__MACOSX")) {
                                val content = zis.bufferedReader().readText()
                                val json = try {
                                    JSONObject(content)
                                } catch (_: Exception) {
                                    null
                                }
                                if (json != null) {
                                    val name = importSingleThemeJson(json, seenPalettes, seenNames, settingsManager)
                                    if (name != null) importedCount++ else skippedCount++
                                } else {
                                    skippedCount++
                                }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }
            } else {
                if (size > 1024 * 1024) {
                    skippedCount++
                    continue
                }

                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (!content.isNullOrBlank()) {
                    val json = try {
                        JSONObject(content)
                    } catch (_: Exception) {
                        null
                    }
                    if (json != null) {
                        val name = importSingleThemeJson(json, seenPalettes, seenNames, settingsManager)
                        if (name != null) importedCount++ else skippedCount++
                    } else {
                        skippedCount++
                    }
                }
            }
        } catch (_: Exception) {
            skippedCount++
        }
    }

    when {
        importedCount > 0 && skippedCount > 0 -> "Imported $importedCount themes ($skippedCount skipped/duplicate)"
        importedCount > 0 -> "Imported $importedCount themes successfully"
        skippedCount > 0 -> "No new themes found ($skippedCount skipped/invalid)"
        else -> "No valid theme files found"
    }
}

private suspend fun importSingleThemeJson(
    json: JSONObject,
    seenPalettes: MutableSet<ThemePalette>,
    seenNames: MutableSet<String>,
    settingsManager: SettingsManager,
): String? {
    val colorKeys = listOf(
        "accent",
        "chromeAccent",
        "appBackground",
        "appSurface",
        "appSurfaceVariant",
        "appOutline",
        "appForeground",
        "appForegroundMuted",
        "primary",
        "secondary",
        "background",
        "surface",
        "surfaceVariant",
        "outline",
        "readerBackground",
        "readerForeground",
        "readerText",
        "readerForegroundMuted",
        "readerAccent",
        "overlayScrim",
        "startupBackground",
        "startupForeground",
        "favoriteAccent",
        "coverOverlayScrim",
    )
    if (colorKeys.count(json::has) < 3) return null

    val accent = json.parseColorFromKeys("accent", "primary") ?: 0xFF6200EE
    val appBackground = json.parseColorFromKeys("appBackground", "background") ?: 0xFFFFFFFF
    val readerBackground = json.parseColorFromKeys("readerBackground")
    val readerForeground = json.parseColorFromKeys("readerForeground", "readerText")
    val basePalette = generatePaletteFromGuidedInput(
        GuidedThemePaletteInput(
            accent = accent,
            appBackground = appBackground,
            chromeAccent = json.parseColorFromKeys("chromeAccent", "secondary"),
            appSurface = json.parseColorFromKeys("appSurface", "surface"),
            appForeground = json.parseColorFromKeys("appForeground", "systemForeground"),
            appForegroundMuted = json.parseColorFromKeys("appForegroundMuted"),
            readerBackground = readerBackground,
            readerForeground = readerForeground,
            overlayScrim = json.parseColorFromKeys("overlayScrim"),
            readerLinked = readerBackground == null && readerForeground == null,
        ),
    )
    val palette = basePalette.copy(
        surfaceVariant = json.parseColorFromKeys("appSurfaceVariant", "surfaceVariant") ?: basePalette.surfaceVariant,
        outline = json.parseColorFromKeys("appOutline", "outline") ?: basePalette.outline,
        readerForegroundMuted = json.parseColorFromKeys("readerForegroundMuted") ?: basePalette.readerForegroundMuted,
        readerAccent = json.parseColorFromKeys("readerAccent") ?: basePalette.readerAccent,
        startupBackground = json.parseColorFromKeys("startupBackground") ?: basePalette.startupBackground,
        startupForeground = json.parseColorFromKeys("startupForeground") ?: basePalette.startupForeground,
        favoriteAccent = json.parseColorFromKeys("favoriteAccent") ?: basePalette.favoriteAccent,
        coverOverlayScrim = json.parseColorFromKeys("coverOverlayScrim") ?: basePalette.coverOverlayScrim,
    )
    if (seenPalettes.contains(palette)) return null

    val baseName = json.optString("name", "Imported Theme").trim()
    var finalName = baseName
    if (seenNames.contains(normalizeThemeName(finalName))) {
        var counter = 1
        while (seenNames.contains(normalizeThemeName("$baseName ($counter)"))) counter++
        finalName = "$baseName ($counter)"
    }

    val newTheme = CustomTheme(
        id = "${CustomThemeIdPrefix}${UUID.randomUUID()}",
        name = finalName,
        palette = palette,
        isAdvanced = true,
    )

    return try {
        settingsManager.saveCustomTheme(newTheme, activate = false)
        seenPalettes.add(palette)
        seenNames.add(normalizeThemeName(finalName))
        finalName
    } catch (_: Exception) {
        null
    }
}

internal suspend fun exportThemeToUri(
    context: Context,
    exportUri: Uri,
    theme: CustomTheme,
): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val json = theme.toPortableThemeJson()
        context.contentResolver.openOutputStream(exportUri)?.use { it.write(json.toString(4).toByteArray()) }
    }.isSuccess
}

internal suspend fun exportThemesPackToUri(
    context: Context,
    exportUri: Uri,
    themes: List<CustomTheme>,
): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.openOutputStream(exportUri)?.use { output ->
            ZipOutputStream(output).use { zos ->
                themes.forEach { theme ->
                    val json = theme.toPortableThemeJson()
                    val entry = ZipEntry("${theme.name.replace(Regex("[^a-zA-Z0-9]"), "_")}.json")
                    zos.putNextEntry(entry)
                    zos.write(json.toString(4).toByteArray())
                    zos.closeEntry()
                }
            }
        }
    }.isSuccess
}

private fun JSONObject.parseColorFromKeys(vararg keys: String): Long? {
    return keys.firstNotNullOfOrNull { key ->
        optString(key).takeIf { it.isNotBlank() }?.let(::parseThemeColorOrNull)
    }
}

private fun CustomTheme.toPortableThemeJson(): JSONObject {
    return JSONObject().apply {
        put("name", name)
        put("primary", formatThemeColor(palette.primary))
        put("secondary", formatThemeColor(palette.secondary))
        put("background", formatThemeColor(palette.background))
        put("surface", formatThemeColor(palette.surface))
        put("surfaceVariant", formatThemeColor(palette.surfaceVariant))
        put("outline", formatThemeColor(palette.outline))
        put("readerBackground", formatThemeColor(palette.readerBackground))
        put("readerForeground", formatThemeColor(palette.readerForeground))
        put("systemForeground", formatThemeColor(palette.systemForeground))
        put("accent", formatThemeColor(palette.accent))
        put("chromeAccent", formatThemeColor(palette.chromeAccent))
        put("appBackground", formatThemeColor(palette.appBackground))
        put("appSurface", formatThemeColor(palette.appSurface))
        put("appSurfaceVariant", formatThemeColor(palette.appSurfaceVariant))
        put("appOutline", formatThemeColor(palette.appOutline))
        put("appForeground", formatThemeColor(palette.appForeground))
        put("appForegroundMuted", formatThemeColor(palette.appForegroundMuted))
        put("readerForegroundMuted", formatThemeColor(palette.readerForegroundMuted))
        put("readerAccent", formatThemeColor(palette.readerAccent))
        put("overlayScrim", formatThemeColor(palette.overlayScrim))
        put("startupBackground", formatThemeColor(palette.startupBackground))
        put("startupForeground", formatThemeColor(palette.startupForeground))
        put("favoriteAccent", formatThemeColor(palette.favoriteAccent))
        put("coverOverlayScrim", formatThemeColor(palette.coverOverlayScrim))
    }
}
