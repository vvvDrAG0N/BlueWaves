package com.epubreader.feature.editbook

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AddTextOrImportChapterDialog(
    chapterCount: Int,
    defaultPosition: Int,
    onDismiss: () -> Unit,
    onAddChapter: (EditableChapterItem, Int) -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var position by rememberSaveable { mutableStateOf(defaultPosition.toString()) }
    var importedMarkup by rememberSaveable { mutableStateOf<String?>(null) }
    var importedFileName by rememberSaveable { mutableStateOf<String?>(null) }
    var importLoadInFlight by remember { mutableStateOf(false) }
    var importError by rememberSaveable { mutableStateOf<String?>(null) }
    val parsedPosition = position.toIntOrNull()?.coerceIn(1, chapterCount + 1)
    val isImportedMode = importedMarkup != null
    val canAdd = title.trim().isNotBlank() && parsedPosition != null && (isImportedMode || body.trim().isNotBlank())

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            importLoadInFlight = true
            val importedChapter = withContext(Dispatchers.IO) { loadImportedChapterDraft(context, uri) }
            importLoadInFlight = false
            val htmlContent = importedChapter?.content as? com.epubreader.core.model.BookNewChapterContent.HtmlDocument
            if (importedChapter != null && htmlContent != null) {
                title = importedChapter.title
                importedMarkup = htmlContent.markup
                importedFileName = htmlContent.fileNameHint
                body = ""
                importError = null
            } else {
                importError = "Pick an HTML or XHTML file."
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text("Add Chapter") },
        text = {
            Column(
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus(force = true) })
                },
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    trailingIcon = {
                        IconButton(
                            enabled = !importLoadInFlight,
                            onClick = { importPicker.launch(SupportedImportMimeTypes) },
                            modifier = Modifier.testTag("edit-book-action-import-html"),
                        ) {
                            if (importLoadInFlight) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                            } else {
                                Icon(Icons.Default.Language, contentDescription = "Import HTML XHTML")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("edit-book-add-title"),
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    enabled = !isImportedMode,
                    label = { Text("Text") },
                    placeholder = {
                        Text(if (isImportedMode) "HTML/XHTML imported. Text is disabled." else "Write chapter text")
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp).testTag("edit-book-add-body"),
                )
                importError?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                OutlinedTextField(
                    value = position,
                    onValueChange = { position = it.filter(Char::isDigit) },
                    label = { Text("Insert index") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("edit-book-add-position"),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canAdd,
                onClick = {
                    parsedPosition?.let { insertPosition ->
                        val chapter = if (isImportedMode) {
                            buildHtmlDraftChapter(title = title, markup = importedMarkup.orEmpty(), fileNameHint = importedFileName)
                        } else {
                            buildTextDraftChapter(title, body)
                        }
                        onAddChapter(chapter, insertPosition)
                    }
                },
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
            ) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface) }
        },
    )
}

@Composable
internal fun ChapterSelectionDialog(
    chapterCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (ChapterSelectionMode, Int, Int) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var mode by rememberSaveable { mutableStateOf(ChapterSelectionMode.SPECIFIC) }
    var first by rememberSaveable { mutableStateOf("1") }
    var second by rememberSaveable { mutableStateOf(if (chapterCount > 0) chapterCount.toString() else "1") }
    val parsedFirst = if (chapterCount > 0) first.toIntOrNull()?.coerceIn(1, chapterCount) else null
    val parsedSecond = if (chapterCount > 0) second.toIntOrNull()?.coerceIn(1, chapterCount) else null
    val needsSecondIndex = mode != ChapterSelectionMode.SPECIFIC
    val canConfirm = chapterCount > 0 && parsedFirst != null && (!needsSecondIndex || parsedSecond != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text("Selection") },
        text = {
            Column(
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus(force = true) })
                },
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectionModeCard("●", mode == ChapterSelectionMode.SPECIFIC, { mode = ChapterSelectionMode.SPECIFIC }, Modifier.weight(1f))
                    SelectionModeCard("●────●", mode == ChapterSelectionMode.RANGE, { mode = ChapterSelectionMode.RANGE }, Modifier.weight(1f))
                    SelectionModeCard("─●  ●─", mode == ChapterSelectionMode.OUTSIDE, { mode = ChapterSelectionMode.OUTSIDE }, Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = first,
                        onValueChange = { first = it.filter(Char::isDigit) },
                        label = { Text("Index 1") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    if (needsSecondIndex) {
                        OutlinedTextField(
                            value = second,
                            onValueChange = { second = it.filter(Char::isDigit) },
                            label = { Text("Index 2") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = canConfirm, onClick = { if (parsedFirst != null) onConfirm(mode, parsedFirst, parsedSecond ?: parsedFirst) }) {
                Text("Select")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
            ) { Text("Cancel Select") }
        },
    )
}

@Composable
private fun SelectionModeCard(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        tonalElevation = if (selected) 6.dp else 0.dp,
        shadowElevation = if (selected) 10.dp else 0.dp,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun PositionDialog(
    title: String,
    label: String,
    chapterCount: Int,
    defaultPosition: Int,
    maxPosition: Int = chapterCount + 1,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var position by rememberSaveable { mutableStateOf(defaultPosition.toString()) }
    val parsedPosition = position.toIntOrNull()?.coerceIn(1, maxPosition.coerceAtLeast(1))

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(title) },
        text = {
            Box(modifier = Modifier.pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus(force = true) }) }) {
                OutlinedTextField(
                    value = position,
                    onValueChange = { position = it.filter(Char::isDigit) },
                    label = { Text(label) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = parsedPosition != null, onClick = { parsedPosition?.let(onConfirm) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
            ) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface) }
        },
    )
}

@Composable
internal fun RenameChapterDialog(
    currentTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var title by rememberSaveable(currentTitle) { mutableStateOf(currentTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text("Rename Chapter") },
        text = {
            Box(modifier = Modifier.pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus(force = true) }) }) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = title.trim().isNotBlank(), onClick = { onConfirm(title) }) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
            ) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface) }
        },
    )
}

@Composable
internal fun SimpleConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val isDestructive = confirmLabel == "Delete" || confirmLabel == "Discard"
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (isDestructive) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
            ) { Text(dismissLabel) }
        },
    )
}
