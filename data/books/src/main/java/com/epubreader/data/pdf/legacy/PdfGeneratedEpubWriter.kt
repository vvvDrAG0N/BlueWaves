package com.epubreader.data.pdf.legacy

import com.epubreader.data.parser.EPUB_MIME_TYPE
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal fun writeGeneratedEpub(
    outputFile: File,
    workspaceDir: File,
    title: String,
    author: String,
    totalPages: Int,
) {
    val sections = buildPdfReflowSections(workspaceDir, totalPages)
    require(sections.isNotEmpty()) { "No reflow sections were generated for this PDF." }

    outputFile.parentFile?.mkdirs()
    ZipOutputStream(FileOutputStream(outputFile)).use { zip ->
        addStoredEntry(zip, "mimetype", EPUB_MIME_TYPE)
        addEntry(
            zip,
            "META-INF/container.xml",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
            """.trimIndent().trimStart(),
        )
        addFileEntry(
            zip,
            "OEBPS/styles/book.css",
            File(workspaceDir, "styles/book.css"),
        )
        for (section in sections) {
            addEntry(
                zip,
                "OEBPS/${section.href}",
                buildSectionDocument(
                    section = section,
                    bookTitle = title,
                ),
            )
        }
        addEntry(zip, "OEBPS/content.opf", buildOpfDocument(title, author, sections))
        addEntry(zip, "OEBPS/toc.ncx", buildNcxDocument(title, sections))
    }
}

internal fun buildOpfDocument(
    title: String,
    author: String,
    sections: List<PdfReflowSection>,
): String {
    val manifest = buildString {
        appendLine("""    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>""")
        appendLine("""    <item id="css" href="styles/book.css" media-type="text/css"/>""")
        for (section in sections) {
            appendLine(
                """    <item id="section-${section.index}" href="${section.href}" media-type="application/xhtml+xml"/>""",
            )
        }
    }.trimEnd()

    val spine = buildString {
        for (section in sections) {
            appendLine("""    <itemref idref="section-${section.index}"/>""")
        }
    }.trimEnd()

    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="BookId">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:title>${title.escapeForXhtml()}</dc:title>
            <dc:creator>${author.escapeForXhtml()}</dc:creator>
            <dc:identifier id="BookId">urn:uuid:${UUID.randomUUID()}</dc:identifier>
            <dc:language>en</dc:language>
          </metadata>
          <manifest>
        $manifest
          </manifest>
          <spine toc="ncx">
        $spine
          </spine>
        </package>
    """.trimIndent().trimStart()
}

internal fun buildNcxDocument(title: String, sections: List<PdfReflowSection>): String {
    val navPoints = buildString {
        for (section in sections) {
            appendLine(
                """
                <navPoint id="section-${section.index}" playOrder="${section.index}">
                  <navLabel><text>${section.title.escapeForXhtml()}</text></navLabel>
                  <content src="${section.href}#${section.anchorId}"/>
                </navPoint>
                """.trimIndent(),
            )
        }
    }.trimEnd()

    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
          <head/>
          <docTitle><text>${title.escapeForXhtml()}</text></docTitle>
          <navMap>
        $navPoints
          </navMap>
        </ncx>
    """.trimIndent().trimStart()
}

internal fun buildSectionDocument(
    section: PdfReflowSection,
    bookTitle: String,
): String {
    val body = section.pages.joinToString(separator = "\n") { page ->
        val pageBody = if (page.paragraphs.isEmpty()) {
            """<p class="page-note">This page did not yield extractable reflow text.</p>"""
        } else {
            page.paragraphs.joinToString(separator = "\n") { paragraph ->
                "<p>${paragraph.escapeForXhtml().replace("\n", "<br />")}</p>"
            }
        }
        """
        <div class="page-marker" id="${page.anchorId()}"><span>Page ${page.pageNumber}</span></div>
        $pageBody
        """.trimIndent()
    }

    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml">
          <head>
            <title>${"$bookTitle - ${section.title}".escapeForXhtml()}</title>
            <link rel="stylesheet" type="text/css" href="../styles/book.css"/>
          </head>
          <body>
            <section class="reflow-section">
              <h1 class="section-title">${section.title.escapeForXhtml()}</h1>
              $body
            </section>
          </body>
        </html>
    """.trimIndent().trimStart()
}

private fun addStoredEntry(zip: ZipOutputStream, name: String, content: String) {
    val bytes = content.toByteArray(StandardCharsets.UTF_8)
    val crc = CRC32().apply { update(bytes) }
    val entry = ZipEntry(name).apply {
        method = ZipEntry.STORED
        size = bytes.size.toLong()
        compressedSize = bytes.size.toLong()
        this.crc = crc.value
    }
    zip.putNextEntry(entry)
    zip.write(bytes)
    zip.closeEntry()
}

private fun addEntry(zip: ZipOutputStream, name: String, content: String) {
    zip.putNextEntry(ZipEntry(name))
    zip.write(content.toByteArray(StandardCharsets.UTF_8))
    zip.closeEntry()
}

private fun addFileEntry(
    zip: ZipOutputStream,
    name: String,
    sourceFile: File,
) {
    zip.putNextEntry(ZipEntry(name))
    sourceFile.inputStream().use { input ->
        input.copyTo(zip)
    }
    zip.closeEntry()
}

private fun String.escapeForXhtml(): String {
    return buildString(length) {
        for (char in this@escapeForXhtml) {
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(char)
            }
        }
    }
}

private fun PdfWorkspacePage.anchorId(): String = "page-${pageNumber.toString().padStart(4, '0')}"
