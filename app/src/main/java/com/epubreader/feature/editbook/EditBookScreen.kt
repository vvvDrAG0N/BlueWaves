package com.epubreader.feature.editbook

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.epubreader.core.model.BookChapterAddition
import com.epubreader.core.model.BookCoverUpdate
import com.epubreader.core.model.BookEditRequest
import com.epubreader.core.model.EpubBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private val SupportedCoverMimeTypes = arrayOf(
    "image/png",
    "image/jpeg",
    "image/jpg",
    "image/gif",
    "image/webp",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBookScreen(
    book: EpubBook,
    isSaving: Boolean,
    errorMessage: String?,
    onDismissError: () -> Unit,
    onBack: () -> Unit,
    onSave: (BookEditRequest) -> Unit,
) {
    var title by rememberSaveable(book.id) { mutableStateOf(book.title) }
    var author by rememberSaveable(book.id) { mutableStateOf(book.author) }
    var selectedCover by remember(book.id) { mutableStateOf<BookCoverUpdate?>(null) }
    var chapterItems by remember(book.id) { mutableStateOf(buildEditableChapterItems(book)) }
    var showAddChapterDialog by remember { mutableStateOf(false) }
    var coverLoadInFlight by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(errorMessage) {
        errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            onDismissError()
        }
    }

    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            coverLoadInFlight = true
            val coverUpdate = withContext(Dispatchers.IO) {
                loadCoverUpdate(context, uri)
            }
            coverLoadInFlight = false
            if (coverUpdate != null) {
                selectedCover = coverUpdate
            } else {
                snackbarHostState.showSnackbar("Pick a PNG, JPG, GIF, or WEBP image.")
            }
        }
    }

    val canDeleteChapter = chapterItems.size > 1
    val canSave = title.trim().isNotBlank() && chapterItems.isNotEmpty() && !isSaving
    val coverModel: Any? = selectedCover?.bytes
        ?: book.coverPath
            ?.takeIf { File(it).exists() }
            ?.let(::File)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Edit Book") },
                navigationIcon = {
                    IconButton(
                        enabled = !isSaving,
                        onClick = onBack,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        enabled = canSave,
                        onClick = {
                            val deletedChapterHrefs = book.spineHrefs
                                .filterNot { existingHref ->
                                    chapterItems.any { item -> item.href == existingHref }
                                }
                                .toSet()
                            val addedChapters = chapterItems
                                .filterNot(EditableChapterItem::isPersisted)
                                .map { item ->
                                    BookChapterAddition(
                                        title = item.title,
                                        body = item.body,
                                    )
                                }

                            onSave(
                                BookEditRequest(
                                    title = title.trim(),
                                    author = author.trim(),
                                    coverUpdate = selectedCover,
                                    deletedChapterHrefs = deletedChapterHrefs,
                                    addedChapters = addedChapters,
                                ),
                            )
                        },
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Icon(Icons.Default.Save, contentDescription = "Save")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Book Details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(0.38f)
                                .aspectRatio(0.7f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (coverModel != null) {
                                AsyncImage(
                                    model = coverModel,
                                    contentDescription = "Cover preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = "No custom cover",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.weight(0.62f),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Title") },
                                enabled = !isSaving,
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = author,
                                onValueChange = { author = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Author") },
                                enabled = !isSaving,
                                singleLine = true,
                            )
                            FilledTonalButton(
                                enabled = !isSaving && !coverLoadInFlight,
                                onClick = { coverPicker.launch(SupportedCoverMimeTypes) },
                            ) {
                                if (coverLoadInFlight) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp),
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = null,
                                    )
                                }
                                Spacer(Modifier.size(8.dp))
                                Text(if (selectedCover == null) "Replace Cover" else "Change Cover")
                            }
                        }
                    }
                }
            }

            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "Chapters",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "New chapters append to the end of the book.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        FilledTonalButton(
                            enabled = !isSaving,
                            onClick = { showAddChapterDialog = true },
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Add Chapter")
                        }
                    }

                    chapterItems.forEachIndexed { index, chapter ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${index + 1}. ${chapter.title}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = if (chapter.isPersisted) {
                                            chapter.href.orEmpty()
                                        } else {
                                            "New chapter"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }

                                IconButton(
                                    enabled = !isSaving && canDeleteChapter,
                                    onClick = {
                                        if (chapterItems.size > 1) {
                                            chapterItems = chapterItems.filterNot { it.id == chapter.id }
                                        }
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete chapter ${chapter.title}",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isSaving) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = "Saving EPUB changes...",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }

    if (showAddChapterDialog) {
        AddChapterDialog(
            onDismiss = { showAddChapterDialog = false },
            onAddChapter = { chapterTitle, chapterBody ->
                chapterItems = chapterItems + EditableChapterItem(
                    id = "draft-${UUID.randomUUID()}",
                    title = chapterTitle.trim(),
                    href = null,
                    body = chapterBody.trim(),
                    isPersisted = false,
                )
                showAddChapterDialog = false
            },
        )
    }
}

@Composable
private fun AddChapterDialog(
    onDismiss: () -> Unit,
    onAddChapter: (String, String) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    val canAdd = title.trim().isNotBlank() && body.trim().isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Chapter") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Chapter title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Chapter text") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canAdd,
                onClick = {
                    if (canAdd) {
                        onAddChapter(title, body)
                    }
                },
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private data class EditableChapterItem(
    val id: String,
    val title: String,
    val href: String?,
    val body: String,
    val isPersisted: Boolean,
)

private fun buildEditableChapterItems(book: EpubBook): List<EditableChapterItem> {
    val tocTitlesByHref = book.toc.associate { tocItem ->
        cleanEditableHref(tocItem.href) to stripEditableTocNumbering(tocItem.title)
    }

    return book.spineHrefs.mapIndexed { index, href ->
        EditableChapterItem(
            id = href,
            title = tocTitlesByHref[cleanEditableHref(href)]
                ?.takeIf { it.isNotBlank() }
                ?: fallbackEditableTitle(href, index),
            href = href,
            body = "",
            isPersisted = true,
        )
    }
}

private fun stripEditableTocNumbering(title: String): String {
    return title.replace(Regex("""^\s*\d+(?:\.\d+)*\.\s*"""), "").trim()
}

private fun fallbackEditableTitle(
    href: String,
    index: Int,
): String {
    return href.substringAfterLast('/')
        .substringBeforeLast('.')
        .replace('_', ' ')
        .replace('-', ' ')
        .replaceFirstChar { character ->
            if (character.isLowerCase()) character.titlecase() else character.toString()
        }
        .ifBlank { "Chapter ${index + 1}" }
}

private fun cleanEditableHref(rawHref: String): String {
    return rawHref.substringBefore("#").removePrefix("/").trim()
}

private fun loadCoverUpdate(
    context: Context,
    uri: Uri,
): BookCoverUpdate? {
    val mimeType = context.contentResolver.getType(uri)?.lowercase().orEmpty()
    if (mimeType !in SupportedCoverMimeTypes) {
        return null
    }

    val fileName = context.contentResolver.query(
        uri,
        arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val columnIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (columnIndex != -1) cursor.getString(columnIndex) else null
        } else {
            null
        }
    } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "cover"

    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        input.readBytes()
    } ?: return null

    return BookCoverUpdate(
        fileName = fileName,
        mimeType = mimeType,
        bytes = bytes,
    )
}
