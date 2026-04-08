package com.epubreader

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import nl.siegmann.epublib.domain.Book
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileOutputStream
import java.io.StringReader
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile

data class TocItem(
    val title: String,
    val href: String
)

sealed class ChapterElement {
    data class Text(val content: String, val type: String = "p", val id: String = UUID.randomUUID().toString()) : ChapterElement()
    data class Image(val data: ByteArray, val id: String = UUID.randomUUID().toString()) : ChapterElement()
}

data class EpubBook(
    val id: String,
    val title: String,
    val author: String,
    val coverPath: String?,
    val rootPath: String,
    val toc: List<TocItem> = emptyList(),
    val spineHrefs: List<String> = emptyList()
)

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
            spineHrefs = spineHrefs
        )

        saveMetadata(bookFolder, epubBook)
        return epubBook
    }

    fun deleteBook(book: EpubBook) {
        val folder = File(book.rootPath)
        if (folder.exists()) {
            folder.deleteRecursively()
        }
    }

    fun getChapterHref(book: EpubBook, index: Int): String? {
        return book.spineHrefs.getOrNull(index)
    }

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

                val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                
                val parser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(StringReader(xml))

                var eventType = parser.eventType
                val textAccumulator = StringBuilder()

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    val name = parser.name?.lowercase()
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            if (isBlockTag(name ?: "")) {
                                flushText(textAccumulator, elements)
                            }
                            if (name == "img" || name == "image") {
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
                                        elements.add(ChapterElement.Image(zip.getInputStream(it).readBytes()))
                                    }
                                }
                            }
                        }
                        XmlPullParser.TEXT -> {
                            textAccumulator.append(parser.text)
                        }
                        XmlPullParser.END_TAG -> {
                            if (isBlockTag(name ?: "")) {
                                val content = textAccumulator.toString().trim()
                                if (content.isNotEmpty()) {
                                    val type = if (name?.startsWith("h") == true) "h" else "p"
                                    elements.add(ChapterElement.Text(content.unescapeHtml(), type))
                                }
                                textAccumulator.setLength(0)
                            }
                        }
                    }
                    eventType = try { parser.next() } catch (e: Exception) { XmlPullParser.END_DOCUMENT }
                }
                flushText(textAccumulator, elements)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return elements
    }

    private fun flushText(accumulator: StringBuilder, elements: MutableList<ChapterElement>) {
        val content = accumulator.toString().trim()
        if (content.isNotEmpty()) {
            elements.add(ChapterElement.Text(content.unescapeHtml()))
            accumulator.setLength(0)
        }
    }

    private fun isBlockTag(tag: String) = tag in listOf("p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "li", "blockquote", "title", "section", "article", "br")

    private fun String.unescapeHtml(): String {
        return this.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&copy;", "©")
            .replace("&reg;", "®")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&hellip;", "…")
    }

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

    private fun saveMetadata(folder: File, book: EpubBook) {
        val json = JSONObject().apply {
            put("id", book.id)
            put("title", book.title)
            put("author", book.author)
            put("coverPath", book.coverPath)
            put("rootPath", book.rootPath)
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
                spineHrefs = spineHrefs
            )
        } catch (e: Exception) {
            null
        }
    }
}
