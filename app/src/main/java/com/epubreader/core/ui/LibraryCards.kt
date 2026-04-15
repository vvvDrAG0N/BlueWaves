package com.epubreader.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.epubreader.core.model.BookRepresentation
import com.epubreader.core.model.BookProgress
import com.epubreader.core.model.EpubBook
import com.epubreader.core.model.GlobalSettings
import com.epubreader.data.settings.SettingsManager
import org.json.JSONObject
import java.io.File

private const val PdfSupportDisabledLibraryLabel = "PDF support disabled"

@Composable
fun RecentlyViewedStrip(
    book: EpubBook,
    settingsManager: SettingsManager,
    globalSettings: GlobalSettings,
    onOpen: (EpubBook) -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Text(
                "Recently Viewed",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        val bookFolder = remember(book.id, globalSettings.bookGroups) {
            try {
                JSONObject(globalSettings.bookGroups).optString(book.id, "My Library")
            } catch (e: Exception) {
                "My Library"
            }
        }
        val progress by settingsManager
            .getBookProgress(book.id, book.activeRepresentation)
            .collectAsState(initial = BookProgress())
        val progressLabel = remember(progress, book) { formatReadingProgress(book, progress) }

        Surface(
            onClick = { onOpen(book) },
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(vertical = 4.dp).height(40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(30.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                ) {
                    if (book.coverPath != null) {
                        AsyncImage(
                            model = File(book.coverPath),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Book,
                                null,
                                Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = bookFolder,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                Text(
                    text = progressLabel,
                    style = MaterialTheme.typography.labelSmall
                )
                Icon(Icons.AutoMirrored.Filled.ArrowRight, null, modifier = Modifier.size(16.dp))
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 12.dp).alpha(0.3f))
    }
}

@Composable
fun BookItem(
    book: EpubBook,
    settingsManager: SettingsManager,
    isCompact: Boolean = false,
    isSelected: Boolean = false,
) {
    val progress by settingsManager
        .getBookProgress(book.id, book.activeRepresentation)
        .collectAsState(initial = BookProgress())
    val progressLabel = remember(progress, book) { formatReadingProgress(book, progress) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCompact) 0.dp else 2.dp),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.7f)) {
            if (book.coverPath != null) {
                AsyncImage(
                    model = File(book.coverPath),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                if (!isCompact) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                                startY = 300f
                            )
                        )
                    )
                    Column(modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 14.sp
                        )
                        Text(
                            text = book.author,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 10.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = progressLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
            } else {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Book,
                        null,
                        modifier = Modifier.size(if (isCompact) 32.dp else 48.dp).alpha(0.2f),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                            startY = if (isCompact) 100f else 250f
                        )
                    )
                )

                Column(modifier = Modifier.align(Alignment.BottomStart).padding(if (isCompact) 4.dp else 8.dp)) {
                    Text(
                        text = book.title,
                        style = if (isCompact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                        lineHeight = if (isCompact) 10.sp else 14.sp,
                        fontSize = if (isCompact) 9.sp else 12.sp
                    )
                    if (!isCompact) {
                        Text(
                            text = book.author,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 10.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = progressLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                )
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                )
            }
        }
    }
}

private fun formatReadingProgress(book: EpubBook, progress: BookProgress): String {
    if (book.sourceFormat == com.epubreader.core.model.BookFormat.PDF) {
        return PdfSupportDisabledLibraryLabel
    }

    val totalUnits = book.readingUnitCount
    if (totalUnits <= 0) {
        return book.formatLabel
    }

    val currentUnit = when {
        book.activeRepresentation == BookRepresentation.PDF -> progress.scrollIndex.coerceIn(0, totalUnits - 1) + 1
        else -> {
            val chapterIndex = book.spineHrefs.indexOf(progress.lastChapterHref)
            if (chapterIndex == -1) 1 else chapterIndex + 1
        }
    }.coerceIn(1, totalUnits)

    return "$currentUnit / $totalUnits ${book.progressUnitLabel}"
}
