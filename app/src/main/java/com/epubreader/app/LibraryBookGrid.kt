package com.epubreader.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.epubreader.core.model.BookProgress
import com.epubreader.core.ui.BookItem
import com.epubreader.core.ui.RecentlyViewedStrip

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LibraryBookGrid(
    state: LibraryScreenState,
    actions: LibraryScreenActions,
) {
    val hasAnyBooks = state.books.isNotEmpty()

    if (!hasAnyBooks && !state.asyncState.libraryRefresh) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.LibraryBooks,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Text("Your library is empty", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Add EPUB files to start reading",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    val gridState = rememberLazyGridState()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(140.dp),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        state.lastOpenedBook?.let { book ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                RecentlyViewedStrip(
                    book = book,
                    bookFolder = state.bookFolderById[book.id] ?: RootLibraryName,
                    globalSettings = state.globalSettings,
                    progress = state.progressByBookId[book.id] ?: BookProgress(),
                    onOpen = actions.onOpenBook,
                )
            }
        }

        if (state.libraryItems.isEmpty() && !state.asyncState.libraryRefresh) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 64.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "This folder is empty",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        items(state.libraryItems, key = { it.id }) { book ->
            val isSelected = state.selection.selectedBookIds.contains(book.id)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .combinedClickable(
                        onClick = {
                            if (state.selection.isBookSelectionMode) {
                                actions.onToggleBookSelection(book)
                            } else {
                                actions.onOpenBook(book)
                            }
                        },
                        onLongClick = {
                            if (!state.selection.isBookSelectionMode) {
                                actions.onStartBookSelection(book)
                            }
                        },
                    ),
            ) {
                BookItem(
                    book = book,
                    globalSettings = state.globalSettings,
                    progress = state.progressByBookId[book.id] ?: BookProgress(),
                    isSelected = isSelected,
                )
            }
        }
    }
}
