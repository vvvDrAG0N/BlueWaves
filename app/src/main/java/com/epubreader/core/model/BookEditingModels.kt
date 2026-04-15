package com.epubreader.core.model

/**
 * Shared request models for the EPUB-only Edit Book flow.
 * File-system mutation still belongs to the parser layer.
 */
data class BookEditRequest(
    val title: String,
    val author: String,
    val coverUpdate: BookCoverUpdate? = null,
    val deletedChapterHrefs: Set<String> = emptySet(),
    val addedChapters: List<BookChapterAddition> = emptyList(),
)

data class BookCoverUpdate(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
)

data class BookChapterAddition(
    val title: String,
    val body: String,
)
