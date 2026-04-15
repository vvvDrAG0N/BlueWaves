package com.epubreader.core.model

/**
 * Shared request models for the EPUB-only Edit Book flow.
 * File-system mutation still belongs to the parser layer.
 */
data class BookEditRequest(
    val title: String,
    val author: String,
    val coverAction: BookCoverAction = BookCoverAction.Keep,
    val chapters: List<BookChapterEdit> = emptyList(),
)

sealed interface BookCoverAction {
    data object Keep : BookCoverAction
    data object Remove : BookCoverAction
    data class Replace(val cover: BookCoverUpdate) : BookCoverAction
}

data class BookCoverUpdate(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
)

data class BookChapterEdit(
    val existingHref: String? = null,
    val title: String,
    val newChapterContent: BookNewChapterContent? = null,
)

sealed interface BookNewChapterContent {
    val fileNameHint: String?

    data class PlainText(
        val body: String,
        override val fileNameHint: String? = null,
    ) : BookNewChapterContent

    data class HtmlDocument(
        val markup: String,
        override val fileNameHint: String? = null,
    ) : BookNewChapterContent
}
