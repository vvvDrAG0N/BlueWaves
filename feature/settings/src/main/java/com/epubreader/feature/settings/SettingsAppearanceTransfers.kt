package com.epubreader.feature.settings

import android.content.Context
import android.net.Uri
import com.epubreader.core.model.BuiltInThemeOptions
import com.epubreader.core.model.CustomTheme
import com.epubreader.core.model.CustomThemeIdPrefix
import com.epubreader.core.model.ThemePalette
import com.epubreader.core.model.formatThemeColor
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
        "primary",
        "secondary",
        "background",
        "surface",
        "surfaceVariant",
        "outline",
        "readerBackground",
        "readerForeground",
        "readerText",
    )
    if (colorKeys.count(json::has) < 3) return null

    val readerBg = parseThemeColorOrNull(json.optString("readerBackground")) ?: 0xFFFFFFFF
    val readerFg = parseThemeColorOrNull(json.optString("readerForeground"))
        ?: parseThemeColorOrNull(json.optString("readerText"))
        ?: 0xFF000000
    val bg = parseThemeColorOrNull(json.optString("background")) ?: 0xFFFFFFFF
    val pri = parseThemeColorOrNull(json.optString("primary")) ?: 0xFF6200EE

    val palette = ThemePalette(
        primary = pri,
        secondary = parseThemeColorOrNull(json.optString("secondary")) ?: pri,
        background = bg,
        surface = parseThemeColorOrNull(json.optString("surface")) ?: bg,
        surfaceVariant = parseThemeColorOrNull(json.optString("surfaceVariant")) ?: bg,
        outline = parseThemeColorOrNull(json.optString("outline")) ?: 0xFF757575,
        readerBackground = readerBg,
        readerForeground = readerFg,
        systemForeground = parseThemeColorOrNull(json.optString("systemForeground")) ?: 0xFF000000,
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
        val json = JSONObject().apply {
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
        }
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
                    val json = JSONObject().apply {
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
                    }
                    val entry = ZipEntry("${theme.name.replace(Regex("[^a-zA-Z0-9]"), "_")}.json")
                    zos.putNextEntry(entry)
                    zos.write(json.toString(4).toByteArray())
                    zos.closeEntry()
                }
            }
        }
    }.isSuccess
}
