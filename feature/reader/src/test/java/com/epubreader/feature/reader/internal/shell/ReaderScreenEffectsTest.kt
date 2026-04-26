package com.epubreader.feature.reader.internal.shell

import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderScreenEffectsTest {

    @Test
    fun clearReaderImmersiveSystemUiFlags_preservesLayoutFlags() {
        val layoutFlags =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        val immersiveFlags =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        val cleared = clearReaderImmersiveSystemUiFlags(layoutFlags or immersiveFlags)

        assertEquals(layoutFlags, cleared)
    }

    @Test
    fun clearReaderImmersiveSystemUiFlags_leavesNonImmersiveFlagsUntouched() {
        val nonImmersiveFlags =
            View.SYSTEM_UI_FLAG_LOW_PROFILE or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        val cleared = clearReaderImmersiveSystemUiFlags(nonImmersiveFlags)

        assertEquals(nonImmersiveFlags, cleared)
    }
}
