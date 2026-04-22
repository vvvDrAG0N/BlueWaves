package com.epubreader.data.parser

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.epubreader.core.model.BookFormat
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

internal fun queryFileSize(context: Context, uri: Uri): Long? {
    return context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
        descriptor.statSize
    }
}

internal fun queryDisplayName(context: Context, uri: Uri): String? {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (columnIndex != -1) {
                return cursor.getString(columnIndex)
            }
        }
    }

    return uri.lastPathSegment?.substringAfterLast('/')
}

internal fun readHeaderBytes(context: Context, uri: Uri, size: Int = 8): ByteArray {
    return context.contentResolver.openInputStream(uri)?.use { input ->
        ByteArray(size).also { buffer ->
            val read = input.read(buffer)
            if (read > 0) {
                return@use buffer.copyOf(read)
            }
        }
    } ?: ByteArray(0)
}

internal fun inspectZipSource(
    context: Context,
    uri: Uri,
): ArchiveInspectionResult {
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(BufferedInputStream(input)).use { zipInput ->
                val entryNames = mutableListOf<String>()
                var looksLikeEpubContainer = false
                var entry = zipInput.nextEntry
                while (entry != null) {
                    val entryName = entry.name.orEmpty()
                    entryNames += entryName
                    if (entryName.equals("META-INF/container.xml", ignoreCase = true)) {
                        looksLikeEpubContainer = true
                    } else if (entryName == "mimetype") {
                        val mimetype = String(zipInput.readBytes(), Charsets.UTF_8).trim()
                        if (mimetype == EPUB_MIME_TYPE) {
                            looksLikeEpubContainer = true
                        }
                    }
                    zipInput.closeEntry()
                    entry = zipInput.nextEntry
                }
                inspectArchiveEntries(entryNames, looksLikeEpubContainer)
            }
        } ?: ArchiveInspectionResult.Rejected(ImportFailureReason.ReadFailed)
    } catch (_: Exception) {
        ArchiveInspectionResult.Rejected(ImportFailureReason.ReadFailed)
    }
}

internal fun copyUriToFile(
    context: Context,
    uri: Uri,
    targetFile: File,
): Boolean {
    return context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(targetFile).use { output ->
            input.copyTo(output)
        }
        true
    } ?: false
}

internal fun extractArchiveEntry(
    context: Context,
    uri: Uri,
    entryPath: String,
    targetFile: File,
): Boolean {
    return context.contentResolver.openInputStream(uri)?.use { input ->
        ZipInputStream(BufferedInputStream(input)).use { zipInput ->
            var entry = zipInput.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name == entryPath) {
                    FileOutputStream(targetFile).use { output ->
                        zipInput.copyTo(output)
                    }
                    zipInput.closeEntry()
                    return@use true
                }
                zipInput.closeEntry()
                entry = zipInput.nextEntry
            }
            false
        }
    } ?: false
}

internal fun stagedStoredBookFile(bookFolder: File, format: BookFormat): File {
    return File(bookFolder, "${storedFileName(format)}.importing")
}

internal fun cleanupFailedImport(
    bookFolder: File,
    stagedTargetFile: File,
    folderExisted: Boolean,
) {
    if (stagedTargetFile.exists()) {
        stagedTargetFile.delete()
    }
    if (!folderExisted && bookFolder.exists()) {
        bookFolder.deleteRecursively()
    }
}

internal fun storedBookFile(bookFolder: File, format: BookFormat): File {
    return File(bookFolder, storedFileName(format))
}

internal fun storedFileName(format: BookFormat): String {
    return when (format) {
        BookFormat.EPUB -> EPUB_ARCHIVE_FILE_NAME
        BookFormat.PDF -> PDF_DOCUMENT_FILE_NAME
    }
}

internal fun cleanupArtifactsForNativeEpub(
    bookFolder: File,
    workspaceDirName: String,
) {
    listOf(
        File(bookFolder, GENERATED_EPUB_FILE_NAME),
        File(bookFolder, PDF_DOCUMENT_FILE_NAME),
        File(bookFolder, LEGACY_PDF_DOCUMENT_FILE_NAME),
        File(bookFolder, "$GENERATED_EPUB_FILE_NAME.importing"),
    ).filter(File::exists).forEach(File::delete)
    File(bookFolder, workspaceDirName)
        .takeIf(File::exists)
        ?.deleteRecursively()
}

internal fun cleanupArtifactsForPdfImport(
    bookFolder: File,
    workspaceDirName: String,
) {
    File(bookFolder, EPUB_ARCHIVE_FILE_NAME)
        .takeIf(File::exists)
        ?.delete()
    File(bookFolder, GENERATED_EPUB_FILE_NAME)
        .takeIf(File::exists)
        ?.delete()
    File(bookFolder, "$GENERATED_EPUB_FILE_NAME.importing")
        .takeIf(File::exists)
        ?.delete()
    File(bookFolder, workspaceDirName)
        .takeIf(File::exists)
        ?.deleteRecursively()
}
