package com.epubreader.feature.editbook

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.epubreader.core.model.BookCoverAction
import com.epubreader.core.model.BookCoverUpdate
import com.epubreader.core.model.BookEditRequest
import com.epubreader.core.model.EpubBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val SupportedCoverMimeTypes = arrayOf("image/png", "image/jpeg", "image/jpg", "image/gif", "image/webp")
private val SupportedImportMimeTypes = arrayOf("text/html", "application/xhtml+xml", "application/xml", "text/xml", "text/plain")
private val SupportedImportExtensions = setOf("html", "htm", "xhtml")

private enum class EditBookTab { DETAILS, CHAPTERS }

private data class RangeSelectionRequest(
    val title: String,
    val action: (Int, Int) -> Unit,
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
    var selectedTab by rememberSaveable(book.id) { mutableStateOf(EditBookTab.DETAILS) }
    var title by rememberSaveable(book.id) { mutableStateOf(book.title) }
    var author by rememberSaveable(book.id) { mutableStateOf(book.author) }
    var coverAction by remember(book.id) { mutableStateOf<BookCoverAction>(BookCoverAction.Keep) }
    var chapterItems by remember(book.id) { mutableStateOf(buildEditableChapterItems(book)) }
    var selectedChapterIds by remember(book.id) { mutableStateOf<Set<String>>(emptySet()) }
    var chapterQuery by rememberSaveable(book.id) { mutableStateOf("") }
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }
    var showDeleteSelectionDialog by remember { mutableStateOf(false) }
    var rangeSelectionRequest by remember { mutableStateOf<RangeSelectionRequest?>(null) }
    var showAddTextDialog by remember { mutableStateOf(false) }
    var showImportPositionDialog by remember { mutableStateOf(false) }
    var showMoveSelectionDialog by remember { mutableStateOf(false) }
    var renameTargetId by remember { mutableStateOf<String?>(null) }
    var pendingInsertPosition by remember { mutableIntStateOf(0) }
    var coverLoadInFlight by remember { mutableStateOf(false) }
    var importLoadInFlight by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val originalChapterItems = remember(book.id) { buildEditableChapterItems(book) }
    val chapterListState = rememberLazyListState()
    val filteredChapterItems = remember(chapterItems, chapterQuery) {
        val query = chapterQuery.trim()
        if (query.isBlank()) {
            chapterItems.mapIndexed(::IndexedValue)
        } else {
            chapterItems.mapIndexedNotNull { index, item ->
                val matches = item.title.contains(query, ignoreCase = true) ||
                    item.href.orEmpty().contains(query, ignoreCase = true)
                if (matches) IndexedValue(index, item) else null
            }
        }
    }
    val selectedChapterItems = remember(chapterItems, selectedChapterIds) {
        chapterItems.filter { it.id in selectedChapterIds }
    }
    val isDirty = title != book.title ||
        author != book.author ||
        coverAction != BookCoverAction.Keep ||
        chapterItems != originalChapterItems
    val canSave = title.trim().isNotBlank() && chapterItems.isNotEmpty() && !isSaving
    val hasPersistedSelection = selectedChapterItems.any(EditableChapterItem::isPersisted)
    val coverModel: Any? = when (val action = coverAction) {
        BookCoverAction.Keep -> book.coverPath?.takeIf { File(it).exists() }?.let(::File)
        BookCoverAction.Remove -> null
        is BookCoverAction.Replace -> action.cover.bytes
    }

    fun defaultInsertPosition(): Int {
        if (selectedChapterIds.isEmpty()) return chapterItems.size + 1
        val lastSelectedIndex = chapterItems.indexOfLast { it.id in selectedChapterIds }
        return if (lastSelectedIndex == -1) chapterItems.size + 1 else lastSelectedIndex + 2
    }

    fun requestExit() {
        if (isSaving) return
        if (isDirty) showUnsavedChangesDialog = true else onBack()
    }

    fun deleteSelection() {
        if (selectedChapterIds.isEmpty()) return
        val remainingItems = chapterItems.filterNot { it.id in selectedChapterIds }
        if (remainingItems.isEmpty()) {
            scope.launch { snackbarHostState.showSnackbar("A book needs at least one chapter.") }
            return
        }
        chapterItems = remainingItems
        selectedChapterIds = emptySet()
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onDismissError()
        }
    }

    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            coverLoadInFlight = true
            val coverUpdate = withContext(Dispatchers.IO) { loadCoverUpdate(context, uri) }
            coverLoadInFlight = false
            if (coverUpdate != null) {
                coverAction = BookCoverAction.Replace(coverUpdate)
            } else {
                snackbarHostState.showSnackbar("Pick a PNG, JPG, GIF, or WEBP image.")
            }
        }
    }

    val htmlImportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            importLoadInFlight = true
            val importedChapters = withContext(Dispatchers.IO) { loadImportedChapterDrafts(context, uris) }
            importLoadInFlight = false
            if (importedChapters.isEmpty()) {
                snackbarHostState.showSnackbar("Pick HTML or XHTML chapter files.")
            } else {
                chapterItems = insertChapterItems(chapterItems, pendingInsertPosition, importedChapters)
                selectedChapterIds = importedChapters.mapTo(linkedSetOf(), EditableChapterItem::id)
                selectedTab = EditBookTab.CHAPTERS
                snackbarHostState.showSnackbar("Imported ${importedChapters.size} chapter file(s).")
            }
        }
    }

    BackHandler(enabled = true, onBack = ::requestExit)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Edit Book") },
                navigationIcon = {
                    IconButton(enabled = !isSaving, onClick = ::requestExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        enabled = canSave,
                        onClick = {
                            onSave(
                                BookEditRequest(
                                    title = title.trim(),
                                    author = author.trim(),
                                    coverAction = coverAction,
                                    chapters = chapterItems.map(EditableChapterItem::toChapterEdit),
                                ),
                            )
                        },
                        modifier = Modifier.testTag("edit-book-save"),
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Default.Save, contentDescription = "Save")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                Tab(
                    selected = selectedTab == EditBookTab.DETAILS,
                    onClick = { selectedTab = EditBookTab.DETAILS },
                    text = { Text("Book Details") },
                    modifier = Modifier.testTag("edit-book-tab-details"),
                )
                Tab(
                    selected = selectedTab == EditBookTab.CHAPTERS,
                    onClick = { selectedTab = EditBookTab.CHAPTERS },
                    text = { Text("Chapters") },
                    modifier = Modifier.testTag("edit-book-tab-chapters"),
                )
            }

            when (selectedTab) {
                EditBookTab.DETAILS -> EditBookDetailsTab(
                    book = book,
                    title = title,
                    author = author,
                    coverModel = coverModel,
                    chapterCount = chapterItems.size,
                    isSaving = isSaving,
                    coverLoadInFlight = coverLoadInFlight,
                    onTitleChange = { title = it },
                    onAuthorChange = { author = it },
                    onPickCover = { coverPicker.launch(SupportedCoverMimeTypes) },
                    onRemoveCover = { coverAction = BookCoverAction.Remove },
                )

                EditBookTab.CHAPTERS -> EditBookChaptersTab(
                    chapterItems = chapterItems,
                    filteredChapterItems = filteredChapterItems,
                    selectedChapterIds = selectedChapterIds,
                    query = chapterQuery,
                    isSaving = isSaving,
                    importLoadInFlight = importLoadInFlight,
                    listState = chapterListState,
                    onQueryChange = { chapterQuery = it },
                    onToggleChapterSelection = { chapterId ->
                        selectedChapterIds = if (chapterId in selectedChapterIds) {
                            selectedChapterIds - chapterId
                        } else {
                            selectedChapterIds + chapterId
                        }
                    },
                    onClearSelection = { selectedChapterIds = emptySet() },
                    onRequestAddText = {
                        pendingInsertPosition = defaultInsertPosition()
                        showAddTextDialog = true
                    },
                    onRequestImportHtml = {
                        pendingInsertPosition = defaultInsertPosition()
                        showImportPositionDialog = true
                    },
                    onRequestRename = { renameTargetId = selectedChapterItems.singleOrNull()?.id },
                    onRequestDelete = {
                        if (selectedChapterIds.isEmpty()) return@EditBookChaptersTab
                        if (hasPersistedSelection) showDeleteSelectionDialog = true else deleteSelection()
                    },
                    onRequestSelectRange = {
                        rangeSelectionRequest = RangeSelectionRequest("Select From X To X") { start, end ->
                            selectedChapterIds = selectChapterRange(chapterItems, start, end)
                        }
                    },
                    onRequestSelectOutsideRange = {
                        rangeSelectionRequest = RangeSelectionRequest("Select Outside X To X") { start, end ->
                            selectedChapterIds = selectOutsideChapterRange(chapterItems, start, end)
                        }
                    },
                    onRequestMoveSelection = { showMoveSelectionDialog = true },
                )
            }

            if (isSaving) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Saving EPUB changes...")
                }
            }
        }
    }

    if (showUnsavedChangesDialog) {
        SimpleConfirmDialog(
            title = "Discard Changes?",
            message = "You have unsaved Edit Book changes.",
            confirmLabel = "Discard",
            dismissLabel = "Keep Editing",
            onDismiss = { showUnsavedChangesDialog = false },
            onConfirm = {
                showUnsavedChangesDialog = false
                onBack()
            },
        )
    }

    if (showDeleteSelectionDialog) {
        SimpleConfirmDialog(
            title = "Delete Selected Chapters?",
            message = "This will remove ${selectedChapterIds.size} selected chapter(s) from the EPUB.",
            confirmLabel = "Delete",
            dismissLabel = "Cancel",
            onDismiss = { showDeleteSelectionDialog = false },
            onConfirm = {
                showDeleteSelectionDialog = false
                deleteSelection()
            },
        )
    }

    rangeSelectionRequest?.let { request ->
        RangeSelectionDialog(
            title = request.title,
            chapterCount = chapterItems.size,
            onDismiss = { rangeSelectionRequest = null },
            onConfirm = { start, end ->
                request.action(start, end)
                rangeSelectionRequest = null
            },
        )
    }

    if (showAddTextDialog) {
        AddTextChapterDialog(
            chapterCount = chapterItems.size,
            defaultPosition = pendingInsertPosition.coerceAtLeast(1),
            onDismiss = { showAddTextDialog = false },
            onAddChapter = { chapterTitle, chapterBody, insertPosition ->
                chapterItems = insertChapterItems(
                    chapterItems,
                    insertPosition,
                    listOf(buildTextDraftChapter(chapterTitle, chapterBody)),
                )
                selectedTab = EditBookTab.CHAPTERS
                selectedChapterIds = emptySet()
                showAddTextDialog = false
            },
        )
    }

    if (showImportPositionDialog) {
        PositionDialog(
            title = "Import HTML/XHTML",
            label = "Insert at position",
            chapterCount = chapterItems.size,
            defaultPosition = pendingInsertPosition.coerceAtLeast(1),
            confirmLabel = "Pick Files",
            onDismiss = { showImportPositionDialog = false },
            onConfirm = { position ->
                pendingInsertPosition = position
                showImportPositionDialog = false
                htmlImportPicker.launch(SupportedImportMimeTypes)
            },
        )
    }

    if (showMoveSelectionDialog) {
        PositionDialog(
            title = "Move Selected Chapters",
            label = "New start position",
            chapterCount = chapterItems.size,
            defaultPosition = 1,
            confirmLabel = "Move",
            onDismiss = { showMoveSelectionDialog = false },
            onConfirm = { position ->
                chapterItems = moveSelectedChapterItems(chapterItems, selectedChapterIds, position)
                showMoveSelectionDialog = false
            },
        )
    }

    renameTargetId?.let { targetId ->
        chapterItems.firstOrNull { it.id == targetId }?.let { target ->
            RenameChapterDialog(
                currentTitle = target.title,
                onDismiss = { renameTargetId = null },
                onConfirm = { newTitle ->
                    chapterItems = chapterItems.map { item ->
                        if (item.id == targetId) item.copy(title = newTitle.trim()) else item
                    }
                    renameTargetId = null
                },
            )
        }
    }
}

@Composable
private fun EditBookDetailsTab(
    book: EpubBook,
    title: String,
    author: String,
    coverModel: Any?,
    chapterCount: Int,
    isSaving: Boolean,
    coverLoadInFlight: Boolean,
    onTitleChange: (String) -> Unit,
    onAuthorChange: (String) -> Unit,
    onPickCover: () -> Unit,
    onRemoveCover: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(0.36f)
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
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(32.dp))
                                Text("No cover", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.weight(0.64f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = onTitleChange,
                            modifier = Modifier.fillMaxWidth().testTag("edit-book-title"),
                            label = { Text("Title") },
                            singleLine = true,
                            enabled = !isSaving,
                        )
                        OutlinedTextField(
                            value = author,
                            onValueChange = onAuthorChange,
                            modifier = Modifier.fillMaxWidth().testTag("edit-book-author"),
                            label = { Text("Author") },
                            singleLine = true,
                            enabled = !isSaving,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                enabled = !isSaving && !coverLoadInFlight,
                                onClick = onPickCover,
                                modifier = Modifier.weight(1f),
                            ) {
                                if (coverLoadInFlight) {
                                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                } else {
                                    Icon(Icons.Default.Image, contentDescription = null)
                                }
                                Spacer(Modifier.width(8.dp))
                                Text("Replace")
                            }
                            FilledTonalButton(
                                enabled = !isSaving && coverModel != null,
                                onClick = onRemoveCover,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Remove")
                            }
                        }
                    }
                }

                HorizontalDivider()

                SummaryRow("Source", book.sourceFormat.name)
                SummaryRow("Total Chapters", chapterCount.toString())
                SummaryRow("Book ID", book.id, monospace = true)
            }
        }
    }
}

@Composable
private fun EditBookChaptersTab(
    chapterItems: List<EditableChapterItem>,
    filteredChapterItems: List<IndexedValue<EditableChapterItem>>,
    selectedChapterIds: Set<String>,
    query: String,
    isSaving: Boolean,
    importLoadInFlight: Boolean,
    listState: LazyListState,
    onQueryChange: (String) -> Unit,
    onToggleChapterSelection: (String) -> Unit,
    onClearSelection: () -> Unit,
    onRequestAddText: () -> Unit,
    onRequestImportHtml: () -> Unit,
    onRequestRename: () -> Unit,
    onRequestDelete: () -> Unit,
    onRequestSelectRange: () -> Unit,
    onRequestSelectOutsideRange: () -> Unit,
    onRequestMoveSelection: () -> Unit,
) {
    val selectedCount = selectedChapterIds.size
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search chapters") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            enabled = !isSaving,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("${chapterItems.size} total chapters", fontWeight = FontWeight.SemiBold)
                Text("$selectedCount selected", style = MaterialTheme.typography.bodySmall)
            }
            if (importLoadInFlight) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }

        ActionStrip {
            FilledTonalButton(enabled = !isSaving, onClick = onRequestAddText) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add Text")
            }
            FilledTonalButton(enabled = !isSaving && !importLoadInFlight, onClick = onRequestImportHtml) {
                Icon(Icons.Default.UploadFile, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Import HTML/XHTML")
            }
            FilledTonalButton(enabled = !isSaving && selectedCount == 1, onClick = onRequestRename) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Rename")
            }
            FilledTonalButton(enabled = !isSaving && selectedCount > 0, onClick = onRequestMoveSelection) {
                Text("Move To Position")
            }
            FilledTonalButton(enabled = !isSaving && selectedCount > 0, onClick = onRequestDelete) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Delete")
            }
        }

        ActionStrip {
            FilledTonalButton(enabled = !isSaving, onClick = onRequestSelectRange) {
                Text("Select X To X")
            }
            FilledTonalButton(enabled = !isSaving, onClick = onRequestSelectOutsideRange) {
                Text("Select Outside X To X")
            }
            FilledTonalButton(enabled = !isSaving && selectedCount > 0, onClick = onClearSelection) {
                Text("Clear Selection")
            }
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.weight(1f),
        ) {
            if (filteredChapterItems.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No chapters match this search.")
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(12.dp),
                ) {
                    items(filteredChapterItems, key = { it.value.id }) { indexedItem ->
                        ChapterRow(
                            position = indexedItem.index + 1,
                            chapter = indexedItem.value,
                            selected = indexedItem.value.id in selectedChapterIds,
                            onToggleSelection = { onToggleChapterSelection(indexedItem.value.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterRow(
    position: Int,
    chapter: EditableChapterItem,
    selected: Boolean,
    onToggleSelection: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleSelection),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = selected, onCheckedChange = { onToggleSelection() })
            Text(position.toString(), modifier = Modifier.width(32.dp), fontWeight = FontWeight.Bold)
            Column(Modifier.weight(1f)) {
                Text(chapter.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when (chapter.source) {
                        EditableChapterSource.EXISTING -> chapter.href.orEmpty()
                        EditableChapterSource.NEW_TEXT -> "Draft text chapter"
                        EditableChapterSource.IMPORTED_HTML -> "Imported HTML/XHTML"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, monospace: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default)
    }
}

@Composable
private fun ActionStrip(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun AddTextChapterDialog(
    chapterCount: Int,
    defaultPosition: Int,
    onDismiss: () -> Unit,
    onAddChapter: (String, String, Int) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var position by rememberSaveable { mutableStateOf(defaultPosition.toString()) }
    val parsedPosition = position.toIntOrNull()?.coerceIn(1, chapterCount + 1)
    val canAdd = title.trim().isNotBlank() && body.trim().isNotBlank() && parsedPosition != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Text Chapter") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Chapter title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit-book-add-title"),
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Chapter text") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp).testTag("edit-book-add-body"),
                )
                OutlinedTextField(
                    value = position,
                    onValueChange = { position = it.filter(Char::isDigit) },
                    label = { Text("Insert at position") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("edit-book-add-position"),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = canAdd, onClick = {
                parsedPosition?.let { onAddChapter(title, body, it) }
            }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun RangeSelectionDialog(
    title: String,
    chapterCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    var start by rememberSaveable { mutableStateOf("1") }
    var end by rememberSaveable { mutableStateOf(if (chapterCount > 0) chapterCount.toString() else "1") }
    val parsedStart = if (chapterCount > 0) start.toIntOrNull()?.coerceIn(1, chapterCount) else null
    val parsedEnd = if (chapterCount > 0) end.toIntOrNull()?.coerceIn(1, chapterCount) else null
    val canConfirm = chapterCount > 0 && parsedStart != null && parsedEnd != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = start,
                    onValueChange = { start = it.filter(Char::isDigit) },
                    label = { Text("Start position") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = end,
                    onValueChange = { end = it.filter(Char::isDigit) },
                    label = { Text("End position") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = canConfirm, onClick = {
                if (parsedStart != null && parsedEnd != null) onConfirm(parsedStart, parsedEnd)
            }) {
                Text("Select")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PositionDialog(
    title: String,
    label: String,
    chapterCount: Int,
    defaultPosition: Int,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var position by rememberSaveable { mutableStateOf(defaultPosition.toString()) }
    val parsedPosition = position.toIntOrNull()?.coerceIn(1, chapterCount + 1)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = position,
                onValueChange = { position = it.filter(Char::isDigit) },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = parsedPosition != null, onClick = {
                parsedPosition?.let(onConfirm)
            }) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RenameChapterDialog(
    currentTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var title by rememberSaveable(currentTitle) { mutableStateOf(currentTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Chapter") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Chapter title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = title.trim().isNotBlank(), onClick = { onConfirm(title) }) {
                Text("Rename")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SimpleConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } },
    )
}

private fun loadCoverUpdate(context: Context, uri: Uri): BookCoverUpdate? {
    val mimeType = context.contentResolver.getType(uri)?.lowercase().orEmpty()
    if (mimeType !in SupportedCoverMimeTypes) return null
    val fileName = queryDisplayName(context, uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "cover"
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    return BookCoverUpdate(fileName = fileName, mimeType = mimeType, bytes = bytes)
}

private fun loadImportedChapterDrafts(context: Context, uris: List<Uri>): List<EditableChapterItem> {
    return uris.mapNotNull { uri ->
        val fileName = queryDisplayName(context, uri)
        val mimeType = context.contentResolver.getType(uri)?.lowercase().orEmpty()
        val extension = fileName?.substringAfterLast('.', "")?.lowercase().orEmpty()
        if (mimeType !in SupportedImportMimeTypes && extension !in SupportedImportExtensions) {
            return@mapNotNull null
        }
        val markup = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        } ?: return@mapNotNull null
        buildHtmlDraftChapter(
            title = inferImportedChapterTitle(fileName, markup),
            markup = markup,
            fileNameHint = fileName,
        )
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (columnIndex != -1) cursor.getString(columnIndex) else null
        } else {
            null
        }
    }
}
