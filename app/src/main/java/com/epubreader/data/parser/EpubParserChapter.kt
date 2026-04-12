/**
 * AI_READ_AFTER: EpubParser.kt
 * AI_RELEVANT_TO: [Chapter Parsing, Image Resolution, normalizePath(), ZipFile Safety]
 * PURPOSE: Package-local chapter parsing helpers.
 * AI_WARNING: Preserve ZipFile `.use {}`, relaxed parser error handling, and image path resolution order.
 */
package com.epubreader.data.parser

import android.util.Xml
import com.epubreader.core.debug.AppLog
import com.epubreader.core.model.ChapterElement
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.StringReader
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

internal fun parseBookChapter(bookFolderPath: String, href: String): List<ChapterElement> {
    val elements = mutableListOf<ChapterElement>()
    val bookFile = File(bookFolderPath, EPUB_ARCHIVE_FILE_NAME)
    if (!bookFile.exists()) return elements

    try {
        ZipFile(bookFile).use { zip ->
            val cleanHref = href.substringBefore("#").removePrefix("/")
            val entry = resolveChapterEntry(zip, cleanHref) ?: return emptyList()

            val rawXml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
            val xml = rawXml.preProcessXml()

            val parser = Xml.newPullParser()
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
                                        val imgHref = if (parentPath.isEmpty()) src else "$parentPath/$src"
                                        val cleanImgHref = normalizePath(imgHref).removePrefix("/")

                                        val imgEntry = resolveImageEntry(zip, cleanImgHref, src)
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
                        // Try to skip to something that looks like a tag
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

private fun resolveChapterEntry(zip: ZipFile, cleanHref: String): ZipEntry? {
    return zip.getEntry(cleanHref)
        ?: zip.getEntry("OEBPS/$cleanHref")
        ?: zip.getEntry("OPS/$cleanHref")
        ?: zip.entries().asSequence().find { it.name.endsWith(cleanHref) }
}

private fun resolveImageEntry(zip: ZipFile, cleanImgHref: String, src: String): ZipEntry? {
    return zip.getEntry(cleanImgHref)
        ?: zip.entries().asSequence().find { it.name.endsWith(src.substringAfterLast("/")) }
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

/**
 * PURPOSE: Normalizes relative paths (e.g., "../Images/pic.jpg" -> "Images/pic.jpg").
 * AI_NOTE: Essential for finding resources inside the EPUB zip container.
 */
internal fun normalizePath(path: String): String {
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
