/**
 * AI_ENTRY_POINT
 * AI_READ_FIRST
 * AI_RELEVANT_TO: [EPUB Parsing, Book Extraction, Chapter Rendering Data, Metadata Management]
 * AI_STATE_OWNER: Stateless facade - callers manage lifecycle.
 * AI_DATA_FLOW: Uri -> parseAndExtract() -> cache/books -> EpubBook
 * AI_WARNING: All parsing is IO-heavy; call from Dispatchers.IO.
 *
 * FILE: EpubParser.kt
 * PURPOSE: Public EPUB parser facade.
 * RESPONSIBILITIES:
 *  - Owns the public parser API used by app shell and reader code.
 *  - Coordinates import, cached book scans, chapter parsing, and metadata updates.
 *  - Delegates chapter parsing and metadata/book rebuild details to package-local helpers.
 * NON-GOALS:
 *  - Does not own UI logic.
 *  - Does not own DataStore-backed state.
 */
package com.epubreader.data.parser

import android.content.Context
import android.net.Uri
import com.epubreader.core.model.ChapterElement
import com.epubreader.core.model.EpubBook
import java.io.File
import java.io.FileOutputStream

/**
 * Handles all EPUB extraction and parsing logic.
 * AI_WARNING: All methods should ideally be called from Dispatchers.IO.
 */
class EpubParser(private val context: Context) {

    private val booksDir = File(context.cacheDir, EPUB_BOOKS_DIR_NAME).apply {
        if (!exists()) mkdirs()
    }

    /**
     * AI_MUTATION_POINT
     * AI_WARNING: Must trigger UI refresh after this.
     *
     * PURPOSE: Imports an EPUB from a Uri, extracts it to internal storage, and generates metadata.
     * INPUT: Uri of the EPUB file.
     * OUTPUT: [EpubBook] object if successful, null otherwise.
     * SIDE EFFECTS: Writes to cache/books directory.
     * AI_WARNING: This duplicates the entire EPUB file into internal storage.
     */
    fun parseAndExtract(uri: Uri): EpubBook? {
        return try {
            val fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            val fileSize = fileDescriptor.statSize
            fileDescriptor.close()

            val bookFolder = File(booksDir, buildBookId(uri, fileSize))
            if (!bookFolder.exists()) bookFolder.mkdirs()

            val bookFile = File(bookFolder, EPUB_ARCHIVE_FILE_NAME)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(bookFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            reparseBook(bookFolder)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * AI_MUTATION_POINT
     * AI_WARNING: Must trigger UI refresh after this.
     *
     * PURPOSE: Fully parses an extracted EPUB folder into an [EpubBook] model.
     * INPUT: Folder where "book.epub" is stored.
     * OUTPUT: [EpubBook] object.
     * SIDE EFFECTS: Re-writes "metadata.json" in the folder.
     */
    fun reparseBook(bookFolder: File): EpubBook? = rebuildBookMetadata(bookFolder)

    fun scanBooks(): List<EpubBook> {
        return booksDir.listFiles()?.filter { it.isDirectory }?.mapNotNull(::loadBookMetadata) ?: emptyList()
    }

    fun deleteBook(book: EpubBook) {
        // AI_MUTATION_POINT: Deletes files from disk.
        // AI_WARNING: Must trigger UI refresh after this.
        val folder = File(book.rootPath)
        if (folder.exists()) {
            folder.deleteRecursively()
        }
    }

    fun getChapterHref(book: EpubBook, index: Int): String? {
        return book.spineHrefs.getOrNull(index)
    }

    /**
     * PURPOSE: Parses a specific chapter (XHTML file) within the EPUB.
     * INPUT: bookFolderPath, href of the chapter.
     * OUTPUT: List of [ChapterElement] (Text and Image nodes).
     * AI_CRITICAL: This delegates to the package-local chapter parser that preserves ZipFile safety,
     * relaxed XmlPullParser handling, and normalizePath() behavior.
     */
    fun parseChapter(bookFolderPath: String, href: String): List<ChapterElement> {
        return parseBookChapter(bookFolderPath, href)
    }

    fun updateLastRead(book: EpubBook) {
        // AI_MUTATION_POINT: Updates progress in metadata.json.
        // AI_WARNING: Must trigger UI refresh after this.
        val folder = File(booksDir, book.id)
        if (folder.exists()) {
            saveBookMetadata(folder, book)
        }
    }

    /**
     * PURPOSE: Reads cached metadata.json for an already-imported book folder.
     * AI_NOTE: Kept public for compatibility with existing parser surface, even though current callers
     * mainly go through scanBooks().
     */
    fun loadMetadata(folder: File): EpubBook? = loadBookMetadata(folder)
}
