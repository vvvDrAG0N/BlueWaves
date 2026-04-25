package com.epubreader.feature.pdf.legacy

import org.junit.Assert.assertTrue
import org.junit.Test

class PdfLegacyLegoPluginTest {

    @Test
    fun chromeSpec_usesImmersiveSystemBars() {
        assertTrue(PdfLegacyLegoPlugin.chromeSpec.immersiveSystemBars)
    }
}
