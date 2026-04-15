/**
 * Deprecated runtime retained in place for the upcoming safe refactor.
 * `AppNavigation` no longer routes user-facing flows into this screen.
 */
package com.epubreader.feature.pdf.legacy

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.epubreader.core.model.BookFormat
import com.epubreader.core.model.BookRepresentation
import com.epubreader.core.model.BookProgress
import com.epubreader.core.model.EpubBook
import com.epubreader.core.model.GlobalSettings
import com.epubreader.data.parser.EpubParser
import com.epubreader.data.settings.SettingsManager
import com.epubreader.feature.reader.ReaderTheme
import com.epubreader.feature.reader.getThemeColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    book: EpubBook,
    settingsManager: SettingsManager,
    parser: EpubParser,
    onBack: () -> Unit,
    onOpenGeneratedEpub: (() -> Unit)? = null,
    onRetryPdfConversion: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val globalSettings by settingsManager.globalSettings.collectAsState(initial = GlobalSettings())
    val themeColors = getThemeColors(globalSettings.theme, globalSettings.customThemes)
    val listState = rememberLazyListState()
    val pdfFile = remember(book.id, book.format, book.sourceFormat) {
        parser.resolveStoredBookFile(book, BookRepresentation.PDF)
    }
    val documentHandle = remember(book.id) { PdfDocumentHandle.open(pdfFile) }
    val totalPages = documentHandle?.pageCount ?: book.pageCount
    val defaultAspectRatio = documentHandle?.defaultAspectRatio ?: (1f / 1.4142f)
    var isInitialScrollDone by remember(book.id) { mutableStateOf(false) }
    var isRestoringPosition by remember(book.id) { mutableStateOf(false) }
    var showControls by remember(book.id) { mutableStateOf(false) }

    DisposableEffect(documentHandle) {
        onDispose {
            documentHandle?.close()
        }
    }

    LaunchedEffect(book.id, totalPages, documentHandle) {
        if (documentHandle == null || totalPages <= 0) {
            return@LaunchedEffect
        }

        isRestoringPosition = true
        val savedProgress = settingsManager
            .getBookProgress(book.id, BookRepresentation.PDF)
            .first()
        val restoredIndex = savedProgress.scrollIndex.coerceIn(0, totalPages - 1)
        val restoredOffset = savedProgress.scrollOffset.coerceAtLeast(0)
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }
            .filter { it.isNotEmpty() && listState.layoutInfo.totalItemsCount >= totalPages }
            .first()
        delay(1)
        listState.scrollToItem(restoredIndex, restoredOffset)
        delay(100)
        if (listState.firstVisibleItemIndex != restoredIndex) {
            listState.scrollToItem(restoredIndex, restoredOffset)
            delay(100)
        }
        isInitialScrollDone = true
        isRestoringPosition = false
    }

    LaunchedEffect(
        listState.firstVisibleItemIndex,
        listState.firstVisibleItemScrollOffset,
        isInitialScrollDone,
        isRestoringPosition,
        totalPages,
    ) {
        if (!isInitialScrollDone || isRestoringPosition || totalPages <= 0) {
            return@LaunchedEffect
        }

        delay(500)
        settingsManager.saveBookProgress(
            book.id,
            BookProgress(
                scrollIndex = listState.firstVisibleItemIndex.coerceIn(0, totalPages - 1),
                scrollOffset = listState.firstVisibleItemScrollOffset,
                lastChapterHref = null,
            ),
            representation = BookRepresentation.PDF,
        )
    }

    suspend fun saveCurrentProgress() {
        if (totalPages <= 0) {
            return
        }

        withContext(NonCancellable) {
            settingsManager.saveBookProgress(
                book.id,
                BookProgress(
                    scrollIndex = listState.firstVisibleItemIndex.coerceIn(0, totalPages - 1),
                    scrollOffset = listState.firstVisibleItemScrollOffset,
                    lastChapterHref = null,
                ),
                representation = BookRepresentation.PDF,
            )
        }
    }

    suspend fun saveAndBack() {
        saveCurrentProgress()
        onBack()
    }

    suspend fun saveAndOpenGeneratedEpub() {
        saveCurrentProgress()
        onOpenGeneratedEpub?.invoke()
    }

    val currentPage by remember(totalPages, listState.firstVisibleItemIndex) {
        derivedStateOf {
            if (totalPages <= 0) {
                0
            } else {
                listState.firstVisibleItemIndex.coerceIn(0, totalPages - 1) + 1
            }
        }
    }

    BackHandler {
        scope.launch { saveAndBack() }
    }

    val view = LocalView.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var resumeTrigger by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                resumeTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(showControls, globalSettings.showSystemBar, resumeTrigger) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)

        if (showControls || globalSettings.showSystemBar) {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        } else {
            windowInsetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("pdf_reader_surface")
                .background(themeColors.background)
                .semantics {
                    onClick(label = "Toggle PDF controls") {
                        showControls = !showControls
                        true
                    }
                },
        ) {
            if (documentHandle == null || totalPages <= 0) {
                PdfReaderErrorState(
                    theme = themeColors,
                    onBack = { scope.launch { saveAndBack() } },
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(book.id) {
                            detectTapGestures {
                                showControls = !showControls
                            }
                        },
                    contentPadding = PaddingValues(
                        top = if (showControls) 72.dp else 20.dp,
                        bottom = if (showControls) 88.dp else 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(
                        count = totalPages,
                        key = { pageIndex -> "$pageIndex-${book.id}" },
                    ) { pageIndex ->
                        PdfPageItem(
                            documentHandle = documentHandle,
                            pageIndex = pageIndex,
                            defaultAspectRatio = defaultAspectRatio,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 2 }),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                TopAppBar(
                    modifier = Modifier.testTag("pdf_reader_top_bar"),
                    title = {
                        Column {
                            Text(
                                text = book.title,
                                maxLines = 1,
                            )
                            Text(
                                modifier = Modifier.testTag("pdf_reader_page_label"),
                                text = if (currentPage > 0) "Page $currentPage of $totalPages" else "PDF",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { saveAndBack() } }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = themeColors.background.copy(alpha = 0.97f),
                        titleContentColor = themeColors.foreground,
                        navigationIconContentColor = themeColors.foreground,
                    ),
                    actions = {
                        when {
                            book.canOpenGeneratedEpub && onOpenGeneratedEpub != null -> {
                                TextButton(onClick = { scope.launch { saveAndOpenGeneratedEpub() } }) {
                                    Text(text = "EPUB", color = themeColors.foreground)
                                }
                            }

                            book.sourceFormat == BookFormat.PDF && onRetryPdfConversion != null -> {
                                TextButton(onClick = onRetryPdfConversion) {
                                    Text(text = "Retry", color = themeColors.foreground)
                                }
                            }
                        }
                    },
                )
            }

            AnimatedVisibility(
                visible = showControls && totalPages > 0,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Surface(
                    color = themeColors.background.copy(alpha = 0.98f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        HorizontalDivider(color = themeColors.foreground.copy(alpha = 0.12f))
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            IconButton(
                                onClick = {
                                    val targetPage = (currentPage - 2).coerceAtLeast(0)
                                    scope.launch { listState.animateScrollToItem(targetPage, 0) }
                                },
                                enabled = currentPage > 1,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Previous page",
                                    tint = themeColors.foreground,
                                )
                            }
                            Text(
                                text = "Page $currentPage / $totalPages",
                                color = themeColors.foreground,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            IconButton(
                                onClick = {
                                    val targetPage = currentPage.coerceAtMost(totalPages - 1)
                                    scope.launch { listState.animateScrollToItem(targetPage, 0) }
                                },
                                enabled = currentPage in 1 until totalPages,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Next page",
                                    tint = themeColors.foreground,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPageItem(
    documentHandle: PdfDocumentHandle,
    pageIndex: Int,
    defaultAspectRatio: Float,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
        val renderedBitmap by produceState<Bitmap?>(initialValue = null, documentHandle, pageIndex, widthPx) {
            value = withContext(Dispatchers.IO) {
                documentHandle.renderPage(pageIndex, widthPx)
            }
        }
        DisposableEffect(renderedBitmap) {
            onDispose {
                renderedBitmap?.takeIf { !it.isRecycled }?.recycle()
            }
        }

        Surface(
            color = androidx.compose.ui.graphics.Color.White,
            tonalElevation = 2.dp,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (renderedBitmap != null) {
                Image(
                    bitmap = renderedBitmap!!.asImageBitmap(),
                    contentDescription = "PDF page ${pageIndex + 1}",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((maxWidth / defaultAspectRatio).coerceAtLeast(180.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfReaderErrorState(
    theme: ReaderTheme,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.PictureAsPdf,
            contentDescription = null,
            tint = theme.foreground.copy(alpha = 0.7f),
            modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Couldn't open this PDF.",
            color = theme.foreground,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "The file may be missing or unreadable.",
            color = theme.foreground.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(20.dp))
        TextButton(onClick = onBack) {
            Text(text = "Back to Library")
        }
    }
}

private class PdfDocumentHandle private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    val pageCount: Int,
    val defaultAspectRatio: Float,
) {
    private val renderLock = Any()

    fun renderPage(pageIndex: Int, targetWidthPx: Int): Bitmap? {
        if (pageIndex !in 0 until pageCount || targetWidthPx <= 0) {
            return null
        }

        return runCatching {
            synchronized(renderLock) {
                renderer.openPage(pageIndex).use { page ->
                    val targetHeightPx = max(
                        1,
                        (targetWidthPx.toFloat() * page.height / page.width).toInt(),
                    )
                    val bitmap = Bitmap.createBitmap(targetWidthPx, targetHeightPx, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    val matrix = Matrix().apply {
                        setScale(
                            targetWidthPx.toFloat() / page.width.toFloat(),
                            targetHeightPx.toFloat() / page.height.toFloat(),
                        )
                    }
                    page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }.getOrNull()
    }

    fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }

    companion object {
        fun open(pdfFile: File): PdfDocumentHandle? {
            if (!pdfFile.exists()) {
                return null
            }

            val descriptor = runCatching {
                ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            }.getOrNull() ?: return null

            var renderer: PdfRenderer? = null
            return try {
                renderer = PdfRenderer(descriptor)
                if (renderer.pageCount <= 0) {
                    renderer.close()
                    descriptor.close()
                    return null
                }

                val defaultAspectRatio = renderer.openPage(0).use { page ->
                    page.width.toFloat() / page.height.toFloat()
                }
                PdfDocumentHandle(
                    descriptor = descriptor,
                    renderer = renderer,
                    pageCount = renderer.pageCount,
                    defaultAspectRatio = defaultAspectRatio,
                )
            } catch (_: Exception) {
                renderer?.close()
                descriptor.close()
                null
            }
        }
    }
}
