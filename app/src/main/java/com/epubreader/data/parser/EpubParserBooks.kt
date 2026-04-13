/**
 * AI_READ_AFTER: EpubParser.kt
 * AI_RELEVANT_TO: [Book Import, Metadata Cache, TOC Reconstruction, Book ID Generation]
 * PURPOSE: Package-local helpers for EPUB book rebuild and metadata persistence.
 * AI_WARNING: Preserve book ID generation and metadata.json shape.
 */
package com.epubreader.data.parser

import android.net.Uri
import com.epubreader.core.model.EpubBook
import com.epubreader.core.model.TocItem
import nl.siegmann.epublib.domain.Book
import nl.siegmann.epublib.domain.TOCReference
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

internal const val EPUB_BOOKS_DIR_NAME = "books"
internal const val EPUB_ARCHIVE_FILE_NAME = "book.epub"

private const val EPUB_METADATA_FILE_NAME = "metadata.json"
private const val EPUB_COVER_FILE_NAME = "cover_thumb.png"

internal fun buildBookId(uri: Uri, fileSize: Long): String {
    // Book ID must remain MD5(uri + fileSize) to preserve cached folders and reading progress keys.
    val rawId = "$uri$fileSize"
    return MessageDigest.getInstance("MD5")
        .digest(rawId.toByteArray())
        .joinToString("") { "%02x".format(it) }
}

internal fun rebuildBookMetadata(bookFolder: File): EpubBook? {
    val bookFile = File(bookFolder, EPUB_ARCHIVE_FILE_NAME)
    if (!bookFile.exists()) return null

    // [SELF-HEALING] Always re-read the binary to ensure metadata is perfectly synced
    // with the actual file content, even if metadata.json was corrupted or outdated.
    val book: Book = bookFile.inputStream().use { input ->
        nl.siegmann.epublib.epub.EpubReader().readEpub(input)
    }

    val coverPath = book.coverImage?.let { coverResource ->
        val coverFile = File(bookFolder, EPUB_COVER_FILE_NAME)
        coverResource.inputStream.use { input ->
            FileOutputStream(coverFile).use { output ->
                input.copyTo(output)
            }
        }
        coverFile.absolutePath
    }

    val spineHrefs = book.spine.spineReferences.map { it.resource.href }
    val toc = buildTableOfContents(book, spineHrefs)

    val epubBook = EpubBook(
        id = bookFolder.name,
        // [SELF-HEALING] Fallbacks for missing core metadata to prevent UI crashes.
        title = book.title ?: "Unknown Title",
        author = book.metadata.authors.firstOrNull()?.let { "${it.firstname} ${it.lastname}" } ?: "Unknown Author",
        coverPath = coverPath,
        rootPath = bookFolder.absolutePath,
        toc = toc,
        spineHrefs = spineHrefs,
        dateAdded = System.currentTimeMillis()
    )

    saveBookMetadata(bookFolder, epubBook)
    return epubBook
}

internal fun saveBookMetadata(folder: File, book: EpubBook) {
    val json = JSONObject().apply {
        put("id", book.id)
        put("title", book.title)
        put("author", book.author)
        put("coverPath", book.coverPath)
        put("rootPath", book.rootPath)
        put("dateAdded", book.dateAdded)
        put("lastRead", book.lastRead)
        val tocArray = JSONArray()
        book.toc.forEach { item ->
            tocArray.put(JSONObject().apply {
                put("title", item.title)
                put("href", item.href)
            })
        }
        put("toc", tocArray)
        val spineArray = JSONArray()
        book.spineHrefs.forEach { spineArray.put(it) }
        put("spineHrefs", spineArray)
    }
    File(folder, EPUB_METADATA_FILE_NAME).writeText(json.toString())
}

internal fun loadBookMetadata(folder: File): EpubBook? {
    val file = File(folder, EPUB_METADATA_FILE_NAME)
    if (!file.exists()) return null
    return try {
        val json = JSONObject(file.readText())
        val tocArray = json.getJSONArray("toc")
        val toc = mutableListOf<TocItem>()
        for (i in 0 until tocArray.length()) {
            val item = tocArray.getJSONObject(i)
            toc.add(TocItem(item.getString("title"), item.getString("href")))
        }

        val spineArray = json.optJSONArray("spineHrefs")
        val spineHrefs = mutableListOf<String>()
        if (spineArray != null) {
            for (i in 0 until spineArray.length()) {
                spineHrefs.add(spineArray.getString(i))
            }
        }

        EpubBook(
            id = json.getString("id"),
            title = json.getString("title"),
            author = json.getString("author"),
            // [SELF-HEALING] Resilience against null/empty cover paths.
            coverPath = json.optString("coverPath").takeIf { it.isNotEmpty() && it != "null" },
            rootPath = json.getString("rootPath"),
            toc = toc,
            spineHrefs = spineHrefs,
            // [SELF-HEALING] Preserve sort order even if dateAdded is missing from JSON.
            dateAdded = json.optLong("dateAdded", folder.lastModified()),
            lastRead = json.optLong("lastRead", 0L)
        )
    } catch (e: Exception) {
        // [SELF-HEALING] If JSON is corrupt, return null so scanBooks can ignore it or 
        // the caller can trigger a re-import/re-parse.
        null
    }
}

private fun buildTableOfContents(book: Book, spineHrefs: List<String>): List<TocItem> {
    val toc = mutableListOf<TocItem>()
    addTocReferences(book.tableOfContents.tocReferences, toc)

    // [SELF-HEALING] If the EPUB lacks a proper Table of Contents, reconstruct one 
    // from the spine filenames to ensure navigation is always possible.
    if (toc.isNotEmpty()) return toc

    spineHrefs.forEachIndexed { i, href ->
        val title = href.substringAfterLast("/").substringBeforeLast(".")
            .replace("_", " ")
            .replace("-", " ")
            .replaceFirstChar { it.uppercase() }
        toc.add(TocItem("${i + 1}. $title", href))
    }

    return toc
}

private fun addTocReferences(
    references: List<TOCReference>,
    toc: MutableList<TocItem>,
    prefix: String = ""
) {
    references.forEachIndexed { i, ref ->
        val currentNumber = if (prefix.isEmpty()) "${i + 1}" else "$prefix.${i + 1}"
        // [SELF-HEALING] Multi-step title fallback for malformed TOC entries.
        val rawTitle = ref.title?.trim()?.takeIf { it.isNotEmpty() }
            ?: ref.resource?.title?.trim()?.takeIf { it.isNotEmpty() }
            ?: ref.resource?.href?.substringAfterLast("/")?.substringBeforeLast(".")?.replace("_", " ")?.replace("-", " ")
            ?: "Chapter"

        val displayTitle = "$currentNumber. $rawTitle"
        toc.add(TocItem(title = displayTitle, href = ref.completeHref ?: ref.resource.href))
        if (ref.children.isNotEmpty()) {
            addTocReferences(ref.children, toc, currentNumber)
        }
    }
}
