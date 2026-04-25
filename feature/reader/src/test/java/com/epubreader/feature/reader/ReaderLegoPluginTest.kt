package com.epubreader.feature.reader

import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderLegoPluginTest {

    @Test
    fun chromeSpec_usesImmersiveSystemBars() {
        assertTrue(ReaderLegoPlugin.chromeSpec.immersiveSystemBars)
    }
}
