package com.epubreader.feature.library.internal.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.epubreader.core.ui.getStaticWindowInsets
import com.epubreader.feature.library.internal.BookSelectionActionBarActions
import com.epubreader.feature.library.internal.BookSelectionActionBarState
import com.epubreader.feature.library.internal.LibraryDialogActions
import com.epubreader.feature.library.internal.LibraryDialogState
import com.epubreader.feature.library.internal.LibraryScreenActions
import com.epubreader.feature.library.internal.LibraryScreenState
import com.epubreader.feature.library.internal.LibraryScreenSlots

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalMaterialApi::class,
)
@Composable
internal fun LibraryScreen(
    state: LibraryScreenState,
    actions: LibraryScreenActions,
    slots: LibraryScreenSlots,
    selectionBarState: BookSelectionActionBarState,
    selectionBarActions: BookSelectionActionBarActions,
    dialogState: LibraryDialogState,
    dialogActions: LibraryDialogActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = state.drawerState,
            gesturesEnabled = !state.selection.isBookSelectionMode || state.drawerState.isOpen,
            drawerContent = {
                LibraryDrawerContent(
                    state = state,
                    actions = actions.folderDrawer,
                )
            },
        ) {
            Scaffold(
                contentWindowInsets = getStaticWindowInsets(),
                snackbarHost = { SnackbarHost(state.snackbarHostState) },
                floatingActionButton = {
                    if (!state.selection.isBookSelectionMode) {
                        FloatingActionButton(onClick = actions.onAddBookClick) {
                            Icon(Icons.Default.Add, contentDescription = "Add Book")
                        }
                    }
                },
                topBar = {
                    LibraryTopBar(
                        state = state,
                        actions = actions,
                        extensionActions = slots.topBarActions,
                    )
                },
            ) { padding ->
                val pullRefreshState = rememberPullRefreshState(
                    refreshing = state.asyncState.libraryRefresh,
                    onRefresh = actions.onRefreshLibrary,
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .pullRefresh(pullRefreshState),
                ) {
                    LibraryBookGrid(
                        state = state,
                        actions = actions,
                    )

                    PullRefreshIndicator(
                        refreshing = state.asyncState.libraryRefresh,
                        state = pullRefreshState,
                        modifier = Modifier.align(Alignment.TopCenter),
                        backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.primary,
                    )

                    if (state.asyncState.importInFlight) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter),
                        )
                    }
                }
            }
        }

        slots.decorations.forEach { decoration ->
            decoration.content(this)
        }

        BookSelectionActionBar(
            state = selectionBarState,
            actions = selectionBarActions,
        )

        LibraryDialogHost(
            state = dialogState,
            actions = dialogActions,
        )
    }
}
