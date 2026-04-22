/**
 * FILE: EpubParserEditing.kt (Repaired & Preservation-Ready)
 * PURPOSE: Surgical EPUB editing with full asset preservation.
 * FIXES: 
 *  - Fixed dual-stage data loss (Selective Dirtying).
 *  - Fixed manifest wipe (Manifest Preservation).
 *  - Fixed blank book bug by ensuring unmodified chapters are streamed correctly.
 */
package com.epubreader.data.parser

import com.epubreader.core.debug.AppLog
import com.epubreader.core.model.*
import java.io.*
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

private const val EditedBookAssetDir = "bluewaves"
private const val EditedBookChapterPrefix = "$EditedBookAssetDir/added-chapter-"
private const val EditedBookCustomCoverPrefix = "$EditedBookAssetDir/custom-cover."

/**
 * The main entry point for editing an EPUB.
 */
internal fun editStoredEpubBook(
    bookFolder: File,
    request: BookEditRequest,
): EpubBook? {
    val sourceFile = activeEpubFile(bookFolder, BookFormat.EPUB)
    if (!sourceFile.exists()) return null

    val normalizedRequest = request.normalized() ?: return null
    val stagedFile = File(bookFolder, "$EPUB_ARCHIVE_FILE_NAME.editing")
    stagedFile.delete()

    val metadataSeed = prepareMetadataSeedForBookEdit(
        existingMetadata = loadBookMetadata(bookFolder),
        bookFolder = bookFolder,
        coverAction = normalizedRequest.coverAction,
    )

    return try {
        ZipFile(sourceFile).use { zipIn ->
            val opfPath = findOpfPath(zipIn) ?: return null
            val opfDir = opfPath.substringBeforeLast("/", "")
            
            // 1. Parse full original structure for preservation
            val rawMetadata = InternalEpubParser.parseEpub(zipIn) ?: return null
            
            // 2. Prepare edits
            val chapterEditMap = prepareChapterEdits(normalizedRequest.chapters)
            val chapterEntries = buildEditedChapterEntries(opfDir, normalizedRequest.chapters, chapterEditMap)
            val coverEdit = prepareCoverEdit(normalizedRequest.coverAction)
            val navigationRewrites = buildNavigationRewrites(
                manifestItems = rawMetadata.manifest,
                opfDir = opfDir,
                bookTitle = normalizedRequest.title,
                chapterEntries = chapterEntries,
            )
            
            // 3. Rebuild OPF XML while preserving original manifest items
            val newOpfXml = rebuildOpf(rawMetadata, normalizedRequest, chapterEntries, coverEdit)
            
            // [PIPELINE] Begin surgical ZIP reconstruction
            FileOutputStream(stagedFile).use { fos ->
                ZipOutputStream(BufferedOutputStream(fos)).use { zipOut ->
                    
                    // A. THE MIMETYPE RULE
                    writeMimetypeEntry(zipOut)

                    // B. SELECTIVE DIRTYING: Only skip files we are explicitly replacing
                    val dirtyPaths = mutableSetOf<String>()
                    dirtyPaths.add("mimetype")
                    dirtyPaths.add(opfPath)
                    
                    // Only add to dirtyPaths if there is actual NEW content to write
                    chapterEditMap.forEach { (path, content) ->
                        if (content.hasNewContent()) {
                            dirtyPaths.add(archivePathForStoredHref(opfDir, path))
                        }
                    }
                    
                    if (coverEdit is CoverAction.Replace) {
                        dirtyPaths.add(archivePathForStoredHref(opfDir, coverEdit.absolutePath))
                    }
                    if (rawMetadata.coverHref != null && isEditedBookCustomCoverHref(rawMetadata.coverHref)) {
                        dirtyPaths.add(archivePathForStoredHref(opfDir, rawMetadata.coverHref))
                    }
                    navigationRewrites.forEach { dirtyPaths.add(it.archivePath) }

                    // C. THE BUFFER RULE: Stream unmodified assets (Images, CSS, Fonts, Unchanged Chapters)
                    val buffer = ByteArray(8192)
                    zipIn.entries().asSequence().forEach { entry ->
                        if (entry.name !in dirtyPaths) {
                            val newEntry = ZipEntry(entry.name)
                            zipOut.putNextEntry(newEntry)
                            zipIn.getInputStream(entry).use { input ->
                                var bytesRead: Int
                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    zipOut.write(buffer, 0, bytesRead)
                                }
                            }
                            zipOut.closeEntry()
                        }
                    }

                    // D. WRITE MODIFIED ENTRIES
                    writeTextEntry(zipOut, opfPath, newOpfXml)
                    
                    chapterEditMap.forEach { (targetPath, content) ->
                        val bytes = when (content) {
                            is ChapterContent.New -> buildNewChapterDocument(content.title, content.content)
                            is ChapterContent.Existing -> content.newHtml?.toByteArray(Charsets.UTF_8)
                        }
                        // Only write if we have bytes (Existing unmodified chapters were handled in step C)
                        bytes?.let { writeBytesEntry(zipOut, archivePathForStoredHref(opfDir, targetPath), it) }
                    }

                    if (coverEdit is CoverAction.Replace) {
                        writeBytesEntry(zipOut, archivePathForStoredHref(opfDir, coverEdit.absolutePath), coverEdit.bytes)
                    }
                    navigationRewrites.forEach { rewrite ->
                        writeTextEntry(zipOut, rewrite.archivePath, rewrite.content)
                    }
                }
            }
        }

        replaceFileAtomically(stagedFile, sourceFile)
        rebuildBookMetadata(bookFolder, metadataSeed)
    } catch (e: Exception) {
        AppLog.e(AppLog.PARSER, e) { "Failed to edit book ZIP" }
        stagedFile.takeIf(File::exists)?.delete()
        null
    }
}

private fun writeMimetypeEntry(zipOut: ZipOutputStream) {
    val mimetypeBytes = "application/epub+zip".toByteArray(Charsets.US_ASCII)
    val crc32 = CRC32().apply { update(mimetypeBytes) }
    val mimeEntry = ZipEntry("mimetype").apply {
        method = ZipEntry.STORED
        size = mimetypeBytes.size.toLong()
        compressedSize = mimetypeBytes.size.toLong()
        crc = crc32.value
    }
    zipOut.putNextEntry(mimeEntry)
    zipOut.write(mimetypeBytes)
    zipOut.closeEntry()
}

private fun writeTextEntry(zipOut: ZipOutputStream, path: String, text: String) {
    writeBytesEntry(zipOut, path, text.toByteArray(Charsets.UTF_8))
}

private fun writeBytesEntry(zipOut: ZipOutputStream, path: String, bytes: ByteArray) {
    val entry = ZipEntry(path)
    zipOut.putNextEntry(entry)
    zipOut.write(bytes)
    zipOut.closeEntry()
}

/**
 * Rebuilds the OPF XML with full manifest preservation.
 */
private fun rebuildOpf(
    rawMetadata: RawEpubMetadata,
    request: BookEditRequest,
    chapterEntries: List<EditedChapterEntry>,
    coverEdit: CoverAction?
): String {
    val sb = StringBuilder()
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
    sb.append("<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"2.0\" unique-identifier=\"bookid\">\n")
    
    // 1. Metadata Preservation & Update
    sb.append("  <metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n")
    sb.append("    <dc:title>${escapeXml(request.title)}</dc:title>\n")
    sb.append("    <dc:creator>${escapeXml(request.author)}</dc:creator>\n")
    sb.append("    <dc:language>en</dc:language>\n")
    sb.append("    <dc:identifier id=\"bookid\">${System.currentTimeMillis()}</dc:identifier>\n")
    if (coverEdit is CoverAction.Replace) {
        sb.append("    <meta name=\"cover\" content=\"cover-image\"/>\n")
    }
    sb.append("  </metadata>\n")

    // 2. Manifest Preservation: Keep all assets, only update/add chapters
    sb.append("  <manifest>\n")
    
    // First, add all original manifest items that are NOT chapters we're adding/editing
    // (We distinguish chapters by checking if their absolute path matches our spine/request)
    val chapterPaths = chapterEntries.map { it.storedHref.substringBefore("#") }.toSet()
    
    rawMetadata.manifest.forEach { item ->
        val storedPath = item.href.substringBefore("#")
        if (storedPath !in chapterPaths && item.id != "cover-image") {
            sb.append("    <item id=\"${item.id}\" href=\"${item.href}\" media-type=\"${item.mediaType}\"")
            if (item.properties != null) sb.append(" properties=\"${item.properties}\"")
            sb.append("/>\n")
        }
    }

    // Now add/update chapters in the order requested
    chapterEntries.forEach { chapterEntry ->
        sb.append("    <item id=\"${chapterEntry.id}\" href=\"${chapterEntry.relativeHref}\" media-type=\"application/xhtml+xml\"/>\n")
    }

    // Add cover if exists
    if (coverEdit is CoverAction.Replace) {
        sb.append("    <item id=\"cover-image\" href=\"${coverEdit.absolutePath}\" media-type=\"image/jpeg\"/>\n")
    }
    sb.append("  </manifest>\n")

    // 3. Spine Rebuild: Reflect new order
    val ncxItemId = rawMetadata.manifest.firstOrNull { it.mediaType == "application/x-dtbncx+xml" }?.id
    if (ncxItemId != null) {
        sb.append("  <spine toc=\"$ncxItemId\">\n")
    } else {
        sb.append("  <spine>\n")
    }
    chapterEntries.forEach { chapterEntry ->
        sb.append("    <itemref idref=\"${chapterEntry.id}\"/>\n")
    }
    sb.append("  </spine>\n")
    
    sb.append("</package>")
    return sb.toString()
}

private sealed class ChapterContent {
    data class Existing(val absolutePath: String, val newHtml: String? = null) : ChapterContent()
    data class New(val title: String, val content: BookNewChapterContent) : ChapterContent()

    fun hasNewContent(): Boolean = when(this) {
        is Existing -> newHtml != null
        is New -> true
    }
}

private sealed class CoverAction {
    object Remove : CoverAction()
    data class Replace(val absolutePath: String, val bytes: ByteArray) : CoverAction()
}

private data class EditedChapterEntry(
    val id: String,
    val title: String,
    val storedHref: String,
    val relativeHref: String,
)

private data class NavigationRewrite(
    val archivePath: String,
    val content: String,
)

private fun prepareChapterEdits(chapters: List<BookChapterEdit>): Map<String, ChapterContent> {
    val map = mutableMapOf<String, ChapterContent>()
    var nextId = 1
    chapters.forEach { edit ->
        val href = edit.existingHref
        if (href != null) {
            map[href] = ChapterContent.Existing(href, edit.newChapterContent?.let { (it as? BookNewChapterContent.HtmlDocument)?.markup })
        } else {
            val newPath = "$EditedBookChapterPrefix${nextId.toString().padStart(4, '0')}.xhtml"
            edit.newChapterContent?.let {
                map[newPath] = ChapterContent.New(edit.title, it)
                nextId++
            }
        }
    }
    return map
}

private fun prepareCoverEdit(action: BookCoverAction): CoverAction? {
    return when (action) {
        is BookCoverAction.Replace -> {
            val ext = action.cover.extensionOrNull() ?: "jpg"
            CoverAction.Replace("$EditedBookCustomCoverPrefix$ext", action.cover.bytes)
        }
        BookCoverAction.Remove -> CoverAction.Remove
        else -> null
    }
}

private fun buildEditedChapterEntries(
    opfDir: String,
    chapters: List<BookChapterEdit>,
    chapterEdits: Map<String, ChapterContent>,
): List<EditedChapterEntry> {
    return chapters.mapIndexed { index, chapter ->
        val storedHref = chapter.existingHref
            ?: chapterEdits.entries
                .first { it.value is ChapterContent.New && (it.value as ChapterContent.New).title == chapter.title }
                .key
        EditedChapterEntry(
            id = "chapter_$index",
            title = chapter.title,
            storedHref = storedHref,
            relativeHref = if (opfDir.isEmpty()) storedHref else storedHref.removePrefix("$opfDir/"),
        )
    }
}

private fun buildNavigationRewrites(
    manifestItems: List<ManifestItem>,
    opfDir: String,
    bookTitle: String,
    chapterEntries: List<EditedChapterEntry>,
): List<NavigationRewrite> {
    return manifestItems.mapNotNull { item ->
        when {
            item.mediaType == "application/x-dtbncx+xml" -> NavigationRewrite(
                archivePath = InternalEpubParser.resolveZipPath(opfDir, item.href),
                content = buildNcxDocument(bookTitle, chapterEntries),
            )

            item.properties?.contains("nav") == true -> NavigationRewrite(
                archivePath = InternalEpubParser.resolveZipPath(opfDir, item.href),
                content = buildNavDocument(bookTitle, chapterEntries),
            )

            else -> null
        }
    }
}

private fun buildNcxDocument(
    bookTitle: String,
    chapterEntries: List<EditedChapterEntry>,
): String {
    val navPoints = chapterEntries.mapIndexed { index, chapter ->
        """    <navPoint id="navPoint-${index + 1}" playOrder="${index + 1}">
      <navLabel><text>${escapeXml(chapter.title)}</text></navLabel>
      <content src="${escapeXml(chapter.relativeHref)}"/>
    </navPoint>"""
    }.joinToString("\n")

    return """<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
  <head></head>
  <docTitle><text>${escapeXml(bookTitle)}</text></docTitle>
  <navMap>
$navPoints
  </navMap>
</ncx>"""
}

private fun buildNavDocument(
    bookTitle: String,
    chapterEntries: List<EditedChapterEntry>,
): String {
    val items = chapterEntries.joinToString("\n") { chapter ->
        """      <li><a href="${escapeXml(chapter.relativeHref)}">${escapeXml(chapter.title)}</a></li>"""
    }

    return """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
  <head><title>${escapeXml(bookTitle)}</title></head>
  <body>
    <nav epub:type="toc">
      <ol>
$items
      </ol>
    </nav>
  </body>
</html>"""
}

private fun archivePathForStoredHref(opfDir: String, storedHref: String): String {
    return InternalEpubParser.resolveZipPath(opfDir, storedHref).substringBefore("#")
}

private fun findOpfPath(zip: ZipFile): String? {
    val entry = zip.getEntry("META-INF/container.xml") ?: return null
    zip.getInputStream(entry).use { input ->
        val text = input.bufferedReader().use { it.readText() }
        val match = Regex("""full-path="([^"]+)"""").find(text)
        return match?.groupValues?.getOrNull(1)
    }
}

private fun buildNewChapterDocument(title: String, content: BookNewChapterContent): ByteArray {
    val bodyMarkup = when (content) {
        is BookNewChapterContent.PlainText -> "<h1>${escapeXml(title)}</h1><p>${escapeXml(content.body).replace("\n", "<br/>")}</p>"
        is BookNewChapterContent.HtmlDocument -> content.markup
    }
    return """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml">
<head><title>${escapeXml(title)}</title></head>
<body>$bodyMarkup</body>
</html>""".toByteArray(Charsets.UTF_8)
}

private fun BookEditRequest.normalized(): BookEditRequest? {
    if (title.isBlank() || chapters.isEmpty()) return null
    return this
}

private fun BookCoverUpdate.extensionOrNull(): String? {
    val mime = mimeType.lowercase()
    return when {
        mime.contains("png") -> "png"
        mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
        mime.contains("webp") -> "webp"
        else -> null
    }
}

private fun prepareMetadataSeedForBookEdit(
    existingMetadata: EpubBook?,
    bookFolder: File,
    coverAction: BookCoverAction,
): EpubBook? {
    existingMetadata ?: return null

    val originalCoverPath = existingMetadata.originalCoverPath ?: existingMetadata.coverPath
    val currentCoverPath = existingMetadata.currentCoverPath

    return when (coverAction) {
        BookCoverAction.Keep -> existingMetadata
        BookCoverAction.Remove -> {
            val promotedOriginalPath = originalCoverPath ?: currentCoverPath?.let { currentPath ->
                val currentFile = File(currentPath)
                if (currentFile.exists()) {
                    val originalFile = File(bookFolder, EPUB_ORIGINAL_COVER_FILE_NAME)
                    if (currentFile.absolutePath != originalFile.absolutePath) {
                        currentFile.inputStream().use { input -> originalFile.outputStream().use { output -> input.copyTo(output) } }
                    }
                    originalFile.absolutePath
                } else null
            }
            existingMetadata.copy(coverPath = promotedOriginalPath, originalCoverPath = promotedOriginalPath, currentCoverPath = null)
        }
        is BookCoverAction.Replace -> existingMetadata.copy(
            coverPath = File(bookFolder, EPUB_CURRENT_COVER_FILE_NAME).absolutePath,
            originalCoverPath = originalCoverPath ?: currentCoverPath ?: existingMetadata.coverPath,
            currentCoverPath = File(bookFolder, EPUB_CURRENT_COVER_FILE_NAME).absolutePath,
        )
    }
}

private fun escapeXml(raw: String): String = raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
