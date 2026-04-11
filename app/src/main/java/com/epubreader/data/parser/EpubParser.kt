/**
 * AI_ENTRY_POINT
 * AI_READ_FIRST
 * AI_RELEVANT_TO: [EPUB Parsing, Book Extraction, Chapter Rendering Data, Metadata Management]
 * AI_STATE_OWNER: Stateless utility – callers manage lifecycle.
 * AI_DATA_FLOW: Uri → parseAndExtract() → cache/books → EpubBook
 * AI_WARNING: All parsing is IO‑heavy; call from Dispatchers.IO.
 * 
 * FILE: EpubParser.kt
 * PURPOSE: Core logic for parsing EPUB files and extracting content for the reader.
 * RESPONSIBILITIES:
 *  - Imports and extracts EPUB files into internal storage (cache/books).
 *  - Parses spine structure and Table of Contents (TOC).
 *  - Extracts individual chapters into [ChapterElement] for rendering in [ReaderScreen].
 *  - Manages book metadata and persistence (metadata.json).
 * NON-GOALS:
 *  - Does not handle UI rendering directly (provides data to ReaderScreen).
 *  - Does not handle settings or global state (see SettingsManager).
 * DEPENDENCIES: nl.siegmann.epublib, XmlPullParser, ZipFile.
 * SIDE EFFECTS: IO operations on cache/books directory.
 */
package com.epubreader.data.parser

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.util.Xml
import com.epubreader.core.model.ChapterElement
import com.epubreader.core.model.EpubBook
import com.epubreader.core.model.TocItem
import nl.siegmann.epublib.domain.Book
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileOutputStream
import java.io.StringReader
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Handles all EPUB extraction and parsing logic.
 * AI_WARNING: All methods should ideally be called from Dispatchers.IO.
 */
class EpubParser(private val context: Context) {

    private val booksDir = File(context.cacheDir, "books")

    init {
        if (!booksDir.exists()) booksDir.mkdirs()
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = cursor.getString(index)
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) result = result?.substring(cut + 1)
        }
        return result
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

            // Unique ID based on path, size (basic duplicate prevention)
            val rawId = "$uri$fileSize"
            val bookId = MessageDigest.getInstance("MD5")
                .digest(rawId.toByteArray())
                .joinToString("") { "%02x".format(it) }

            val bookFolder = File(booksDir, bookId)
            if (!bookFolder.exists()) bookFolder.mkdirs()

            val bookFile = File(bookFolder, "book.epub")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(bookFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            return reparseBook(bookFolder)
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
    fun reparseBook(bookFolder: File): EpubBook? {
        val bookFile = File(bookFolder, "book.epub")
        if (!bookFile.exists()) return null

        val book: Book = bookFile.inputStream().use { input ->
            nl.siegmann.epublib.epub.EpubReader().readEpub(input)
        }

        val coverPath = book.coverImage?.let { coverResource ->
            val coverFile = File(bookFolder, "cover_thumb.png")
            coverResource.inputStream.use { input ->
                FileOutputStream(coverFile).use { output ->
                    input.copyTo(output)
                }
            }
            coverFile.absolutePath
        }

        val spineHrefs = book.spine.spineReferences.map { it.resource.href }
        val toc = mutableListOf<TocItem>()
        
        fun processTOC(references: List<nl.siegmann.epublib.domain.TOCReference>, prefix: String = "") {
            references.forEachIndexed { i, ref ->
                val currentNumber = if (prefix.isEmpty()) "${i + 1}" else "$prefix.${i + 1}"
                val rawTitle = ref.title?.trim()?.takeIf { it.isNotEmpty() }
                    ?: ref.resource?.title?.trim()?.takeIf { it.isNotEmpty() }
                    ?: ref.resource?.href?.substringAfterLast("/")?.substringBeforeLast(".")?.replace("_", " ")?.replace("-", " ")
                    ?: "Chapter"
                
                val displayTitle = "$currentNumber. $rawTitle"
                toc.add(TocItem(title = displayTitle, href = ref.completeHref ?: ref.resource.href))
                if (ref.children.isNotEmpty()) processTOC(ref.children, currentNumber)
            }
        }
        processTOC(book.tableOfContents.tocReferences)

        if (toc.isEmpty()) {
            spineHrefs.forEachIndexed { i, href ->
                val title = href.substringAfterLast("/").substringBeforeLast(".")
                        .replace("_", " ").replace("-", " ").replaceFirstChar { it.uppercase() }
                toc.add(TocItem("${i + 1}. $title", href))
            }
        }

        val epubBook = EpubBook(
            id = bookFolder.name,
            title = book.title ?: "Unknown Title",
            author = book.metadata.authors.firstOrNull()?.let { "${it.firstname} ${it.lastname}" } ?: "Unknown Author",
            coverPath = coverPath,
            rootPath = bookFolder.absolutePath,
            toc = toc,
            spineHrefs = spineHrefs,
            dateAdded = System.currentTimeMillis()
        )

        saveMetadata(bookFolder, epubBook)
        return epubBook
    }

    fun scanBooks(): List<EpubBook> {
        return booksDir.listFiles()?.filter { it.isDirectory }?.mapNotNull { folder ->
            loadMetadata(folder)
        } ?: emptyList()
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
     * PURPOSE: Cleans raw XML content for the parser by replacing HTML entities and fixing unescaped ampersands.
     * AI_NOTE: This is a pre-parsing step to handle poorly formatted EPUB XHTML.
     */
    private fun String.preProcessXml(): String {
        return this.replace("&nbsp;", " ")
            .replace("&rsquo;", "’")
            .replace("&lsquo;", "‘")
            .replace("&ldquo;", "“")
            .replace("&rdquo;", "”")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&hellip;", "…")
            .replace("&copy;", "©")
            .replace("&reg;", "®")
            .replace("&deg;", "°")
            .replace("&prime;", "′")
            .replace("&Prime;", "″")
            .replace("&(?!(amp|lt|gt|quot|apos|#[0-9]+|#x[0-9a-fA-F]+);)".toRegex(), "&amp;")
    }

    /**
     * PURPOSE: Parses a specific chapter (XHTML file) within the EPUB.
     * INPUT: bookFolderPath, href of the chapter.
     * OUTPUT: List of [ChapterElement] (Text and Image nodes).
     * AI_CRITICAL: This uses [XmlPullParser] with custom logic to handle block tags and image extraction.
     * AI_WARNING: High memory usage for chapters with many large images.
     */
    fun parseChapter(bookFolderPath: String, href: String): List<ChapterElement> {
        val elements = mutableListOf<ChapterElement>()
        val bookFile = File(bookFolderPath, "book.epub")
        if (!bookFile.exists()) return elements

        try {
            ZipFile(bookFile).use { zip ->
                val cleanHref = href.substringBefore("#").removePrefix("/")
                val entry = zip.getEntry(cleanHref) 
                    ?: zip.getEntry("OEBPS/$cleanHref") 
                    ?: zip.getEntry("OPS/$cleanHref")
                    ?: zip.entries().asSequence().find { it.name.endsWith(cleanHref) }

                if (entry == null) return emptyList()

                val rawXml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                val xml = rawXml.preProcessXml()
                
                val parser = Xml.newPullParser()
                try {
                    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                    parser.setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true)
                } catch (e: Exception) {
                    Log.w("EpubParser", "Relaxed mode not supported")
                }
                
                parser.setInput(StringReader(xml))

                var eventType = parser.eventType
                val textAccumulator = StringBuilder()
                var errorCount = 0

                while (eventType != XmlPullParser.END_DOCUMENT && errorCount < 5) {
                    val name = parser.name?.lowercase()
                    try {
                        when (eventType) {
                            XmlPullParser.START_TAG -> {
                                when (name) {
                                    "br" -> textAccumulator.append("\n")
                                    "hr" -> flushText(textAccumulator, elements, href)
                                    "img", "image" -> {
                                        val src = parser.getAttributeValue(null, "src") 
                                            ?: parser.getAttributeValue(null, "href")
                                            ?: parser.getAttributeValue(null, "xlink:href")
                                        
                                        if (src != null) {
                                            val parentPath = entry.name.substringBeforeLast("/", "")
                                            val imgHref = if (parentPath.isEmpty()) src else "$parentPath/$src"
                                            val cleanImgHref = normalizePath(imgHref).removePrefix("/")
                                            
                                            val imgEntry = zip.getEntry(cleanImgHref)
                                                ?: zip.entries().asSequence().find { it.name.endsWith(src.substringAfterLast("/")) }
                                            
                                            imgEntry?.let {
                                                val data = zip.getInputStream(it).readBytes()
                                                flushText(textAccumulator, elements, href)
                                                elements.add(ChapterElement.Image(data, id = "$href-img-${elements.size}"))
                                            }
                                        }
                                    }
                                    else -> {
                                        if (isBlockTag(name ?: "")) {
                                            flushText(textAccumulator, elements, href)
                                        }
                                    }
                                }
                            }
                            XmlPullParser.TEXT -> {
                                textAccumulator.append(parser.text)
                            }
                            XmlPullParser.END_TAG -> {
                                if (isBlockTag(name ?: "")) {
                                    val type = if (name?.startsWith("h") == true) "h" else "p"
                                    flushText(textAccumulator, elements, href, type)
                                }
                            }
                        }
                        errorCount = 0 
                    } catch (e: Exception) {
                        Log.e("EpubParser", "Error processing event $eventType at tag <$name>: ${e.message}")
                        errorCount++
                    }
                    
                    try {
                        eventType = parser.next()
                    } catch (e: Exception) {
                        Log.e("EpubParser", "Parser crash at event $eventType: ${e.message}")
                        errorCount++
                        if (errorCount >= 5) {
                            eventType = XmlPullParser.END_DOCUMENT
                        } else {
                            // Try to skip to something that looks like a tag
                            try { eventType = parser.next() } catch (e2: Exception) { eventType = XmlPullParser.END_DOCUMENT }
                        }
                    }
                }
                flushText(textAccumulator, elements, href)
                Log.d("EpubParser", "Parsed chapter $href, found ${elements.size} elements")
            }
        } catch (e: Exception) {
            Log.e("EpubParser", "Critical error parsing chapter $href", e)
        }

        return elements
    }

    private fun flushText(accumulator: StringBuilder, elements: MutableList<ChapterElement>, chapterId: String, type: String = "p") {
        val content = accumulator.toString().trim()
        if (content.isNotEmpty()) {
            elements.add(ChapterElement.Text(content.unescapeHtml(), type, id = "$chapterId-txt-${elements.size}"))
            accumulator.setLength(0)
        }
    }

    private fun isBlockTag(tag: String) = tag in listOf("p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "li", "blockquote", "title", "section", "article", "center")

    private fun String.unescapeHtml(): String {
        return this.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
    }

    /**
     * PURPOSE: Normalizes relative paths (e.g., "../Images/pic.jpg" -> "Images/pic.jpg").
     * AI_NOTE: Essential for finding resources inside the EPUB zip container.
     */
    private fun normalizePath(path: String): String {
        val parts = path.split("/")
        val result = mutableListOf<String>()
        for (part in parts) {
            when (part) {
                "." -> {}
                ".." -> if (result.isNotEmpty()) result.removeAt(result.size - 1)
                else -> if (part.isNotEmpty()) result.add(part)
            }
        }
        return result.joinToString("/")
    }

    fun updateLastRead(book: EpubBook) {
        // AI_MUTATION_POINT: Updates progress in metadata.json.
        // AI_WARNING: Must trigger UI refresh after this.
        val folder = File(booksDir, book.id)
        if (folder.exists()) {
            saveMetadata(folder, book)
        }
    }

    private fun saveMetadata(folder: File, book: EpubBook) {
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
        File(folder, "metadata.json").writeText(json.toString())
    }

    fun loadMetadata(folder: File): EpubBook? {
        val file = File(folder, "metadata.json")
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
                coverPath = json.optString("coverPath").takeIf { it.isNotEmpty() && it != "null" },
                rootPath = json.getString("rootPath"),
                toc = toc,
                spineHrefs = spineHrefs,
                dateAdded = json.optLong("dateAdded", folder.lastModified()),
                lastRead = json.optLong("lastRead", 0L)
            )
        } catch (e: Exception) {
            null
        }
    }
}
