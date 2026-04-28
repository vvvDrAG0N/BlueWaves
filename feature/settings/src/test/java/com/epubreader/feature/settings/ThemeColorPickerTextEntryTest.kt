package com.epubreader.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThemeColorPickerTextEntryTest {

    @Test
    fun hexInput_partial_keepsLastValidHexUntilSixDigits() {
        val initial = themeColorPickerTextFields("#4F46E5")
        val partial = initial.withHexInput("12AB")

        assertEquals("12AB", partial.hexText)
        assertEquals("#4F46E5", partial.lastValidHex)
        assertNull(partial.tryResolveHex())
        assertEquals("079", partial.rgbText.red)
        assertEquals("070", partial.rgbText.green)
        assertEquals("229", partial.rgbText.blue)
    }

    @Test
    fun hexInput_complete_updatesRgbAndLastValidHex() {
        val updated = themeColorPickerTextFields("#000000").withHexInput("12ab34")

        assertEquals("12AB34", updated.hexText)
        assertEquals("#12AB34", updated.lastValidHex)
        assertEquals("#12AB34", updated.tryResolveHex())
        assertEquals("018", updated.rgbText.red)
        assertEquals("171", updated.rgbText.green)
        assertEquals("052", updated.rgbText.blue)
    }

    @Test
    fun rgbInput_complete_updatesHexOnlyWhenAllChannelsAreValid() {
        val initial = themeColorPickerTextFields("#112233")
        val partial = initial.withRgbInput(red = "9", green = "", blue = "255")

        assertEquals("#112233", partial.lastValidHex)
        assertNull(partial.tryResolveHex())

        val complete = partial.withRgbInput(red = "009", green = "128", blue = "255")

        assertEquals("0980FF", complete.hexText)
        assertEquals("#0980FF", complete.lastValidHex)
        assertEquals("#0980FF", complete.tryResolveHex())
        assertEquals("009", complete.rgbText.red)
        assertEquals("128", complete.rgbText.green)
        assertEquals("255", complete.rgbText.blue)
    }
}
