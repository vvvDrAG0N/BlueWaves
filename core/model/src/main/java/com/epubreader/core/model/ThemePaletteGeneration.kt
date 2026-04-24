package com.epubreader.core.model

import kotlin.math.abs
import kotlin.math.roundToInt

private const val AppTextContrastRatio = 4.5
private const val MutedTextContrastRatio = 3.0
private const val ReaderTextContrastRatio = 7.0
private const val ControlContrastRatio = 4.5
private const val StartupTextContrastRatio = 7.0
private const val FavoriteAccentContrastRatio = 3.0
private const val CoverOverlayTextContrastRatio = 4.5

data class GuidedThemePaletteInput(
    val accent: Long,
    val appBackground: Long,
    val chromeAccent: Long? = null,
    val appSurface: Long? = null,
    val appForeground: Long? = null,
    val appForegroundMuted: Long? = null,
    val readerBackground: Long? = null,
    val readerForeground: Long? = null,
    val overlayScrim: Long? = null,
    val readerLinked: Boolean = true,
)

fun generatePaletteFromGuidedInput(input: GuidedThemePaletteInput): ThemePalette {
    val accent = toOpaqueColorLong(input.accent)
    val appBackground = toOpaqueColorLong(input.appBackground)
    val isDarkApp = isDarkBackground(appBackground)
    val appSurface = input.appSurface?.let(::toOpaqueColorLong) ?: deriveSurface(appBackground)
    val appForeground = ensureContrast(
        foreground = input.appForeground?.let(::toOpaqueColorLong) ?: deriveForeground(accent, appBackground),
        minRatio = AppTextContrastRatio,
        backgrounds = longArrayOf(appBackground, appSurface),
    )
    val chromeAccent = ensureContrast(
        foreground = input.chromeAccent?.let(::toOpaqueColorLong) ?: deriveChromeAccent(accent, appBackground),
        minRatio = ControlContrastRatio,
        backgrounds = longArrayOf(appBackground, appSurface),
    )
    val appForegroundMuted = ensureContrast(
        foreground = input.appForegroundMuted?.let(::toOpaqueColorLong)
            ?: deriveAppForegroundMuted(appForeground, appBackground),
        minRatio = MutedTextContrastRatio,
        backgrounds = longArrayOf(appBackground, appSurface),
    )
    val appSurfaceVariant = deriveSurfaceVariant(appSurface, appBackground)
    val appOutline = deriveOutline(appSurfaceVariant, appForeground, isDarkApp)

    val readerBackground = if (input.readerLinked) {
        deriveReaderBackground(appBackground)
    } else {
        input.readerBackground?.let(::toOpaqueColorLong) ?: deriveReaderBackground(appBackground)
    }
    val readerForeground = ensureContrast(
        foreground = input.readerForeground?.let(::toOpaqueColorLong)
            ?: deriveReaderForeground(appForeground, accent, readerBackground),
        minRatio = ReaderTextContrastRatio,
        backgrounds = longArrayOf(readerBackground),
    )
    val readerForegroundMuted = ensureContrast(
        foreground = deriveReaderForegroundMuted(readerForeground, readerBackground),
        minRatio = MutedTextContrastRatio,
        backgrounds = longArrayOf(readerBackground),
    )
    val readerAccent = ensureContrast(
        foreground = deriveReaderAccent(accent, readerBackground),
        minRatio = ControlContrastRatio,
        backgrounds = longArrayOf(readerBackground),
    )
    val overlayScrim = input.overlayScrim?.let(::toOpaqueColorLong) ?: deriveOverlayScrim(appBackground)

    return ThemePalette(
        primary = accent,
        secondary = chromeAccent,
        background = appBackground,
        surface = appSurface,
        surfaceVariant = appSurfaceVariant,
        outline = appOutline,
        readerBackground = readerBackground,
        readerForeground = readerForeground,
        systemForeground = appForeground,
        appForegroundMuted = appForegroundMuted,
        readerForegroundMuted = readerForegroundMuted,
        readerAccent = readerAccent,
        overlayScrim = overlayScrim,
    )
}

fun generatePaletteFromBase(
    primary: Long,
    background: Long,
): ThemePalette {
    return generatePaletteFromGuidedInput(
        GuidedThemePaletteInput(
            accent = primary,
            appBackground = background,
        ),
    )
}

fun deriveAppForegroundMuted(appForeground: Long, background: Long): Long {
    val base = blendColors(appForeground, background, if (isDarkBackground(background)) 0.34f else 0.28f)
    return shiftLightness(base, if (isDarkBackground(background)) 0.06f else -0.06f, saturationScale = 0.9f)
}

fun deriveReaderForegroundMuted(readerForeground: Long, readerBackground: Long): Long {
    val base = blendColors(readerForeground, readerBackground, if (isDarkBackground(readerBackground)) 0.28f else 0.24f)
    return shiftLightness(base, if (isDarkBackground(readerBackground)) 0.07f else -0.07f, saturationScale = 0.88f)
}

fun deriveReaderAccent(accent: Long, readerBackground: Long): Long {
    val target = if (isDarkBackground(readerBackground)) 0xFFFFFFFFL else 0xFF000000L
    val blended = blendColors(accent, target, if (isDarkBackground(readerBackground)) 0.12f else 0.18f)
    return shiftLightness(blended, if (isDarkBackground(readerBackground)) 0.08f else -0.06f, saturationScale = 0.96f)
}

fun deriveOverlayScrim(background: Long): Long {
    val target = 0xFF000000L
    val base = blendColors(background, target, if (isDarkBackground(background)) 0.38f else 0.82f)
    return shiftLightness(base, if (isDarkBackground(background)) -0.03f else 0f, saturationScale = 0.65f)
}

fun deriveStartupBackground(
    appBackground: Long,
    appSurface: Long,
): Long {
    val base = blendColors(
        start = appBackground,
        end = appSurface,
        ratio = if (isDarkBackground(appBackground)) 0.26f else 0.14f,
    )
    return shiftLightness(
        color = base,
        delta = if (isDarkBackground(appBackground)) -0.03f else 0.015f,
        saturationScale = 0.92f,
    )
}

fun deriveStartupForeground(
    startupBackground: Long,
    appForeground: Long,
): Long {
    val candidate = blendColors(
        start = appForeground,
        end = contrastColor(startupBackground),
        ratio = 0.4f,
    )
    return ensureContrast(
        foreground = candidate,
        minRatio = StartupTextContrastRatio,
        backgrounds = longArrayOf(startupBackground),
    )
}

fun deriveFavoriteAccent(
    accent: Long,
    appBackground: Long,
    appSurface: Long,
): Long {
    val warmTarget = 0xFFFFC857L
    val blended = blendColors(accent, warmTarget, 0.42f)
    val polished = shiftLightness(
        color = blended,
        delta = if (isDarkBackground(appBackground)) 0.08f else -0.04f,
        saturationScale = 1.08f,
    )
    return ensureContrast(
        foreground = polished,
        minRatio = FavoriteAccentContrastRatio,
        backgrounds = longArrayOf(appBackground, appSurface),
    )
}

fun deriveCoverOverlayScrim(overlayScrim: Long): Long {
    val base = blendColors(
        start = overlayScrim,
        end = 0xFF000000L,
        ratio = if (isDarkBackground(overlayScrim)) 0.28f else 0.42f,
    )
    val polished = shiftLightness(
        color = base,
        delta = if (isDarkBackground(base)) -0.04f else -0.16f,
        saturationScale = 0.75f,
    )
    return ensureBackgroundContrast(
        background = polished,
        preferredForeground = 0xFFFFFFFFL,
        minRatio = CoverOverlayTextContrastRatio,
    )
}

fun deriveCoverOverlayForeground(coverOverlayScrim: Long): Long {
    val scrim = toOpaqueColorLong(coverOverlayScrim)
    val white = 0xFFFFFFFFL
    val black = 0xFF000000L
    val preferred = if (colorContrast(white, scrim) >= colorContrast(black, scrim)) {
        white
    } else {
        black
    }
    return ensureContrast(
        foreground = preferred,
        minRatio = CoverOverlayTextContrastRatio,
        backgrounds = longArrayOf(scrim),
    )
}

fun isPaletteReaderLinked(palette: ThemePalette): Boolean {
    val linkedPalette = generatePaletteFromGuidedInput(
        GuidedThemePaletteInput(
            accent = palette.primary,
            appBackground = palette.background,
            chromeAccent = palette.secondary,
            appSurface = palette.surface,
            appForeground = palette.systemForeground,
            appForegroundMuted = palette.appForegroundMuted,
            overlayScrim = palette.overlayScrim,
            readerLinked = true,
        ),
    )

    return areColorsClose(palette.readerBackground, linkedPalette.readerBackground) &&
        areColorsClose(palette.readerForeground, linkedPalette.readerForeground) &&
        areColorsClose(palette.readerForegroundMuted, linkedPalette.readerForegroundMuted) &&
        areColorsClose(palette.readerAccent, linkedPalette.readerAccent)
}

private fun deriveSurface(background: Long): Long {
    return shiftLightness(
        color = background,
        delta = if (isDarkBackground(background)) 0.08f else -0.03f,
        saturationScale = 0.92f,
    )
}

private fun deriveSurfaceVariant(surface: Long, background: Long): Long {
    val adjusted = shiftLightness(
        color = surface,
        delta = if (isDarkBackground(background)) 0.07f else -0.05f,
        saturationScale = 0.88f,
    )
    return blendColors(adjusted, background, if (isDarkBackground(background)) 0.12f else 0.08f)
}

private fun deriveOutline(surfaceVariant: Long, foreground: Long, isDark: Boolean): Long {
    return blendColors(surfaceVariant, foreground, if (isDark) 0.3f else 0.18f)
}

private fun deriveChromeAccent(accent: Long, background: Long): Long {
    val target = if (isDarkBackground(background)) 0xFFFFFFFFL else 0xFF000000L
    val adjusted = blendColors(accent, target, if (isDarkBackground(background)) 0.08f else 0.14f)
    return shiftLightness(adjusted, if (isDarkBackground(background)) 0.08f else -0.05f, saturationScale = 0.95f)
}

private fun deriveForeground(accent: Long, background: Long): Long {
    val target = if (isDarkBackground(background)) 0xFFFFFFFFL else 0xFF000000L
    val weighted = blendColors(accent, target, if (isDarkBackground(background)) 0.84f else 0.9f)
    return shiftLightness(weighted, if (isDarkBackground(background)) 0.02f else -0.02f, saturationScale = 0.7f)
}

private fun deriveReaderBackground(background: Long): Long {
    return shiftLightness(
        color = background,
        delta = if (isDarkBackground(background)) 0.02f else 0.01f,
        saturationScale = 0.82f,
    )
}

private fun deriveReaderForeground(appForeground: Long, accent: Long, readerBackground: Long): Long {
    val target = if (isDarkBackground(readerBackground)) 0xFFFFFFFFL else 0xFF000000L
    val accentedForeground = blendColors(accent, target, if (isDarkBackground(readerBackground)) 0.88f else 0.93f)
    return blendColors(appForeground, accentedForeground, 0.55f)
}

private fun ensureContrast(
    foreground: Long,
    minRatio: Double,
    backgrounds: LongArray,
): Long {
    val candidate = toOpaqueColorLong(foreground)
    val opaqueBackgrounds = backgrounds.map(::toOpaqueColorLong)
    if (opaqueBackgrounds.all { background -> colorContrast(candidate, background) >= minRatio }) {
        return candidate
    }

    val hsl = colorToHsl(candidate)
    hsl[1] = (hsl[1] * 0.75f).coerceIn(0f, 1f)
    val averageLuminance = opaqueBackgrounds.map(::calculateLuminance).average().toFloat()
    val shouldLighten = averageLuminance < 0.45f

    val range = if (shouldLighten) 0..100 else 100 downTo 0
    for (step in range) {
        hsl[2] = step / 100f
        val adjusted = hslToColorLong(hsl)
        if (opaqueBackgrounds.all { background -> colorContrast(adjusted, background) >= minRatio }) {
            return adjusted
        }
    }

    val black = 0xFF000000L
    val white = 0xFFFFFFFFL
    return listOf(black, white).maxByOrNull { fallback ->
        opaqueBackgrounds.minOf { background -> colorContrast(fallback, background) }
    } ?: candidate
}

private fun ensureBackgroundContrast(
    background: Long,
    preferredForeground: Long,
    minRatio: Double,
): Long {
    val candidate = toOpaqueColorLong(background)
    if (colorContrast(preferredForeground, candidate) >= minRatio) {
        return candidate
    }

    val hsl = colorToHsl(candidate)
    for (step in 100 downTo 0) {
        hsl[2] = step / 100f
        val adjusted = hslToColorLong(hsl)
        if (colorContrast(preferredForeground, adjusted) >= minRatio) {
            return adjusted
        }
    }

    return candidate
}

private fun shiftLightness(
    color: Long,
    delta: Float,
    saturationScale: Float = 1f,
): Long {
    val hsl = colorToHsl(color)
    hsl[1] = (hsl[1] * saturationScale).coerceIn(0f, 1f)
    hsl[2] = (hsl[2] + delta).coerceIn(0f, 1f)
    return hslToColorLong(hsl)
}

private fun blendColors(start: Long, end: Long, ratio: Float): Long {
    val clampedRatio = ratio.coerceIn(0f, 1f)
    val startOpaque = toOpaqueColorLong(start)
    val endOpaque = toOpaqueColorLong(end)
    val red = interpolateChannel((startOpaque shr 16) and 0xFF, (endOpaque shr 16) and 0xFF, clampedRatio)
    val green = interpolateChannel((startOpaque shr 8) and 0xFF, (endOpaque shr 8) and 0xFF, clampedRatio)
    val blue = interpolateChannel(startOpaque and 0xFF, endOpaque and 0xFF, clampedRatio)
    return 0xFF000000L or (red shl 16) or (green shl 8) or blue
}

private fun colorContrast(foreground: Long, background: Long): Double {
    val foregroundLuminance = calculateLuminance(toOpaqueColorLong(foreground))
    val backgroundLuminance = calculateLuminance(toOpaqueColorLong(background))
    val lighter = maxOf(foregroundLuminance, backgroundLuminance)
    val darker = minOf(foregroundLuminance, backgroundLuminance)
    return ((lighter + 0.05f) / (darker + 0.05f)).toDouble()
}

private fun areColorsClose(first: Long, second: Long, threshold: Int = 18): Boolean {
    val firstOpaque = toOpaqueColorLong(first)
    val secondOpaque = toOpaqueColorLong(second)
    val redDelta = abs(((firstOpaque shr 16) and 0xFF) - ((secondOpaque shr 16) and 0xFF))
    val greenDelta = abs(((firstOpaque shr 8) and 0xFF) - ((secondOpaque shr 8) and 0xFF))
    val blueDelta = abs((firstOpaque and 0xFF) - (secondOpaque and 0xFF))
    return redDelta + greenDelta + blueDelta <= threshold
}

private fun isDarkBackground(color: Long): Boolean = calculateLuminance(color) < 0.45f

private fun toOpaqueColorLong(color: Long): Long = 0xFF000000L or (color and 0x00FFFFFF)

private fun colorToHsl(color: Long): FloatArray {
    val opaqueColor = toOpaqueColorLong(color)
    val red = ((opaqueColor shr 16) and 0xFF).toFloat() / 255f
    val green = ((opaqueColor shr 8) and 0xFF).toFloat() / 255f
    val blue = (opaqueColor and 0xFF).toFloat() / 255f
    val maxChannel = maxOf(red, green, blue)
    val minChannel = minOf(red, green, blue)
    val delta = maxChannel - minChannel
    val lightness = (maxChannel + minChannel) / 2f
    val saturation = if (delta == 0f) {
        0f
    } else {
        delta / (1f - abs((2f * lightness) - 1f))
    }
    val hue = when {
        delta == 0f -> 0f
        maxChannel == red -> ((((green - blue) / delta) % 6f) + 6f) % 6f * 60f
        maxChannel == green -> (((blue - red) / delta) + 2f) * 60f
        else -> (((red - green) / delta) + 4f) * 60f
    }
    return floatArrayOf(hue.coerceIn(0f, 360f), saturation.coerceIn(0f, 1f), lightness.coerceIn(0f, 1f))
}

private fun hslToColorLong(hsl: FloatArray): Long {
    val hue = (hsl[0] % 360f + 360f) % 360f
    val saturation = hsl[1].coerceIn(0f, 1f)
    val lightness = hsl[2].coerceIn(0f, 1f)
    val chroma = (1f - abs((2f * lightness) - 1f)) * saturation
    val huePrime = hue / 60f
    val secondary = chroma * (1f - abs((huePrime % 2f) - 1f))
    val (redPrime, greenPrime, bluePrime) = when {
        huePrime < 1f -> Triple(chroma, secondary, 0f)
        huePrime < 2f -> Triple(secondary, chroma, 0f)
        huePrime < 3f -> Triple(0f, chroma, secondary)
        huePrime < 4f -> Triple(0f, secondary, chroma)
        huePrime < 5f -> Triple(secondary, 0f, chroma)
        else -> Triple(chroma, 0f, secondary)
    }
    val match = lightness - (chroma / 2f)
    val red = ((redPrime + match) * 255f).roundToInt().coerceIn(0, 255).toLong()
    val green = ((greenPrime + match) * 255f).roundToInt().coerceIn(0, 255).toLong()
    val blue = ((bluePrime + match) * 255f).roundToInt().coerceIn(0, 255).toLong()
    return 0xFF000000L or (red shl 16) or (green shl 8) or blue
}

private fun interpolateChannel(start: Long, end: Long, ratio: Float): Long {
    return (start + ((end - start) * ratio)).roundToInt().coerceIn(0, 255).toLong()
}
