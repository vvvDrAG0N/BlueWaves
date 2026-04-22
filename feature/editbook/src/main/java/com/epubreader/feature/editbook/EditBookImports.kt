package com.epubreader.feature.editbook

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.epubreader.core.model.BookCoverUpdate

internal fun loadCoverUpdate(context: Context, uri: Uri): BookCoverUpdate? {
    val mimeType = context.contentResolver.getType(uri)?.lowercase().orEmpty()
    if (mimeType !in SupportedCoverMimeTypes) return null
    val fileName = queryDisplayName(context, uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "cover"
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    return BookCoverUpdate(fileName = fileName, mimeType = mimeType, bytes = bytes)
}

internal fun loadImportedChapterDraft(context: Context, uri: Uri): EditableChapterItem? {
    val fileName = queryDisplayName(context, uri)
    val mimeType = context.contentResolver.getType(uri)?.lowercase().orEmpty()
    val extension = fileName?.substringAfterLast('.', "")?.lowercase().orEmpty()
    if (mimeType !in SupportedImportMimeTypes && extension !in SupportedImportExtensions) {
        return null
    }
    val markup = context.contentResolver.openInputStream(uri)?.use { input ->
        input.readBytes().toString(Charsets.UTF_8)
    } ?: return null
    return buildHtmlDraftChapter(
        title = inferImportedChapterTitle(fileName, markup),
        markup = markup,
        fileNameHint = fileName,
    )
}

internal fun queryDisplayName(context: Context, uri: Uri): String? {
    return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (columnIndex != -1) cursor.getString(columnIndex) else null
        } else {
            null
        }
    }
}
