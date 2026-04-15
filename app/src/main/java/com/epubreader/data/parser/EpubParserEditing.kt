package com.epubreader.data.parser

import com.epubreader.core.model.BookChapterEdit
import com.epubreader.core.model.BookCoverAction
import com.epubreader.core.model.BookCoverUpdate
import com.epubreader.core.model.BookEditRequest
import com.epubreader.core.model.BookNewChapterContent
import nl.siegmann.epublib.domain.Author
import nl.siegmann.epublib.domain.Book
import nl.siegmann.epublib.domain.GuideReference
import nl.siegmann.epublib.domain.Resource
import nl.siegmann.epublib.domain.SpineReference
import nl.siegmann.epublib.domain.TOCReference
import nl.siegmann.epublib.epub.EpubReader
import nl.siegmann.epublib.epub.EpubWriter
import java.io.File
import java.util.Locale

private const val EditedBookAssetDir = "bluewaves"
private const val EditedBookChapterPrefix = "$EditedBookAssetDir/added-chapter-"
private const val EditedBookCustomCoverPrefix = "$EditedBookAssetDir/custom-cover."

internal fun editStoredEpubBook(
    bookFolder: File,
    request: BookEditRequest,
): com.epubreader.core.model.EpubBook? {
    val sourceFile = activeEpubFile(bookFolder, com.epubreader.core.model.BookFormat.EPUB)
    if (!sourceFile.exists()) {
        return null
    }

    val normalizedRequest = request.normalized() ?: return null
    val stagedFile = File(bookFolder, "$EPUB_ARCHIVE_FILE_NAME.editing")
    stagedFile.delete()
    val metadataSeed = prepareMetadataSeedForBookEdit(
        existingMetadata = loadBookMetadata(bookFolder),
        bookFolder = bookFolder,
        coverAction = normalizedRequest.coverAction,
    )

    return try {
        val book = sourceFile.inputStream().use { input ->
            EpubReader().readEpub(input)
        }

        updateBookMetadata(book, normalizedRequest)
        if (!applyChapterEdits(book, normalizedRequest.chapters)) {
            return null
        }
        applyCoverEdit(book, normalizedRequest.coverAction)

        stagedFile.outputStream().use { output ->
            EpubWriter().write(book, output)
        }
        replaceFileAtomically(stagedFile, sourceFile)
        rebuildBookMetadata(bookFolder, metadataSeed)
    } catch (_: Exception) {
        stagedFile.takeIf(File::exists)?.delete()
        null
    }
}

private fun updateBookMetadata(
    book: Book,
    request: BookEditRequest,
) {
    book.metadata.titles = mutableListOf(request.title)
    book.metadata.authors = mutableListOf(request.author.toEpubAuthor())
}

private fun applyChapterEdits(
    book: Book,
    chapters: List<BookChapterEdit>,
): Boolean {
    if (chapters.isEmpty()) {
        return false
    }

    val originalSpineResourcesByHref = linkedMapOf<String, Resource>()
    book.spine.spineReferences.forEach { reference ->
        val resource = reference.resource ?: return@forEach
        val href = cleanHref(resource.href)
        if (href.isNotBlank()) {
            originalSpineResourcesByHref[href] = resource
        }
    }

    val finalSpineReferences = mutableListOf<SpineReference>()
    val finalTocReferences = mutableListOf<TOCReference>()
    val retainedExistingHrefs = linkedSetOf<String>()
    var nextChapterNumber = nextAddedChapterNumber(book)

    chapters.forEachIndexed { index, chapter ->
        val title = chapter.title.trim().ifBlank {
            chapter.existingHref
                ?.let(::cleanHref)
                ?.takeIf(String::isNotBlank)
                ?.let { href -> fallbackChapterTitle(href, index) }
                ?: "Chapter ${index + 1}"
        }
        val resource = when (val existingHref = chapter.existingHref?.let(::cleanHref)) {
            null, "" -> {
                val content = chapter.newChapterContent ?: return false
                val href = "$EditedBookChapterPrefix${nextChapterNumber.toString().padStart(4, '0')}.xhtml"
                nextChapterNumber++
                Resource(
                    buildNewChapterDocument(title, content),
                    href,
                ).apply {
                    this.title = title
                }.also(book.resources::add)
            }

            else -> {
                val existingResource = originalSpineResourcesByHref[existingHref]
                    ?: book.resources.getByHref(existingHref)
                    ?: return false
                retainedExistingHrefs += existingHref
                existingResource
            }
        }

        finalSpineReferences += SpineReference(resource)
        finalTocReferences += TOCReference(title, resource)
    }

    if (finalSpineReferences.isEmpty()) {
        return false
    }

    val removedHrefs = originalSpineResourcesByHref.keys - retainedExistingHrefs
    removedHrefs.forEach(book.resources::remove)

    book.spine.spineReferences = finalSpineReferences
    book.tableOfContents.tocReferences = finalTocReferences
    return true
}

private fun applyCoverEdit(
    book: Book,
    coverAction: BookCoverAction,
) {
    when (coverAction) {
        BookCoverAction.Keep -> Unit
        BookCoverAction.Remove -> removeCover(book)
        is BookCoverAction.Replace -> replaceCover(book, coverAction.cover)
    }
}

private fun removeCover(book: Book) {
    removeCustomCoverResources(book)
    clearCustomGuideReferences(book)
    setBookCoverImageDirect(book, null)
}

private fun replaceCover(
    book: Book,
    coverUpdate: BookCoverUpdate,
) {
    val extension = coverUpdate.extensionOrNull()
        ?: throw IllegalArgumentException("Unsupported cover format")
    val coverHref = "$EditedBookCustomCoverPrefix$extension"

    removeCustomCoverResources(book)

    val coverImage = Resource(coverUpdate.bytes, coverHref).apply {
        title = "Cover"
    }
    book.resources.add(coverImage)
    book.setCoverImage(coverImage)
}

private fun removeCustomCoverResources(book: Book) {
    book.resources.allHrefs
        .filter(::isCustomCoverHref)
        .toList()
        .forEach(book.resources::remove)
}

private fun clearCustomGuideReferences(book: Book) {
    val remainingReferences = book.guide.references.filterNot { reference ->
        isCustomCoverHref(reference.resource?.href.orEmpty()) ||
            reference.type.equals(GuideReference.COVER, ignoreCase = true) &&
            isCustomCoverHref(reference.completeHref.orEmpty())
    }
    book.guide.references = remainingReferences
}

private fun setBookCoverImageDirect(
    book: Book,
    resource: Resource?,
) {
    val field = Book::class.java.getDeclaredField("coverImage")
    field.isAccessible = true
    field.set(book, resource)
}

private fun looksLikeCoverResource(resource: Resource): Boolean {
    val mediaTypeName = resource.mediaType?.name.orEmpty()
    if (!mediaTypeName.startsWith("image/", ignoreCase = true)) {
        return false
    }

    val normalizedHref = cleanHref(resource.href)
    val normalizedTitle = resource.title.orEmpty()
    return normalizedHref.contains("cover", ignoreCase = true) ||
        normalizedTitle.contains("cover", ignoreCase = true)
}

private fun buildNewChapterDocument(
    title: String,
    content: BookNewChapterContent,
): ByteArray {
    val bodyMarkup = when (content) {
        is BookNewChapterContent.PlainText -> buildPlainTextBodyMarkup(title, content.body)
        is BookNewChapterContent.HtmlDocument -> buildImportedHtmlBodyMarkup(content.markup)
    }

    return """<?xml version="1.0" encoding="UTF-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml">
          <head>
            <title>${escapeXml(title)}</title>
          </head>
          <body>
            $bodyMarkup
          </body>
        </html>
    """.trimIndent().toByteArray(Charsets.UTF_8)
}

private fun buildPlainTextBodyMarkup(
    title: String,
    body: String,
): String {
    val escapedTitle = escapeXml(title)
    val paragraphs = body
        .replace("\r\n", "\n")
        .split(Regex("""\n\s*\n"""))
        .mapNotNull { block ->
            val lines = block
                .lines()
                .map(String::trim)
                .filter(String::isNotBlank)
            if (lines.isEmpty()) {
                null
            } else {
                lines.joinToString("<br/>") { escapeXml(it) }
            }
        }

    return buildString {
        append("<h1>$escapedTitle</h1>")
        if (paragraphs.isEmpty()) {
            append("<p>$escapedTitle</p>")
        } else {
            paragraphs.forEach { paragraph ->
                append("<p>$paragraph</p>")
            }
        }
    }
}

private fun buildImportedHtmlBodyMarkup(markup: String): String {
    var normalizedMarkup = markup
        .removePrefix("\uFEFF")
        .replace(Regex("""(?is)<script\b.*?</script>"""), "")
        .trim()

    val bodyMatch = Regex("""(?is)<body\b[^>]*>(.*?)</body>""").find(normalizedMarkup)
    normalizedMarkup = if (bodyMatch != null) {
        bodyMatch.groupValues[1].trim()
    } else if (Regex("""(?is)<html\b""").containsMatchIn(normalizedMarkup)) {
        normalizedMarkup
            .replace(Regex("""(?is)<\?xml.*?\?>"""), "")
            .replace(Regex("""(?is)<!DOCTYPE.*?>"""), "")
            .replace(Regex("""(?is)<head\b.*?</head>"""), "")
            .replace(Regex("""(?is)</?html\b[^>]*>"""), "")
            .replace(Regex("""(?is)</?body\b[^>]*>"""), "")
            .trim()
    } else {
        normalizedMarkup
    }

    if (!Regex("""(?is)<[a-z][^>]*>""").containsMatchIn(normalizedMarkup)) {
        return buildPlainTextBodyMarkup(
            title = "Imported Chapter",
            body = normalizedMarkup,
        )
    }

    return normalizedMarkup
}

private fun BookEditRequest.normalized(): BookEditRequest? {
    val normalizedTitle = title.trim().ifBlank { return null }
    val normalizedAuthor = author.trim().ifBlank { "Unknown Author" }
    val normalizedChapters = chapters.mapIndexedNotNull { index, chapter ->
        val existingHref = chapter.existingHref
            ?.let(::cleanHref)
            ?.takeIf(String::isNotBlank)
        val normalizedContent = chapter.newChapterContent?.normalized()
        if (existingHref == null && normalizedContent == null) {
            return@mapIndexedNotNull null
        }

        val chapterTitle = chapter.title.trim().ifBlank {
            when {
                existingHref != null -> fallbackChapterTitle(existingHref, index)
                normalizedContent != null -> inferNewChapterTitle(normalizedContent, index)
                else -> "Chapter ${index + 1}"
            }
        }

        BookChapterEdit(
            existingHref = existingHref,
            title = chapterTitle,
            newChapterContent = normalizedContent,
        )
    }
    if (normalizedChapters.isEmpty()) {
        return null
    }

    val normalizedCoverAction = when (val action = coverAction) {
        BookCoverAction.Keep -> BookCoverAction.Keep
        BookCoverAction.Remove -> BookCoverAction.Remove
        is BookCoverAction.Replace -> {
            if (action.cover.extensionOrNull() == null) {
                return null
            }
            action
        }
    }

    return copy(
        title = normalizedTitle,
        author = normalizedAuthor,
        coverAction = normalizedCoverAction,
        chapters = normalizedChapters,
    )
}

private fun BookNewChapterContent.normalized(): BookNewChapterContent? {
    return when (this) {
        is BookNewChapterContent.PlainText -> copy(
            body = body.trim(),
            fileNameHint = fileNameHint?.trim()?.takeIf(String::isNotBlank),
        )

        is BookNewChapterContent.HtmlDocument -> {
            val normalizedMarkup = markup.removePrefix("\uFEFF").trim()
            if (normalizedMarkup.isBlank()) {
                null
            } else {
                copy(
                    markup = normalizedMarkup,
                    fileNameHint = fileNameHint?.trim()?.takeIf(String::isNotBlank),
                )
            }
        }
    }
}

private fun inferNewChapterTitle(
    content: BookNewChapterContent,
    index: Int,
): String {
    val candidate = when (content) {
        is BookNewChapterContent.PlainText -> content.fileNameHint
        is BookNewChapterContent.HtmlDocument -> {
            Regex("""(?is)<title\b[^>]*>(.*?)</title>""")
                .find(content.markup)
                ?.groupValues
                ?.getOrNull(1)
                ?.let(::stripMarkup)
                ?: Regex("""(?is)<h[1-6]\b[^>]*>(.*?)</h[1-6]>""")
                    .find(content.markup)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.let(::stripMarkup)
                ?: content.fileNameHint
        }
    }

    return candidate
        ?.substringAfterLast('/')
        ?.substringBeforeLast('.')
        ?.replace('_', ' ')
        ?.replace('-', ' ')
        ?.replace(Regex("""\s+"""), " ")
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: "Chapter ${index + 1}"
}

private fun BookCoverUpdate.extensionOrNull(): String? {
    val normalizedMime = mimeType.trim().lowercase(Locale.US)
    if (normalizedMime.contains("png")) return "png"
    if (normalizedMime.contains("jpeg") || normalizedMime.contains("jpg")) return "jpg"
    if (normalizedMime.contains("gif")) return "gif"
    if (normalizedMime.contains("webp")) return "webp"

    return fileName.substringAfterLast('.', "")
        .lowercase(Locale.US)
        .takeIf { extension ->
            extension in setOf("png", "jpg", "jpeg", "gif", "webp")
        }
        ?.let { extension ->
            if (extension == "jpeg") "jpg" else extension
        }
}

private fun String.toEpubAuthor(): Author {
    val parts = trim()
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)
    return when (parts.size) {
        0 -> Author("", "Unknown Author")
        1 -> Author("", parts.first())
        else -> Author(
            parts.dropLast(1).joinToString(" "),
            parts.last(),
        )
    }
}

private fun cleanHref(rawHref: String?): String {
    return rawHref
        .orEmpty()
        .substringBefore("#")
        .removePrefix("/")
        .trim()
}

private fun isCustomCoverHref(rawHref: String): Boolean {
    return cleanHref(rawHref).startsWith(EditedBookCustomCoverPrefix)
}

private fun prepareMetadataSeedForBookEdit(
    existingMetadata: com.epubreader.core.model.EpubBook?,
    bookFolder: File,
    coverAction: BookCoverAction,
): com.epubreader.core.model.EpubBook? {
    existingMetadata ?: return null

    val originalCoverPath = existingMetadata.originalCoverPath
        ?: existingMetadata.coverPath
    val currentCoverPath = existingMetadata.currentCoverPath

    return when (coverAction) {
        BookCoverAction.Keep -> existingMetadata
        BookCoverAction.Remove -> {
            val promotedOriginalPath = originalCoverPath
                ?: currentCoverPath?.let { currentPath ->
                    val currentFile = File(currentPath)
                    if (currentFile.exists()) {
                        val originalFile = File(bookFolder, EPUB_ORIGINAL_COVER_FILE_NAME)
                        if (currentFile.absolutePath != originalFile.absolutePath) {
                            currentFile.inputStream().use { input ->
                                originalFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                        originalFile.absolutePath
                    } else {
                        null
                    }
                }

            existingMetadata.copy(
                coverPath = promotedOriginalPath,
                originalCoverPath = promotedOriginalPath,
                currentCoverPath = null,
            )
        }

        is BookCoverAction.Replace -> existingMetadata.copy(
            coverPath = File(bookFolder, EPUB_CURRENT_COVER_FILE_NAME).absolutePath,
            originalCoverPath = originalCoverPath ?: currentCoverPath ?: existingMetadata.coverPath,
            currentCoverPath = File(bookFolder, EPUB_CURRENT_COVER_FILE_NAME).absolutePath,
        )
    }
}

private fun fallbackChapterTitle(
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

private fun nextAddedChapterNumber(book: Book): Int {
    val pattern = Regex("""^${Regex.escape(EditedBookChapterPrefix)}(\d{4})\.xhtml$""")
    val currentMax = book.resources.allHrefs
        .mapNotNull { href ->
            pattern.matchEntire(cleanHref(href))
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
        }
        .maxOrNull()
        ?: 0
    return currentMax + 1
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

private fun escapeXml(raw: String): String {
    return raw
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
