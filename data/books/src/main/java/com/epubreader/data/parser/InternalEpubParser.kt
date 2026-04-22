/**
 * FILE: InternalEpubParser.kt (Hardened & Preservation-Ready)
 * PURPOSE: Lightweight EPUB XML parser.
 * IMPROVEMENTS: Extracts full manifest to support high-fidelity metadata rebuilding.
 */
package com.epubreader.data.parser

import com.epubreader.core.debug.AppLog
import com.epubreader.core.model.TocItem
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.zip.ZipFile

internal data class ManifestItem(
    val id: String,
    val href: String, // Original relative href for rebuilding
    val mediaType: String,
    val properties: String? = null
)

internal data class RawEpubMetadata(
    val title: String?,
    val author: String?,
    val opfDir: String,
    val spine: List<String>, // Absolute paths
    val manifest: List<ManifestItem>,
    val toc: List<TocItem>,
    val coverHref: String? // Absolute path
)

internal object InternalEpubParser {

    fun parseEpub(zip: ZipFile): RawEpubMetadata? {
        return try {
            val opfPath = findOpfPath(zip) ?: return null
            val opfDir = opfPath.substringBeforeLast("/", "")
            
            val opfEntry = zip.getEntry(opfPath) ?: return null
            val opfData = zip.getInputStream(opfEntry).use { opfStream -> 
                parseOpf(opfStream, opfDir)
            }
            
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
                opfDir = opfDir,
                spine = opfData.spine,
                manifest = opfData.manifestItems,
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
            val parser = newPullParser()
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
        val manifestItems: List<ManifestItem>,
        val ncxPath: String?,
        val navPath: String?,
        val coverHref: String?
    )

    private fun parseOpf(input: InputStream, opfDir: String): OpfData {
        val parser = newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        var title: String? = null
        var author: String? = null
        val manifestMap = mutableMapOf<String, String>() // id -> stored href
        val manifestArchiveMap = mutableMapOf<String, String>() // id -> archive zip path
        val manifestItems = mutableListOf<ManifestItem>()
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
                        "dc:title", "title" -> title = readTextContent(parser)
                        "dc:creator", "creator" -> author = readTextContent(parser)
                        "item" -> {
                            val id = parser.getAttributeValue(null, "id")
                            val href = parser.getAttributeValue(null, "href")
                            val mediaType = parser.getAttributeValue(null, "media-type")
                            val properties = parser.getAttributeValue(null, "properties")
                            if (id != null && href != null && mediaType != null) {
                                manifestMap[id] = toStoredHref(opfDir, href)
                                manifestArchiveMap[id] = resolveZipPath(opfDir, href)
                                manifestItems.add(ManifestItem(id, href, mediaType, properties))
                                if (properties?.contains("nav") == true) navPath = manifestArchiveMap[id]
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
            spine = spineIds.mapNotNull { manifestMap[it] },
            manifestItems = manifestItems,
            ncxPath = ncxId?.let { manifestArchiveMap[it] },
            navPath = navPath,
            coverHref = coverId?.let { manifestMap[it] }
        )
    }

    private fun parseNcx(input: InputStream, opfDir: String): List<TocItem> {
        val toc = mutableListOf<TocItem>()
        val parser = newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        var currentTitle: String? = null
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (name) {
                        "text" -> currentTitle = readTextContent(parser)
                        "content" -> {
                            val src = parser.getAttributeValue(null, "src")
                            if (src != null && currentTitle != null) {
                                toc.add(TocItem(currentTitle, toStoredHref(opfDir, src)))
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
        val parser = newPullParser()
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
                        val title = readTextContent(parser)
                        if (href != null) {
                            toc.add(TocItem(title, toStoredHref(opfDir, href)))
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

    private fun readTextContent(parser: XmlPullParser): String {
        val result = StringBuilder()
        val startDepth = parser.depth
        while (true) {
            val eventType = parser.next()
            if (eventType == XmlPullParser.END_DOCUMENT) break
            if (eventType == XmlPullParser.END_TAG && parser.depth == startDepth) break
            
            if (eventType == XmlPullParser.TEXT) {
                result.append(parser.text)
            }
        }
        return result.toString().trim()
    }

    fun resolveZipPath(baseDir: String, relativeHref: String): String {
        val pathPart = relativeHref.substringBefore("#")
        val resolved = if (pathPart.startsWith("/") || baseDir.isEmpty()) {
            normalizePath(pathPart.removePrefix("/"))
        } else {
            normalizePath("$baseDir/$pathPart")
        }
        
        return if (relativeHref.contains("#")) {
            "$resolved#${relativeHref.substringAfter("#")}"
        } else {
            resolved
        }
    }

    fun toStoredHref(opfDir: String, relativeHref: String): String {
        val resolved = resolveZipPath(opfDir, relativeHref)
        val pathPart = resolved.substringBefore("#")
        val storedPath = if (opfDir.isNotBlank() && pathPart.startsWith("$opfDir/")) {
            pathPart.removePrefix("$opfDir/")
        } else {
            pathPart
        }
        return if (resolved.contains("#")) {
            "$storedPath#${resolved.substringAfter("#")}"
        } else {
            storedPath
        }
    }

    private fun newPullParser(): XmlPullParser {
        return XmlPullParserFactory.newInstance().newPullParser()
    }
}

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
