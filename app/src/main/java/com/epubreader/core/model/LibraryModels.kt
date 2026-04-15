package com.epubreader.core.model

import java.util.UUID

enum class BookFormat {
    EPUB,
    PDF,
}

enum class ConversionStatus {
    NONE,
    QUEUED,
    RUNNING,
    READY,
    FAILED,
}

enum class BookRepresentation {
    EPUB,
    PDF,
}

/**
 * Data class for TOC navigation.
 * href usually points to the XHTML file within the EPUB container.
 */
data class TocItem(
    val title: String,
    val href: String
)

/**
 * Sealed class representing a single element of content in a chapter.
 * Used for heterogeneous rendering in the reader LazyColumn.
 */
sealed class ChapterElement {
    abstract val id: String

    data class Text(
        val content: String,
        val type: String = "p",
        override val id: String = UUID.randomUUID().toString()
    ) : ChapterElement()

    data class Image(
        val data: ByteArray,
        override val id: String = UUID.randomUUID().toString()
    ) : ChapterElement() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Image) return false
            return id == other.id && data.contentEquals(other.data)
        }

        override fun hashCode(): Int {
            var result = id.hashCode()
            result = 31 * result + data.contentHashCode()
            return result
        }
    }
}

/**
 * High-level model for a book in the library.
 * bookId is an MD5 hash of (URI + FileSize). Changing this orphans progress.
 */
data class EpubBook(
    val id: String,
    val title: String,
    val author: String,
    val coverPath: String?,
    val originalCoverPath: String? = coverPath,
    val currentCoverPath: String? = coverPath,
    val rootPath: String,
    val format: BookFormat = BookFormat.EPUB,
    val sourceFormat: BookFormat = format,
    val conversionStatus: ConversionStatus = ConversionStatus.NONE,
    val hasPdfFallback: Boolean = false,
    val conversionCompletedPages: Int = 0,
    val conversionTotalPages: Int = 0,
    val toc: List<TocItem> = emptyList(),
    val spineHrefs: List<String> = emptyList(),
    val pageCount: Int = 0,
    val dateAdded: Long = System.currentTimeMillis(),
    val lastRead: Long = 0L
) {
    val activeRepresentation: BookRepresentation
        get() = if (format == BookFormat.PDF) BookRepresentation.PDF else BookRepresentation.EPUB

    val sourceRepresentation: BookRepresentation
        get() = if (sourceFormat == BookFormat.PDF) BookRepresentation.PDF else BookRepresentation.EPUB

    val isConvertedPdf: Boolean
        get() = sourceFormat == BookFormat.PDF && format == BookFormat.EPUB

    val canOpenOriginalPdf: Boolean
        get() = sourceFormat == BookFormat.PDF && hasPdfFallback

    val canOpenGeneratedEpub: Boolean
        get() = sourceFormat == BookFormat.PDF && conversionStatus == ConversionStatus.READY

    val isPdfConversionInFlight: Boolean
        get() = sourceFormat == BookFormat.PDF && (
            conversionStatus == ConversionStatus.QUEUED || conversionStatus == ConversionStatus.RUNNING
        )

    val canRetryPdfConversion: Boolean
        get() = sourceFormat == BookFormat.PDF && (
            conversionStatus == ConversionStatus.NONE || conversionStatus == ConversionStatus.FAILED
        )

    val readingUnitCount: Int
        get() = when (activeRepresentation) {
            BookRepresentation.EPUB -> spineHrefs.size
            BookRepresentation.PDF -> pageCount
        }

    val progressUnitLabel: String
        get() = when {
            activeRepresentation == BookRepresentation.PDF -> "p"
            sourceFormat == BookFormat.PDF -> "sec"
            else -> "ch"
        }

    val formatLabel: String
        get() = when {
            sourceFormat == BookFormat.PDF && format == BookFormat.EPUB -> "PDF"
            else -> format.name
        }

    val navigationUnitLabel: String
        get() = when {
            activeRepresentation == BookRepresentation.PDF -> "Page"
            sourceFormat == BookFormat.PDF -> "Section"
            else -> "Chapter"
        }

    val isPdf: Boolean
        get() = format == BookFormat.PDF

    fun displayCoverPath(allowBlankCovers: Boolean): String? {
        return when {
            !currentCoverPath.isNullOrBlank() -> currentCoverPath
            allowBlankCovers -> null
            !originalCoverPath.isNullOrBlank() -> originalCoverPath
            else -> coverPath
        }
    }
}
