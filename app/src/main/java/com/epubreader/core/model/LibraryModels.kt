package com.epubreader.core.model

import java.util.UUID

enum class BookFormat {
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
    val rootPath: String,
    val format: BookFormat = BookFormat.EPUB,
    val toc: List<TocItem> = emptyList(),
    val spineHrefs: List<String> = emptyList(),
    val pageCount: Int = 0,
    val dateAdded: Long = System.currentTimeMillis(),
    val lastRead: Long = 0L
) {
    val readingUnitCount: Int
        get() = when (format) {
            BookFormat.EPUB -> spineHrefs.size
            BookFormat.PDF -> pageCount
        }

    val progressUnitLabel: String
        get() = when (format) {
            BookFormat.EPUB -> "ch"
            BookFormat.PDF -> "p"
        }

    val formatLabel: String
        get() = format.name

    val isPdf: Boolean
        get() = format == BookFormat.PDF
}
