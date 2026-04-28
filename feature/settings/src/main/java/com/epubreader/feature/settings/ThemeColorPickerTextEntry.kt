package com.epubreader.feature.settings

import com.epubreader.core.model.formatThemeColor
import com.epubreader.core.model.parseThemeColorOrNull

internal data class ThemeColorPickerRgbText(
    val red: String,
    val green: String,
    val blue: String,
)

internal data class ThemeColorPickerTextFields(
    val hexText: String,
    val rgbText: ThemeColorPickerRgbText,
    val lastValidHex: String,
    private val activeInput: ThemeColorPickerActiveInput? = null,
) {
    val preferredInput: ThemeColorPickerActiveInput?
        get() = activeInput

    fun withHexInput(nextHex: String): ThemeColorPickerTextFields {
        val sanitized = nextHex
            .uppercase()
            .filter { it.isDigit() || it in 'A'..'F' }
            .take(6)
        val resolved = sanitized.toResolvedHexOrNull()
        return if (resolved != null) {
            themeColorPickerTextFields(
                hex = resolved,
                activeInput = ThemeColorPickerActiveInput.HEX,
            )
        } else {
            copy(
                hexText = sanitized,
                activeInput = ThemeColorPickerActiveInput.HEX,
            )
        }
    }

    fun withRgbInput(
        red: String = rgbText.red,
        green: String = rgbText.green,
        blue: String = rgbText.blue,
    ): ThemeColorPickerTextFields {
        val nextRgb = ThemeColorPickerRgbText(
            red = red.sanitizeRgbChannelInput(),
            green = green.sanitizeRgbChannelInput(),
            blue = blue.sanitizeRgbChannelInput(),
        )
        val resolved = nextRgb.toResolvedHexOrNull()
        return if (resolved != null) {
            themeColorPickerTextFields(
                hex = resolved,
                rgbOverride = nextRgb,
                activeInput = ThemeColorPickerActiveInput.RGB,
            )
        } else {
            copy(
                rgbText = nextRgb,
                activeInput = ThemeColorPickerActiveInput.RGB,
            )
        }
    }

    fun tryResolveHex(): String? {
        return when (activeInput) {
            ThemeColorPickerActiveInput.HEX -> hexText.toResolvedHexOrNull()
            ThemeColorPickerActiveInput.RGB -> rgbText.toResolvedHexOrNull()
            null -> hexText.toResolvedHexOrNull() ?: rgbText.toResolvedHexOrNull()
        }
    }
}

internal fun themeColorPickerTextFields(
    hex: String,
    rgbOverride: ThemeColorPickerRgbText? = null,
    activeInput: ThemeColorPickerActiveInput? = null,
): ThemeColorPickerTextFields {
    val normalizedHex = formatThemeColor(parseThemeColorOrNull(hex) ?: error("Expected valid hex: $hex"))
    val color = parseThemeColorOrNull(normalizedHex) ?: error("Expected valid hex: $normalizedHex")
    val computedRgb = ThemeColorPickerRgbText(
        red = ((color shr 16) and 0xFF).toString().padStart(3, '0'),
        green = ((color shr 8) and 0xFF).toString().padStart(3, '0'),
        blue = (color and 0xFF).toString().padStart(3, '0'),
    )
    return ThemeColorPickerTextFields(
        hexText = normalizedHex.removePrefix("#"),
        rgbText = rgbOverride ?: computedRgb,
        lastValidHex = normalizedHex,
        activeInput = activeInput,
    )
}

internal enum class ThemeColorPickerActiveInput {
    HEX,
    RGB,
}

private fun String.toResolvedHexOrNull(): String? {
    if (length != 6) return null
    return parseThemeColorOrNull("#$this")?.let(::formatThemeColor)
}

private fun ThemeColorPickerRgbText.toResolvedHexOrNull(): String? {
    if (red.length != 3 || green.length != 3 || blue.length != 3) return null
    val resolvedRed = red.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
    val resolvedGreen = green.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
    val resolvedBlue = blue.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
    val color = 0xFF000000L or
        (resolvedRed.toLong() shl 16) or
        (resolvedGreen.toLong() shl 8) or
        resolvedBlue.toLong()
    return formatThemeColor(color)
}

private fun String.sanitizeRgbChannelInput(): String {
    val digitsOnly = filter(Char::isDigit).take(3)
    if (digitsOnly.length < 3) {
        return digitsOnly
    }
    val parsed = digitsOnly.toIntOrNull() ?: return digitsOnly
    return if (parsed > 255) {
        "255"
    } else {
        digitsOnly
    }
}
