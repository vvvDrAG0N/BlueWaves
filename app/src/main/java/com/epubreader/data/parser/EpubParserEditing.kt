package com.epubreader.data.parser

import com.epubreader.core.model.BookChapterAddition
import com.epubreader.core.model.BookCoverUpdate
import com.epubreader.core.model.BookEditRequest
import nl.siegmann.epublib.domain.Author
import nl.siegmann.epublib.domain.Book
import nl.siegmann.epublib.domain.Resource
import nl.siegmann.epublib.domain.SpineReference
import nl.siegmann.epublib.domain.TOCReference
import nl.siegmann.epublib.epub.EpubReader
import nl.siegmann.epublib.epub.EpubWriter
import java.io.File
import java.util.Locale

private const val EditedBookAssetDir = "bluewaves"
private const val EditedBookChapterPrefix = "$EditedBookAssetDir/added-chapter-"

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

    return try {
        val book = sourceFile.inputStream().use { input ->
            EpubReader().readEpub(input)
        }

        updateBookMetadata(book, normalizedRequest)
        if (!applyChapterEdits(book, normalizedRequest)) {
            return null
        }
        applyCoverEdit(book, normalizedRequest.coverUpdate)

        stagedFile.outputStream().use { output ->
            EpubWriter().write(book, output)
        }
        replaceFileAtomically(stagedFile, sourceFile)
        rebuildBookMetadata(bookFolder)
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
    request: BookEditRequest,
): Boolean {
    val deletedChapterHrefs = request.deletedChapterHrefs
        .map(::cleanHref)
        .filter(String::isNotBlank)
        .toSet()
    val chapterTitlesByHref = buildChapterTitleMap(book)
    val keptSpineReferences = book.spine.spineReferences
        .filter { reference ->
            val href = cleanHref(reference.resource?.href)
            href.isNotBlank() && href !in deletedChapterHrefs
        }
        .toMutableList()

    deletedChapterHrefs.forEach { href ->
        book.resources.remove(href)
        chapterTitlesByHref.remove(href)
    }

    val nextChapterNumber = nextAddedChapterNumber(book)
    val addedSpineReferences = request.addedChapters.mapIndexed { index, chapter ->
        val href = "$EditedBookChapterPrefix${(nextChapterNumber + index).toString().padStart(4, '0')}.xhtml"
        val resource = Resource(
            buildAddedChapterDocument(
                title = chapter.title,
                body = chapter.body,
            ),
            href,
        ).apply {
            title = chapter.title
        }
        book.resources.add(resource)
        chapterTitlesByHref[href] = chapter.title
        SpineReference(resource)
    }

    val finalSpineReferences = keptSpineReferences + addedSpineReferences
    if (finalSpineReferences.isEmpty()) {
        return false
    }

    book.spine.spineReferences = finalSpineReferences
    book.tableOfContents.tocReferences = finalSpineReferences.mapIndexed { index, reference ->
        val href = cleanHref(reference.resource?.href)
        val title = chapterTitlesByHref[href]
            ?.takeIf { it.isNotBlank() }
            ?: fallbackChapterTitle(href, index)
        TOCReference(title, reference.resource)
    }
    return true
}

private fun applyCoverEdit(
    book: Book,
    coverUpdate: BookCoverUpdate?,
) {
    if (coverUpdate == null) {
        return
    }

    val extension = coverUpdate.extensionOrNull()
        ?: throw IllegalArgumentException("Unsupported cover format")
    val coverHref = "$EditedBookAssetDir/custom-cover.$extension"

    book.resources.allHrefs
        .filter { href ->
            href.startsWith("$EditedBookAssetDir/custom-cover.")
        }
        .toList()
        .forEach(book.resources::remove)

    val coverImage = Resource(coverUpdate.bytes, coverHref).apply {
        title = "Cover"
    }
    book.resources.add(coverImage)
    book.setCoverImage(coverImage)
}

private fun buildChapterTitleMap(book: Book): LinkedHashMap<String, String> {
    val titlesByHref = linkedMapOf<String, String>()
    collectTocTitles(book.tableOfContents.tocReferences, titlesByHref)
    book.spine.spineReferences.forEachIndexed { index, reference ->
        val href = cleanHref(reference.resource?.href)
        if (href.isNotBlank()) {
            titlesByHref.putIfAbsent(href, fallbackChapterTitle(href, index))
        }
    }
    return titlesByHref
}

private fun collectTocTitles(
    references: List<TOCReference>,
    titlesByHref: MutableMap<String, String>,
) {
    references.forEach { reference ->
        val href = cleanHref(reference.completeHref ?: reference.resource?.href)
        if (href.isNotBlank()) {
            titlesByHref.putIfAbsent(
                href,
                stripTocNumbering(reference.title).ifBlank {
                    fallbackChapterTitle(href, titlesByHref.size)
                },
            )
        }
        if (reference.children.isNotEmpty()) {
            collectTocTitles(reference.children, titlesByHref)
        }
    }
}

private fun nextAddedChapterNumber(book: Book): Int {
    val pattern = Regex("""^$EditedBookChapterPrefix(\d{4})\.xhtml$""")
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

private fun buildAddedChapterDocument(
    title: String,
    body: String,
): ByteArray {
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

    val bodyContent = buildString {
        append("<h1>$escapedTitle</h1>")
        if (paragraphs.isEmpty()) {
            append("<p>$escapedTitle</p>")
        } else {
            paragraphs.forEach { paragraph ->
                append("<p>$paragraph</p>")
            }
        }
    }

    return """<?xml version="1.0" encoding="UTF-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml">
          <head>
            <title>$escapedTitle</title>
          </head>
          <body>
            $bodyContent
          </body>
        </html>
    """.trimIndent().toByteArray(Charsets.UTF_8)
}

private fun BookEditRequest.normalized(): BookEditRequest? {
    val normalizedTitle = title.trim().ifBlank { return null }
    val normalizedAuthor = author.trim().ifBlank { "Unknown Author" }
    val normalizedDeletedHrefs = deletedChapterHrefs
        .map(::cleanHref)
        .filter(String::isNotBlank)
        .toSet()
    val normalizedAddedChapters = addedChapters.mapNotNull { chapter ->
        val normalizedChapterTitle = chapter.title.trim()
        if (normalizedChapterTitle.isBlank()) {
            null
        } else {
            BookChapterAddition(
                title = normalizedChapterTitle,
                body = chapter.body.trim().ifBlank { normalizedChapterTitle },
            )
        }
    }

    return copy(
        title = normalizedTitle,
        author = normalizedAuthor,
        deletedChapterHrefs = normalizedDeletedHrefs,
        addedChapters = normalizedAddedChapters,
    )
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

private fun stripTocNumbering(title: String?): String {
    return title
        .orEmpty()
        .replace(Regex("""^\s*\d+(?:\.\d+)*\.\s*"""), "")
        .trim()
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

private fun escapeXml(raw: String): String {
    return raw
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
