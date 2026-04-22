package com.epubreader.feature.editbook

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.epubreader.core.model.EpubBook

@Composable
internal fun EditBookDetailsTab(
    book: EpubBook,
    title: String,
    author: String,
    coverModel: Any?,
    canRemoveCover: Boolean,
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
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val useStackedLayout = maxWidth < 520.dp
                    if (useStackedLayout) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CoverPreviewPane(
                                coverModel = coverModel,
                                canRemoveCover = canRemoveCover,
                                isSaving = isSaving,
                                coverLoadInFlight = coverLoadInFlight,
                                onPickCover = onPickCover,
                                onRemoveCover = onRemoveCover,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            DetailsFieldColumn(
                                title = title,
                                author = author,
                                isSaving = isSaving,
                                onTitleChange = onTitleChange,
                                onAuthorChange = onAuthorChange,
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            CoverPreviewPane(
                                coverModel = coverModel,
                                canRemoveCover = canRemoveCover,
                                isSaving = isSaving,
                                coverLoadInFlight = coverLoadInFlight,
                                onPickCover = onPickCover,
                                onRemoveCover = onRemoveCover,
                                modifier = Modifier.weight(0.34f),
                            )
                            DetailsFieldColumn(
                                title = title,
                                author = author,
                                isSaving = isSaving,
                                onTitleChange = onTitleChange,
                                onAuthorChange = onAuthorChange,
                                modifier = Modifier.weight(0.66f),
                            )
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
private fun SummaryRow(label: String, value: String, monospace: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CoverPreviewPane(
    coverModel: Any?,
    canRemoveCover: Boolean,
    isSaving: Boolean,
    coverLoadInFlight: Boolean,
    onPickCover: () -> Unit,
    onRemoveCover: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .testTag("edit-book-cover-preview"),
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
                    modifier = Modifier.padding(vertical = 32.dp),
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(32.dp))
                    Text("No cover", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CoverActionButton(
                enabled = !isSaving && !coverLoadInFlight && canRemoveCover,
                onClick = onRemoveCover,
                contentDescription = "Remove current cover",
                testTag = "edit-book-cover-remove",
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            CoverActionButton(
                enabled = !isSaving && !coverLoadInFlight,
                onClick = onPickCover,
                contentDescription = "Replace current cover",
                testTag = "edit-book-cover-pick",
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                if (coverLoadInFlight) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                } else {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun DetailsFieldColumn(
    title: String,
    author: String,
    isSaving: Boolean,
    onTitleChange: (String) -> Unit,
    onAuthorChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
    }
}

@Composable
private fun CoverActionButton(
    enabled: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    testTag: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = if (enabled) containerColor else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (enabled) contentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        shape = CircleShape,
        shadowElevation = 8.dp,
        modifier = modifier
            .size(36.dp)
            .testTag(testTag)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            content()
        }
    }
}
