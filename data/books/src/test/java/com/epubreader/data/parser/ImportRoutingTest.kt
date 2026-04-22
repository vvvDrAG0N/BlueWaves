package com.epubreader.data.parser

import com.epubreader.core.model.BookFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportRoutingTest {

    @Test
    fun inferDirectImportFormat_acceptsEpubContainers() {
        val result = inferDirectImportFormat(
            fileName = "book.epub",
            mimeType = EPUB_MIME_TYPE,
            headerBytes = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4),
            looksLikeEpubContainer = false,
        )

        assertEquals(BookFormat.EPUB, result)
    }

    @Test
    fun inferDirectImportFormat_acceptsPdfFiles() {
        val result = inferDirectImportFormat(
            fileName = "paper.pdf",
            mimeType = PDF_MIME_TYPE,
            headerBytes = "%PDF-1.7".toByteArray(),
            looksLikeEpubContainer = false,
        )

        assertEquals(BookFormat.PDF, result)
    }

    @Test
    fun inspectArchiveEntries_acceptsSinglePdfCandidate() {
        val result = inspectArchiveEntries(
            entryNames = listOf("docs/paper.pdf"),
            looksLikeEpubContainer = false,
        )

        assertEquals(
            ArchiveInspectionResult.Candidate(
                ArchiveImportCandidate(
                    format = BookFormat.PDF,
                    entryPath = "docs/paper.pdf",
                ),
            ),
            result,
        )
    }

    @Test
    fun inspectArchiveEntries_rejectsArchivesWithoutRealEntries() {
        val result = inspectArchiveEntries(
            entryNames = listOf("__MACOSX/", "folder/", "._book.epub"),
            looksLikeEpubContainer = false,
        )

        assertEquals(
            ArchiveInspectionResult.Rejected(ImportFailureReason.EmptyArchive),
            result,
        )
    }

    @Test
    fun inspectArchiveEntries_rejectsAmbiguousMultipleEpubs() {
        val result = inspectArchiveEntries(
            entryNames = listOf("one.epub", "nested/two.epub"),
            looksLikeEpubContainer = false,
        )

        assertEquals(
            ArchiveInspectionResult.Rejected(ImportFailureReason.AmbiguousArchive),
            result,
        )
    }

    @Test
    fun inspectArchiveEntries_rejectsMixedSupportedBooksAsAmbiguous() {
        val result = inspectArchiveEntries(
            entryNames = listOf("one.epub", "docs/paper.pdf"),
            looksLikeEpubContainer = false,
        )

        assertEquals(
            ArchiveInspectionResult.Rejected(ImportFailureReason.AmbiguousArchive),
            result,
        )
    }

    @Test
    fun inspectArchiveEntries_acceptsSingleEpubCandidate() {
        val result = inspectArchiveEntries(
            entryNames = listOf("nested/book.epub", "cover.jpg"),
            looksLikeEpubContainer = false,
        )

        assertEquals(
            ArchiveInspectionResult.Candidate(
                ArchiveImportCandidate(
                    format = BookFormat.EPUB,
                    entryPath = "nested/book.epub",
                ),
            ),
            result,
        )
    }

    @Test
    fun inspectArchiveEntries_rejectsUnsupportedArchivePayloads() {
        val result = inspectArchiveEntries(
            entryNames = listOf("cover.jpg", "notes/readme.txt"),
            looksLikeEpubContainer = false,
        )

        assertEquals(
            ArchiveInspectionResult.Rejected(ImportFailureReason.UnsupportedArchive),
            result,
        )
    }
}
