package com.epubreader.app

import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal fun createMinimalTestEpub(cacheDir: File, namePrefix: String): File {
    val epubFile = File(cacheDir, "$namePrefix-${System.currentTimeMillis()}.epub")
    ZipOutputStream(FileOutputStream(epubFile)).use { zip ->
        addStoredEntry(zip, "mimetype", "application/epub+zip")
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
            """.trimIndent(),
        )
        addEntry(
            zip,
            "OEBPS/content.opf",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" unique-identifier="BookId" version="2.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>Background Conversion EPUB</dc:title>
                <dc:creator>Blue Waves</dc:creator>
                <dc:identifier id="BookId">urn:uuid:background-conversion-epub</dc:identifier>
                <dc:language>en</dc:language>
              </metadata>
              <manifest>
                <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                <item id="chapter1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine toc="ncx">
                <itemref idref="chapter1"/>
              </spine>
            </package>
            """.trimIndent(),
        )
        addEntry(
            zip,
            "OEBPS/toc.ncx",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
              <head/>
              <docTitle><text>Background Conversion EPUB</text></docTitle>
              <navMap>
                <navPoint id="navPoint-1" playOrder="1">
                  <navLabel><text>Chapter 1</text></navLabel>
                  <content src="chapter1.xhtml"/>
                </navPoint>
              </navMap>
            </ncx>
            """.trimIndent(),
        )
        addEntry(
            zip,
            "OEBPS/chapter1.xhtml",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
              <head><title>Chapter 1</title></head>
              <body><p>Hello from the EPUB reader.</p></body>
            </html>
            """.trimIndent(),
        )
    }
    return epubFile
}

internal fun createUnreadableGeneratedPdfFallbackEpub(
    outputFile: File,
    title: String,
    author: String,
) {
    ZipOutputStream(FileOutputStream(outputFile)).use { zip ->
        addStoredEntry(zip, "mimetype", "application/epub+zip")
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
            """.trimIndent(),
        )
        addEntry(
            zip,
            "OEBPS/content.opf",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" unique-identifier="BookId" version="2.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>$title</dc:title>
                <dc:creator>$author</dc:creator>
                <dc:identifier id="BookId">urn:uuid:corrupt-converted-book</dc:identifier>
                <dc:language>en</dc:language>
              </metadata>
              <manifest>
                <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                <item id="section1" href="sections/section-0001.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine toc="ncx">
                <itemref idref="section1"/>
              </spine>
            </package>
            """.trimIndent(),
        )
        addEntry(
            zip,
            "OEBPS/toc.ncx",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
              <head/>
              <docTitle><text>$title</text></docTitle>
              <navMap>
                <navPoint id="navPoint-1" playOrder="1">
                  <navLabel><text>Page 1</text></navLabel>
                  <content src="sections/section-0001.xhtml#page-0001"/>
                </navPoint>
              </navMap>
            </ncx>
            """.trimIndent(),
        )
        addEntry(
            zip,
            "OEBPS/sections/section-0001.xhtml",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
              <head><title></title></head>
              <body><section id="page-0001"></section></body>
            </html>
            """.trimIndent(),
        )
    }
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
