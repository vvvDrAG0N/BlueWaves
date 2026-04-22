package com.epubreader.data.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class EpubParserChapterTest {

    @Test
    fun normalizePath_resolvesSingleParentSegment() {
        assertEquals(
            "OEBPS/ch1.html",
            normalizePath("OEBPS/images/../ch1.html"),
        )
    }

    @Test
    fun normalizePath_resolvesNestedParentSegments() {
        assertEquals(
            "OPS/Images/cover.jpg",
            normalizePath("OPS/Text/Sections/../../Images/cover.jpg"),
        )
    }

    @Test
    fun normalizePath_keepsAlreadyNormalizedPathsUnchanged() {
        assertEquals(
            "OEBPS/Text/ch1.xhtml",
            normalizePath("OEBPS/Text/ch1.xhtml"),
        )
    }

    @Test
    fun normalizePath_ignoresLeadingParentSegmentsWithoutCrashing() {
        assertEquals(
            "Images/pic.jpg",
            normalizePath("../../Images/pic.jpg"),
        )
    }

    @Test
    fun normalizePath_ignoresCurrentDirectorySegments() {
        assertEquals(
            "OEBPS/Text/ch1.xhtml",
            normalizePath("./OEBPS/./Text/ch1.xhtml"),
        )
    }
}
