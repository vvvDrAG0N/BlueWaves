/**
 * FILE: EpubParserChapter.kt
 * PURPOSE: Optimized chapter parsing with O(1) ZIP entry resolution.
 */
package com.epubreader.data.parser

import com.epubreader.core.debug.AppLog
import com.epubreader.core.model.ChapterElement
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.StringReader
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Parses a specific chapter (XHTML file) using high-efficiency entry resolution.
 */
internal fun parseBookChapter(bookFolderPath: String, href: String): List<ChapterElement> {
    val elements = mutableListOf<ChapterElement>()
    val bookFolder = File(bookFolderPath)
    val bookFile = resolveChapterArchiveFile(bookFolder) ?: return elements
    val chapterMediaDir = File(bookFolder, CHAPTER_MEDIA_DIR_NAME)

    try {
        ZipFile(bookFile).use { zip ->
            val cleanHref = href.substringBefore("#").removePrefix("/")
            val entry = resolveChapterEntry(zip, cleanHref) ?: return emptyList()

            val rawXml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
            val xml = rawXml.preProcessXml()

            val parser = XmlPullParserFactory.newInstance().newPullParser()
            try {
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true)
            } catch (e: Exception) {
                AppLog.w(AppLog.PARSER) { "Relaxed mode not supported" }
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
                                        val cleanImgHref = InternalEpubParser.resolveZipPath(parentPath, src)

                                        val imgEntry = resolveImageEntry(zip, cleanImgHref, src)
                                        imgEntry?.let {
                                            val filePath = materializeChapterImage(
                                                zip = zip,
                                                imageEntry = it,
                                                chapterMediaDir = chapterMediaDir,
                                                imageKey = cleanImgHref.substringBefore("#"),
                                            )
                                            flushText(textAccumulator, elements, href)
                                            if (filePath != null) {
                                                elements.add(ChapterElement.Image(filePath, id = "$href-img-${elements.size}"))
                                            }
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
                    AppLog.e(AppLog.PARSER) { "Error processing event $eventType at tag <$name>: ${e.message}" }
                    errorCount++
                }

                try {
                    eventType = parser.next()
                } catch (e: Exception) {
                    AppLog.e(AppLog.PARSER) { "Parser crash at event $eventType: ${e.message}" }
                    errorCount++
                    if (errorCount >= 5) {
                        eventType = XmlPullParser.END_DOCUMENT
                    } else {
                        try {
                            eventType = parser.next()
                        } catch (e2: Exception) {
                            eventType = XmlPullParser.END_DOCUMENT
                        }
                    }
                }
            }
            flushText(textAccumulator, elements, href)
            AppLog.d(AppLog.PARSER) { "Parsed chapter $href, found ${elements.size} elements" }
        }
    } catch (e: Exception) {
        AppLog.e(AppLog.PARSER, e) { "Critical error parsing chapter $href" }
    }

    return elements
}

private fun resolveChapterArchiveFile(bookFolder: File): File? {
    return File(bookFolder, EPUB_ARCHIVE_FILE_NAME).takeIf(File::exists)
        ?: File(bookFolder, GENERATED_EPUB_FILE_NAME).takeIf(File::exists)
}

/**
 * OPTIMIZED: O(1) entry resolution using direct zip.getEntry().
 * The $O(N^2)$ sequence scan has been completely removed.
 */
private fun resolveChapterEntry(zip: ZipFile, exactPath: String): ZipEntry? {
    return zip.getEntry(exactPath)
        ?: zip.getEntry("OEBPS/$exactPath") // O(1) fallback
        ?: zip.getEntry("OPS/$exactPath")   // O(1) fallback
}

/**
 * OPTIMIZED: Image resolution using direct lookup.
 */
private fun resolveImageEntry(zip: ZipFile, cleanImgHref: String, src: String): ZipEntry? {
    // Both cleanImgHref (resolved absolute) and src (raw relative) are checked via O(1) lookups.
    return zip.getEntry(cleanImgHref.substringBefore("#"))
        ?: zip.getEntry(src.removePrefix("/"))
}

private fun materializeChapterImage(
    zip: ZipFile,
    imageEntry: ZipEntry,
    chapterMediaDir: File,
    imageKey: String,
): String? {
    if (!chapterMediaDir.exists() && !chapterMediaDir.mkdirs()) {
        return null
    }

    val extension = imageEntry.name.substringAfterLast('.', "img")
    val hashedName = "${hashImageKey(imageKey)}.$extension"
    val imageFile = File(chapterMediaDir, hashedName)
    val expectedSize = imageEntry.size

    if (!imageFile.exists() || (expectedSize >= 0 && imageFile.length() != expectedSize)) {
        zip.getInputStream(imageEntry).use { input ->
            imageFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    return imageFile.absolutePath
}

private fun hashImageKey(key: String): String {
    return MessageDigest.getInstance("MD5")
        .digest(key.toByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }
}

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

private fun flushText(
    accumulator: StringBuilder,
    elements: MutableList<ChapterElement>,
    chapterId: String,
    type: String = "p"
) {
    val content = accumulator.toString().trim()
    if (content.isNotEmpty()) {
        elements.add(ChapterElement.Text(content.unescapeHtml(), type, id = "$chapterId-txt-${elements.size}"))
        accumulator.setLength(0)
    }
}

private fun isBlockTag(tag: String): Boolean {
    return tag in listOf("p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "li", "blockquote", "title", "section", "article", "center")
}

private fun String.unescapeHtml(): String {
    return this.replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#39;", "'")
}

private const val CHAPTER_MEDIA_DIR_NAME = "chapter_media"
