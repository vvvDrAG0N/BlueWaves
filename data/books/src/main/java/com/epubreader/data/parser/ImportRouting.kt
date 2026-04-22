package com.epubreader.data.parser

import android.net.Uri
import com.epubreader.core.model.BookFormat
import java.util.Locale

const val EPUB_MIME_TYPE = "application/epub+zip"
// Retained for deprecated PDF internals; the app shell no longer exposes PDF import.
const val PDF_MIME_TYPE = "application/pdf"
const val ZIP_MIME_TYPE = "application/zip"
const val ZIP_COMPRESSED_MIME_TYPE = "application/x-zip-compressed"
const val OCTET_STREAM_MIME_TYPE = "application/octet-stream"

data class ImportRequest(
    val bookId: String,
    val uri: Uri,
    val format: BookFormat,
    val archiveEntryPath: String? = null,
    val displayName: String? = null,
)

sealed interface ImportInspectionResult {
    data class Ready(val request: ImportRequest) : ImportInspectionResult
    data class Rejected(val reason: ImportFailureReason) : ImportInspectionResult
}

enum class ImportFailureReason(val userMessage: String) {
    UnsupportedFileType("This file type is not supported. Import an EPUB or a ZIP archive containing one supported EPUB."),
    EmptyArchive("This ZIP archive is empty."),
    UnsupportedArchive("This ZIP archive does not contain a supported EPUB."),
    AmbiguousArchive("This ZIP archive contains multiple supported books. Import one book at a time."),
    ReadFailed("Couldn't read this file."),
}

internal data class ArchiveImportCandidate(
    val format: BookFormat,
    val entryPath: String,
)

internal sealed interface ArchiveInspectionResult {
    data object DirectEpubContainer : ArchiveInspectionResult
    data class Candidate(val candidate: ArchiveImportCandidate) : ArchiveInspectionResult
    data class Rejected(val reason: ImportFailureReason) : ArchiveInspectionResult
}

internal fun inspectArchiveEntries(
    entryNames: List<String>,
    looksLikeEpubContainer: Boolean,
): ArchiveInspectionResult {
    if (looksLikeEpubContainer) {
        return ArchiveInspectionResult.DirectEpubContainer
    }

    val nonIgnoredEntries = entryNames.filterNot(::isIgnorableArchiveEntry)
    if (nonIgnoredEntries.isEmpty()) {
        return ArchiveInspectionResult.Rejected(ImportFailureReason.EmptyArchive)
    }

    val supportedEntries = nonIgnoredEntries.mapNotNull(::archiveCandidateFor)
    return when {
        supportedEntries.isEmpty() -> ArchiveInspectionResult.Rejected(ImportFailureReason.UnsupportedArchive)
        supportedEntries.size > 1 -> ArchiveInspectionResult.Rejected(ImportFailureReason.AmbiguousArchive)
        else -> ArchiveInspectionResult.Candidate(supportedEntries.single())
    }
}

internal fun inferDirectImportFormat(
    fileName: String?,
    mimeType: String?,
    headerBytes: ByteArray,
    looksLikeEpubContainer: Boolean,
): BookFormat? {
    val normalizedName = fileName?.trim()?.lowercase(Locale.US).orEmpty()
    val normalizedMime = mimeType?.trim()?.lowercase(Locale.US).orEmpty()

    if (looksLikeEpubContainer) {
        return BookFormat.EPUB
    }

    if (isZipSignature(headerBytes) && (normalizedName.endsWith(".epub") || normalizedMime == EPUB_MIME_TYPE)) {
        return BookFormat.EPUB
    }

    if (isPdfSignature(headerBytes) || normalizedMime == PDF_MIME_TYPE) {
        return BookFormat.PDF
    }

    return null
}

internal fun deriveTitleFromName(rawName: String?, fallback: String): String {
    val baseName = rawName
        ?.substringAfterLast('/')
        ?.substringBeforeLast('.', missingDelimiterValue = rawName)
        ?.replace('_', ' ')
        ?.replace('-', ' ')
        ?.trim()
        .orEmpty()

    if (baseName.isBlank()) {
        return fallback
    }

    return baseName.replaceFirstChar { char ->
        if (char.isLowerCase()) {
            char.titlecase(Locale.getDefault())
        } else {
            char.toString()
        }
    }
}

internal fun isZipSignature(headerBytes: ByteArray): Boolean {
    return headerBytes.size >= 2 &&
        headerBytes[0] == 'P'.code.toByte() &&
        headerBytes[1] == 'K'.code.toByte()
}

internal fun isPdfSignature(headerBytes: ByteArray): Boolean {
    return headerBytes.size >= 4 &&
        headerBytes[0] == '%'.code.toByte() &&
        headerBytes[1] == 'P'.code.toByte() &&
        headerBytes[2] == 'D'.code.toByte() &&
        headerBytes[3] == 'F'.code.toByte()
}

private fun archiveCandidateFor(entryName: String): ArchiveImportCandidate? {
    val normalized = entryName.lowercase(Locale.US)
    return when {
        normalized.endsWith(".epub") -> ArchiveImportCandidate(BookFormat.EPUB, entryName)
        normalized.endsWith(".pdf") -> ArchiveImportCandidate(BookFormat.PDF, entryName)
        else -> null
    }
}

private fun isIgnorableArchiveEntry(entryName: String): Boolean {
    val normalized = entryName.lowercase(Locale.US)
    return normalized.isBlank() ||
        normalized.endsWith("/") ||
        normalized.startsWith("__macosx/") ||
        normalized.substringAfterLast('/').startsWith("._")
}
