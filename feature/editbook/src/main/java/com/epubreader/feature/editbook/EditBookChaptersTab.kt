package com.epubreader.feature.editbook

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun EditBookChaptersTab(
    chapterItems: List<EditableChapterItem>,
    filteredChapterItems: List<IndexedValue<EditableChapterItem>>,
    selectedChapterIds: Set<String>,
    query: String,
    chapterDisplaySort: ChapterDisplaySort,
    isSaving: Boolean,
    listState: LazyListState,
    onQueryChange: (String) -> Unit,
    onToggleChapterSelection: (String) -> Unit,
    onToggleAllSelection: () -> Unit,
    onRequestAddText: () -> Unit,
    onRequestRename: () -> Unit,
    onRequestDelete: () -> Unit,
    onRequestSelectionDialog: () -> Unit,
    onRequestMoveSelection: () -> Unit,
    onRequestGoToChapter: () -> Unit,
    onToggleSort: () -> Unit,
) {
    val selectedCount = selectedChapterIds.size
    val toggleSelectionDescription = if (selectedCount == 0) "Select all chapters" else "Unselect all chapters"
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f).testTag("edit-book-search"),
                label = { Text("Search") },
                placeholder = { Text("Index, title, or href") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                enabled = !isSaving,
            )
            ChapterActionButton(
                enabled = !isSaving && chapterItems.isNotEmpty(),
                onClick = onRequestGoToChapter,
                contentDescription = "Go to chapter",
                testTag = "edit-book-action-go-to",
                icon = { Icon(Icons.Default.MyLocation, contentDescription = null) },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChapterStatChip(
                icon = Icons.Default.Checklist,
                value = chapterItems.size.toString(),
                label = "Total",
                modifier = Modifier.weight(1f),
            )
            ChapterStatChip(
                icon = Icons.Default.CheckCircle,
                value = selectedCount.toString(),
                label = "Selected",
                modifier = Modifier.weight(1f),
            )
        }

        ActionStrip {
            ActionGroup {
                ChapterActionButton(
                    enabled = !isSaving,
                    onClick = onRequestAddText,
                    contentDescription = "Add or import chapter",
                    testTag = "edit-book-action-add-text",
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                )
            }
            ActionGroup {
                ChapterActionButton(
                    enabled = !isSaving,
                    onClick = onRequestSelectionDialog,
                    contentDescription = "Selection tools",
                    testTag = "edit-book-action-select",
                    icon = { Icon(Icons.Default.Checklist, contentDescription = null) },
                )
                ChapterActionButton(
                    enabled = !isSaving,
                    onClick = onToggleAllSelection,
                    contentDescription = toggleSelectionDescription,
                    testTag = "edit-book-action-toggle-all",
                    icon = {
                        Icon(
                            imageVector = if (selectedCount == 0) Icons.Default.SelectAll else Icons.Default.Deselect,
                            contentDescription = null,
                        )
                    },
                )
                ChapterActionButton(
                    enabled = !isSaving && selectedCount > 0,
                    onClick = onRequestMoveSelection,
                    contentDescription = "Move selected chapters",
                    testTag = "edit-book-action-move",
                    icon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) },
                )
                ChapterActionButton(
                    enabled = !isSaving && selectedCount == 1,
                    onClick = onRequestRename,
                    contentDescription = "Rename selected chapter",
                    testTag = "edit-book-action-rename",
                    icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                )
                ChapterActionButton(
                    enabled = !isSaving && selectedCount > 0,
                    onClick = onRequestDelete,
                    contentDescription = "Delete selected chapters",
                    testTag = "edit-book-action-delete",
                    icon = { Icon(Icons.Default.Delete, contentDescription = null) },
                )
                ChapterActionButton(
                    enabled = !isSaving && chapterItems.isNotEmpty(),
                    onClick = onToggleSort,
                    contentDescription = if (chapterDisplaySort == ChapterDisplaySort.ASCENDING) {
                        "Sort chapters descending"
                    } else {
                        "Sort chapters ascending"
                    },
                    testTag = "edit-book-action-sort",
                    icon = {
                        Icon(
                            imageVector = if (chapterDisplaySort == ChapterDisplaySort.ASCENDING) {
                                Icons.Default.ArrowDownward
                            } else {
                                Icons.Default.ArrowUpward
                            },
                            contentDescription = null,
                        )
                    },
                )
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
        onClick = onToggleSelection,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onToggleSelection, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (selected) "Deselect" else "Select",
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
            }
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$position.",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = chapter.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = when (chapter.source) {
                        EditableChapterSource.EXISTING -> chapter.href.orEmpty()
                        EditableChapterSource.NEW_TEXT -> "Draft text chapter"
                        EditableChapterSource.IMPORTED_HTML -> "Imported HTML/XHTML"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ChapterStatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("$value $label", fontWeight = FontWeight.SemiBold)
        }
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
private fun ActionGroup(content: @Composable RowScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(18.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun ChapterActionButton(
    enabled: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    testTag: String,
    icon: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = if (enabled) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        },
        modifier = Modifier
            .size(40.dp)
            .testTag(testTag)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            icon()
        }
    }
}
