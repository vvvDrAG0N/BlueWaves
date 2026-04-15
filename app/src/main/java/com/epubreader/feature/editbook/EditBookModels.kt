package com.epubreader.feature.editbook

import com.epubreader.core.model.BookChapterEdit
import com.epubreader.core.model.BookNewChapterContent
import com.epubreader.core.model.EpubBook
import java.util.Locale
import java.util.UUID

internal enum class EditableChapterSource {
    EXISTING,
    NEW_TEXT,
    IMPORTED_HTML,
}

internal data class EditableChapterItem(
    val id: String,
    val title: String,
    val href: String?,
    val content: BookNewChapterContent?,
    val isPersisted: Boolean,
    val source: EditableChapterSource,
)

internal fun buildEditableChapterItems(book: EpubBook): List<EditableChapterItem> {
    val tocTitlesByHref = book.toc.associate { tocItem ->
        cleanEditableHref(tocItem.href) to stripEditableTocNumbering(tocItem.title)
    }

    return book.spineHrefs.mapIndexed { index, href ->
        EditableChapterItem(
            id = href,
            title = tocTitlesByHref[cleanEditableHref(href)]
                ?.takeIf { it.isNotBlank() }
                ?: fallbackEditableTitle(href, index),
            href = href,
            content = null,
            isPersisted = true,
            source = EditableChapterSource.EXISTING,
        )
    }
}

internal fun buildTextDraftChapter(
    title: String,
    body: String,
): EditableChapterItem {
    return EditableChapterItem(
        id = "draft-${UUID.randomUUID()}",
        title = title.trim(),
        href = null,
        content = BookNewChapterContent.PlainText(body = body.trim()),
        isPersisted = false,
        source = EditableChapterSource.NEW_TEXT,
    )
}

internal fun buildHtmlDraftChapter(
    title: String,
    markup: String,
    fileNameHint: String?,
): EditableChapterItem {
    return EditableChapterItem(
        id = "draft-${UUID.randomUUID()}",
        title = title.trim(),
        href = null,
        content = BookNewChapterContent.HtmlDocument(
            markup = markup,
            fileNameHint = fileNameHint,
        ),
        isPersisted = false,
        source = EditableChapterSource.IMPORTED_HTML,
    )
}

internal fun EditableChapterItem.toChapterEdit(): BookChapterEdit {
    return BookChapterEdit(
        existingHref = href,
        title = title,
        newChapterContent = content,
    )
}

internal fun insertChapterItems(
    currentItems: List<EditableChapterItem>,
    insertPosition: Int,
    insertedItems: List<EditableChapterItem>,
): List<EditableChapterItem> {
    if (insertedItems.isEmpty()) {
        return currentItems
    }

    val boundedIndex = (insertPosition - 1).coerceIn(0, currentItems.size)
    return buildList(currentItems.size + insertedItems.size) {
        addAll(currentItems.subList(0, boundedIndex))
        addAll(insertedItems)
        addAll(currentItems.subList(boundedIndex, currentItems.size))
    }
}

internal fun moveSelectedChapterItems(
    currentItems: List<EditableChapterItem>,
    selectedIds: Set<String>,
    targetPosition: Int,
): List<EditableChapterItem> {
    if (selectedIds.isEmpty()) {
        return currentItems
    }

    val movingItems = currentItems.filter { it.id in selectedIds }
    if (movingItems.isEmpty() || movingItems.size == currentItems.size) {
        return currentItems
    }

    val remainingItems = currentItems.filterNot { it.id in selectedIds }
    val boundedIndex = (targetPosition - 1).coerceIn(0, remainingItems.size)
    return buildList(currentItems.size) {
        addAll(remainingItems.subList(0, boundedIndex))
        addAll(movingItems)
        addAll(remainingItems.subList(boundedIndex, remainingItems.size))
    }
}

internal fun selectChapterRange(
    currentItems: List<EditableChapterItem>,
    startPosition: Int,
    endPosition: Int,
): Set<String> {
    if (currentItems.isEmpty()) {
        return emptySet()
    }

    val lastIndex = currentItems.lastIndex + 1
    val start = startPosition.coerceIn(1, lastIndex)
    val end = endPosition.coerceIn(1, lastIndex)
    val lowerBound = minOf(start, end)
    val upperBound = maxOf(start, end)
    return currentItems
        .subList(lowerBound - 1, upperBound)
        .mapTo(linkedSetOf(), EditableChapterItem::id)
}

internal fun selectOutsideChapterRange(
    currentItems: List<EditableChapterItem>,
    startPosition: Int,
    endPosition: Int,
): Set<String> {
    if (currentItems.isEmpty()) {
        return emptySet()
    }

    val lastIndex = currentItems.lastIndex + 1
    val start = startPosition.coerceIn(1, lastIndex)
    val end = endPosition.coerceIn(1, lastIndex)
    val lowerBound = minOf(start, end)
    val upperBound = maxOf(start, end)
    return currentItems
        .filterIndexed { index, _ ->
            val position = index + 1
            position < lowerBound || position > upperBound
        }
        .mapTo(linkedSetOf(), EditableChapterItem::id)
}

internal fun selectSpecificChapter(
    currentItems: List<EditableChapterItem>,
    position: Int,
): Set<String> {
    if (currentItems.isEmpty()) {
        return emptySet()
    }

    return currentItems
        .getOrNull(position.coerceIn(1, currentItems.size) - 1)
        ?.let { linkedSetOf(it.id) }
        ?: emptySet()
}

internal fun matchesChapterSearch(
    chapter: EditableChapterItem,
    position: Int,
    query: String,
): Boolean {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isBlank()) {
        return true
    }

    val matchesText = chapter.title.contains(normalizedQuery, ignoreCase = true) ||
        chapter.href.orEmpty().contains(normalizedQuery, ignoreCase = true)
    val matchesIndex = normalizedQuery.all(Char::isDigit) && position.toString() == normalizedQuery

    return matchesText || matchesIndex
}

internal fun inferImportedChapterTitle(
    fileNameHint: String?,
    markup: String,
): String {
    val inferred = findFirstTitleTag(markup)
        ?: findFirstHeading(markup)
        ?: fileNameHint
            ?.substringAfterLast('/')
            ?.substringBeforeLast('.')
            ?.replace('_', ' ')
            ?.replace('-', ' ')
            ?.trim()

    return inferred
        ?.replace(Regex("""\s+"""), " ")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: "Imported Chapter"
}

internal fun stripEditableTocNumbering(title: String): String {
    return title.replace(Regex("""^\s*\d+(?:\.\d+)*\.\s*"""), "").trim()
}

internal fun fallbackEditableTitle(
    href: String,
    index: Int,
): String {
    return href.substringAfterLast('/')
        .substringBeforeLast('.')
        .replace('_', ' ')
        .replace('-', ' ')
        .replaceFirstChar { character ->
            if (character.isLowerCase()) character.titlecase(Locale.US) else character.toString()
        }
        .ifBlank { "Chapter ${index + 1}" }
}

internal fun cleanEditableHref(rawHref: String): String {
    return rawHref.substringBefore("#").removePrefix("/").trim()
}

private fun findFirstTitleTag(markup: String): String? {
    val match = Regex("""(?is)<title\b[^>]*>(.*?)</title>""").find(markup) ?: return null
    return stripMarkup(match.groupValues[1])
}

private fun findFirstHeading(markup: String): String? {
    val match = Regex("""(?is)<h[1-6]\b[^>]*>(.*?)</h[1-6]>""").find(markup) ?: return null
    return stripMarkup(match.groupValues[1])
}

private fun stripMarkup(raw: String): String {
    return raw
        .replace(Regex("""(?is)<[^>]+>"""), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .trim()
}
