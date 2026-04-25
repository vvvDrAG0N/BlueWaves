package com.epubreader.feature.reader.internal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.epubreader.feature.reader.ReaderChromeCallbacks
import com.epubreader.feature.reader.ReaderChromeState
import com.epubreader.feature.reader.TocSort

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderTocDrawerContent(
    state: ReaderChromeState,
    callbacks: ReaderChromeCallbacks,
) {
    ModalDrawerSheet(
        drawerContainerColor = state.themeColors.background,
        drawerContentColor = state.themeColors.foreground,
    ) {
        var showChapterInputInToc by remember { mutableStateOf(false) }
        var inputChapter by remember { mutableStateOf("") }
        val focusRequester = remember { FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current
        val totalChapters = state.book.spineHrefs.size

        val performJump = {
            val targetChapter = inputChapter.toIntOrNull()?.coerceIn(1, totalChapters)
                ?: (state.currentChapterIndex + 1)
            val targetIndex = (targetChapter - 1).coerceIn(0, totalChapters - 1)
            keyboardController?.hide()
            callbacks.onJumpToChapter(targetIndex)
            inputChapter = ""
            showChapterInputInToc = false
            callbacks.onCloseToc()
        }

        LaunchedEffect(showChapterInputInToc, state.drawerState.currentValue) {
            if (showChapterInputInToc && state.drawerState.isOpen) {
                focusRequester.requestFocus()
            } else {
                keyboardController?.hide()
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = callbacks.onToggleTocSort) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = "Toggle Sort",
                    modifier = if (state.tocSort == TocSort.Ascending) {
                        Modifier.graphicsLayer { rotationX = 180f }
                    } else {
                        Modifier
                    },
                )
            }
            IconButton(onClick = callbacks.onLocateCurrentChapterInToc) {
                Icon(Icons.Default.MyLocation, contentDescription = "Locate Current")
            }

            if (showChapterInputInToc) {
                BasicTextField(
                    value = inputChapter,
                    onValueChange = {
                        if (it.all { char -> char.isDigit() } && it.length <= 4) {
                            inputChapter = it
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .padding(horizontal = 4.dp)
                        .focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { performJump() }),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    decorationBox = { innerTextField ->
                        OutlinedTextFieldDefaults.DecorationBox(
                            value = inputChapter,
                            innerTextField = innerTextField,
                            enabled = true,
                            singleLine = true,
                            visualTransformation = VisualTransformation.None,
                            interactionSource = remember { MutableInteractionSource() },
                            placeholder = {
                                Text(
                                    "1-$totalChapters",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = state.themeColors.foreground.copy(alpha = 0.5f),
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            container = {
                                OutlinedTextFieldDefaults.Container(
                                    enabled = true,
                                    isError = false,
                                    interactionSource = remember { MutableInteractionSource() },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedBorderColor = state.themeColors.foreground.copy(alpha = 0.5f),
                                        unfocusedBorderColor = state.themeColors.foreground.copy(alpha = 0.1f),
                                        focusedTextColor = state.themeColors.foreground,
                                        unfocusedTextColor = state.themeColors.foreground,
                                        focusedPlaceholderColor = state.themeColors.foreground.copy(alpha = 0.5f),
                                        unfocusedPlaceholderColor = state.themeColors.foreground.copy(alpha = 0.5f),
                                    ),
                                    shape = MaterialTheme.shapes.small,
                                )
                            },
                        )
                    },
                )
            } else {
                Spacer(Modifier.weight(1f))
            }

            IconButton(onClick = { showChapterInputInToc = !showChapterInputInToc }) {
                Icon(
                    imageVector = if (showChapterInputInToc) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = "Go to Chapter",
                    tint = if (showChapterInputInToc) {
                        MaterialTheme.colorScheme.error
                    } else {
                        LocalContentColor.current
                    },
                )
            }
        }

        HorizontalDivider()
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(state = state.tocListState, modifier = Modifier.fillMaxSize()) {
                items(state.sortedToc, key = { it.href }) { item ->
                    val isSelected = remember(state.currentChapterIndex) {
                        state.currentChapterIndex != -1 &&
                            state.currentChapterIndex < state.book.spineHrefs.size &&
                            state.book.spineHrefs[state.currentChapterIndex] == item.href.substringBefore("#")
                    }
                    NavigationDrawerItem(
                        label = {
                            Text(
                                item.title,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        selected = isSelected,
                        shape = RectangleShape,
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = Color.Transparent,
                            selectedContainerColor = state.themeColors.foreground.copy(alpha = 0.1f),
                            unselectedTextColor = state.themeColors.foreground,
                            selectedTextColor = state.themeColors.foreground,
                            unselectedIconColor = state.themeColors.foreground,
                            selectedIconColor = state.themeColors.foreground,
                        ),
                        onClick = {
                            val index = state.book.spineHrefs.indexOf(item.href.substringBefore("#"))
                            if (index != -1) {
                                callbacks.onSelectTocChapter(index)
                            }
                        },
                    )
                }
            }
            VerticalScrubber(
                listState = state.tocListState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp, top = 16.dp, bottom = 16.dp)
                    .width(16.dp),
                color = MaterialTheme.colorScheme.primary,
                isTOC = true,
            )
        }
    }
}
