/**
 * FILE: InternalEpubParser.kt
 * PURPOSE: Lightweight, high-performance EPUB XML parser using XmlPullParser.
 * DESIGN: 
 *  - Eliminates epublib dependency to reduce JVM GC pressure.
 *  - Implements O(1) path resolution via direct ZipEntry lookups.
 *  - Supports EPUB 2 (toc.ncx) and EPUB 3 (nav.xhtml) navigation.
 */
package com.epubreader.data.parser

import android.util.Xml
import com.epubreader.core.debug.AppLog
import com.epubreader.core.model.TocItem
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.zip.ZipFile

internal data class RawEpubMetadata(
    val title: String?,
    val author: String?,
    val spine: List<String>,
    val toc: List<TocItem>,
    val coverHref: String?
)

internal object InternalEpubParser {

    /**
     * Entry point for extracting core structural metadata from an EPUB ZipFile.
     */
    fun parseEpub(zip: ZipFile): RawEpubMetadata? {
        return try {
            val opfPath = findOpfPath(zip) ?: return null
            val opfDir = opfPath.substringBeforeLast("/", "")
            
            val opfEntry = zip.getEntry(opfPath) ?: return null
            // FIXED: OPF stream is now correctly wrapped in .use {} to prevent memory leaks.
            val opfData = zip.getInputStream(opfEntry).use { opfStream -> 
                parseOpf(opfStream, opfDir)
            }
            
            // Resolve TOC from EPUB 3 (Nav) or EPUB 2 (NCX)
            val toc = if (opfData.navPath != null) {
                val navEntry = zip.getEntry(opfData.navPath)
                if (navEntry != null) {
                    zip.getInputStream(navEntry).use { parseNav(it, opfDir) }
                } else null
            } else if (opfData.ncxPath != null) {
                val ncxEntry = zip.getEntry(opfData.ncxPath)
                if (ncxEntry != null) {
                    zip.getInputStream(ncxEntry).use { parseNcx(it, opfDir) }
                } else null
            } else null

            RawEpubMetadata(
                title = opfData.title,
                author = opfData.author,
                spine = opfData.spine,
                toc = toc ?: emptyList(),
                coverHref = opfData.coverHref
            )
        } catch (e: Exception) {
            AppLog.e(AppLog.PARSER, e) { "Failed to parse EPUB structure" }
            null
        }
    }

    private fun findOpfPath(zip: ZipFile): String? {
        val entry = zip.getEntry("META-INF/container.xml") ?: return null
        zip.getInputStream(entry).use { input ->
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(input, null)
            
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "rootfile") {
                    return parser.getAttributeValue(null, "full-path")
                }
                eventType = parser.next()
            }
        }
        return null
    }

    private data class OpfData(
        val title: String?,
        val author: String?,
        val spine: List<String>,
        val ncxPath: String?,
        val navPath: String?,
        val coverHref: String?
    )

    private fun parseOpf(input: InputStream, opfDir: String): OpfData {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        var title: String? = null
        var author: String? = null
        val manifest = mutableMapOf<String, String>() // ID -> Absolute ZIP Path
        val spineIds = mutableListOf<String>()
        var ncxId: String? = null
        var navPath: String? = null
        var coverId: String? = null

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (name) {
                        "dc:title", "title" -> title = parser.nextText()
                        "dc:creator", "creator" -> author = parser.nextText()
                        "item" -> {
                            val id = parser.getAttributeValue(null, "id")
                            val href = parser.getAttributeValue(null, "href")
                            val properties = parser.getAttributeValue(null, "properties")
                            if (id != null && href != null) {
                                val fullPath = resolveZipPath(opfDir, href)
                                manifest[id] = fullPath
                                if (properties?.contains("nav") == true) navPath = fullPath
                                if (properties?.contains("cover-image") == true) coverId = id
                            }
                        }
                        "itemref" -> {
                            val idref = parser.getAttributeValue(null, "idref")
                            if (idref != null) spineIds.add(idref)
                        }
                        "spine" -> {
                            ncxId = parser.getAttributeValue(null, "toc")
                        }
                        "meta" -> {
                            if (parser.getAttributeValue(null, "name") == "cover") {
                                coverId = parser.getAttributeValue(null, "content")
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return OpfData(
            title = title,
            author = author,
            spine = spineIds.mapNotNull { manifest[it] },
            ncxPath = ncxId?.let { manifest[it] },
            navPath = navPath,
            coverHref = coverId?.let { manifest[it] }
        )
    }

    private fun parseNcx(input: InputStream, opfDir: String): List<TocItem> {
        val toc = mutableListOf<TocItem>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        var currentTitle: String? = null
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (name) {
                        "text" -> currentTitle = parser.nextText()
                        "content" -> {
                            val src = parser.getAttributeValue(null, "src")
                            if (src != null && currentTitle != null) {
                                toc.add(TocItem(currentTitle, resolveZipPath(opfDir, src)))
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return toc
    }

    private fun parseNav(input: InputStream, opfDir: String): List<TocItem> {
        val toc = mutableListOf<TocItem>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        var inTocNav = false
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (name == "nav") {
                        val type = parser.getAttributeValue(null, "epub:type") ?: parser.getAttributeValue(null, "type")
                        if (type == "toc") inTocNav = true
                    } else if (inTocNav && name == "a") {
                        val href = parser.getAttributeValue(null, "href")
                        val title = parser.nextText()
                        if (href != null) {
                            toc.add(TocItem(title, resolveZipPath(opfDir, href)))
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (name == "nav") inTocNav = false
                }
            }
            eventType = parser.next()
        }
        return toc
    }

    /**
     * Resolves a relative Href against a base directory within the ZIP.
     */
    fun resolveZipPath(baseDir: String, relativeHref: String): String {
        // Handle anchor removal for path normalization, then re-append if needed for TOC.
        // For ZipEntry lookup, we only need the path part.
        val pathPart = relativeHref.substringBefore("#")
        val resolved = if (pathPart.startsWith("/") || baseDir.isEmpty()) {
            normalizePath(pathPart.removePrefix("/"))
        } else {
            normalizePath("$baseDir/$pathPart")
        }
        
        // If the original href had an anchor, preserve it for TOC navigation.
        return if (relativeHref.contains("#")) {
            "$resolved#${relativeHref.substringAfter("#")}"
        } else {
            resolved
        }
    }
}

/**
 * Normalizes relative paths (e.g., "../Images/pic.jpg" -> "Images/pic.jpg").
 * Consistently used across the parser package to resolve ZIP entry paths.
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
