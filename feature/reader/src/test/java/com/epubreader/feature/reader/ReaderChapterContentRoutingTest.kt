package com.epubreader.feature.reader

import com.epubreader.core.model.ReaderContentEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderChapterContentRoutingTest {

    @Test
    fun resolveReaderChapterContentDelegate_routesLegacyEngineToLegacyDelegate() {
        assertEquals(
            ReaderChapterContentDelegate.Legacy,
            resolveReaderChapterContentDelegate(ReaderContentEngine.LEGACY),
        )
    }

    @Test
    fun resolveReaderChapterContentDelegate_routesComposeLazyImprovedEngineToComposeDelegate() {
        assertEquals(
            ReaderChapterContentDelegate.ComposeLazyImproved,
            resolveReaderChapterContentDelegate(ReaderContentEngine.COMPOSE_LAZY_IMPROVED),
        )
    }

    @Test
    fun resolveReaderChapterContentDelegate_routesTextViewEngineToTextViewDelegate() {
        assertEquals(
            ReaderChapterContentDelegate.TextView,
            resolveReaderChapterContentDelegate(ReaderContentEngine.TEXT_VIEW),
        )
    }
}
