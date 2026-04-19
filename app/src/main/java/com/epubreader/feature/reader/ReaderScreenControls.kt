/**
 * AI_READ_AFTER: ReaderScreen.kt
 * AI_RELEVANT_TO: [Reader Controls, Scrubber, Chapter Element Rendering]
 * PURPOSE: Reader-specific presentational helpers that do not own lifecycle or restoration state.
 * AI_WARNING: Scrubber drag behavior and chapter element rendering order are load-bearing UI behavior.
 */
package com.epubreader.feature.reader

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GTranslate
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.epubreader.core.model.availableThemeOptions
import com.epubreader.core.model.ChapterElement
import com.epubreader.core.model.GlobalSettings
import com.epubreader.core.model.themeButtonLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

private val ReaderControlsSheetFractions = listOf(0.5f, 0.8f, 1.0f)
private const val ReaderControlsDismissThresholdRatio = 0.75f
private const val ReaderControlsDismissVelocityThreshold = 1_600f

private fun nearestReaderControlsHeightPx(
    currentHeightPx: Float,
    snapHeightsPx: List<Float>,
): Float {
    return snapHeightsPx.minByOrNull { snapHeightPx ->
        abs(snapHeightPx - currentHeightPx)
    } ?: currentHeightPx
}

@Composable
internal fun ReaderChapterContent(
    settings: GlobalSettings,
    themeColors: ReaderTheme,
    listState: LazyListState,
    chapterElements: List<ChapterElement>,
    isLoadingChapter: Boolean,
    currentChapterIndex: Int,
    selectionResetToken: Int = 0,
    onSelectionActiveChange: (Boolean) -> Unit = {}
) {
    if (currentChapterIndex == -1 || isLoadingChapter) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = themeColors.foreground)
        }
        return
    }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val platformTextToolbar = LocalTextToolbar.current
    val textSelectionScope = rememberCoroutineScope()
    val selectionActiveChangeState = rememberUpdatedState(onSelectionActiveChange)
    val density = LocalDensity.current
    
    // Internal clipboard to capture text without triggering system popups
    val internalClipboard = remember {
        object : ClipboardManager {
            private var content: AnnotatedString? = null
            override fun setText(annotatedString: AnnotatedString) { content = annotatedString }
            override fun getText(): AnnotatedString? = content
            override fun hasText(): Boolean = content != null
        }
    }

    val selectionSession = remember(textSelectionScope) {
        ReaderTextSelectionSession(
            scheduler = createReaderTextSelectionScheduler(textSelectionScope),
            onActiveChanged = { isActive ->
                selectionActiveChangeState.value(isActive)
            }
        )
    }
    var selectionMenuRect by remember { mutableStateOf<Rect?>(null) }
    var contentHeightPx by remember { mutableIntStateOf(0) }
    var actionBarHeightPx by remember { mutableIntStateOf(0) }

    // Bottom sheet state for in-app WebView lookups
    var pendingWebLookup by remember { mutableStateOf<WebLookupAction?>(null) }

    val trackingTextToolbar = remember(platformTextToolbar) {
        object : TextToolbar {
            override val status: TextToolbarStatus
                get() = if (selectionSession.isActive) TextToolbarStatus.Shown else TextToolbarStatus.Hidden

            override fun showMenu(
                rect: Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?
            ) {
                // Update the copy callback but keep selection active throughout drag.
                // This is called repeatedly during handle drag — do NOT toggle state.
                selectionMenuRect = rect
                selectionSession.showMenu(onCopyRequested)
            }

            override fun hide() {
                selectionSession.hide()
            }
        }
    }

    val content = @Composable {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("reader_chapter_content"),
            contentPadding = PaddingValues(
                horizontal = settings.horizontalPadding.dp,
                vertical = 80.dp
            )
        ) {
            items(chapterElements, key = { it.id }) { element ->
                when (element) {
                    is ChapterElement.Text -> {
                        val textContent = @Composable {
                            ReaderChapterText(
                                element = element,
                                settings = settings,
                                themeColors = themeColors,
                            )
                        }

                        if (settings.selectableText) {
                            SelectionContainer(
                                modifier = Modifier.testTag("reader_selectable_text_item")
                            ) {
                                textContent()
                            }
                        } else {
                            textContent()
                        }
                    }

                    is ChapterElement.Image -> {
                        ReaderChapterImage(
                            data = element.data,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(settings.selectableText) {
        if (!settings.selectableText) {
            selectionSession.reset()
        }
    }

    LaunchedEffect(selectionResetToken, settings.selectableText) {
        if (settings.selectableText && selectionResetToken > 0) {
            selectionSession.reset()
        }
    }

    LaunchedEffect(selectionSession.isActive) {
        if (!selectionSession.isActive) {
            selectionMenuRect = null
        }
    }

    if (settings.selectableText) {
        val selectionActionBarBottomPadding =
            if (settings.readerStatusUi.isEnabled && !settings.showSystemBar) 40.dp else 24.dp
        val selectionActionBarTopPadding = 24.dp
        val estimatedActionBarHeightPx =
            if (actionBarHeightPx > 0) actionBarHeightPx else with(density) { 72.dp.roundToPx() }
        val actionBarCollisionZonePx = estimatedActionBarHeightPx + with(density) {
            (selectionActionBarBottomPadding + 24.dp).roundToPx()
        }
        val moveSelectionActionBarToTop =
            selectionMenuRect?.bottom?.let { selectionBottom ->
                contentHeightPx > 0 && selectionBottom >= (contentHeightPx - actionBarCollisionZonePx)
            } == true

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    contentHeightPx = size.height
                }
                .pointerInput(selectionResetToken) {
                    awaitPointerEventScope {
                        while (true) {
                            when (awaitPointerEvent().type) {
                                PointerEventType.Press -> selectionSession.onPointerPressed()
                                PointerEventType.Release,
                                PointerEventType.Exit -> selectionSession.onPointerReleased()
                                else -> Unit
                            }
                        }
                    }
                }
        ) {
            key(selectionResetToken) {
                CompositionLocalProvider(
                    LocalTextToolbar provides trackingTextToolbar,
                    LocalClipboardManager provides internalClipboard
                ) {
                    content()
                }
            }

            // Floating action bar for text selection with Copy/Define/Translate
            AnimatedVisibility(
                visible = selectionSession.isActive,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .align(if (moveSelectionActionBarToTop) Alignment.TopCenter else Alignment.BottomCenter)
                    .then(
                        if (moveSelectionActionBarToTop) {
                            Modifier
                                .padding(top = selectionActionBarTopPadding)
                                .statusBarsPadding()
                        } else {
                            Modifier
                                .padding(bottom = selectionActionBarBottomPadding)
                                .navigationBarsPadding()
                        }
                    )
                    .onSizeChanged { size ->
                        actionBarHeightPx = size.height
                    }
            ) {
                TextSelectionActionBar(
                    themeColors = themeColors,
                    onCopy = {
                        selectionSession.copyAction?.invoke() // Copies to internalClipboard
                        internalClipboard.getText()?.let { clipboardManager.setText(it) }
                    },
                    onDefine = {
                        selectionSession.copyAction?.invoke() // Copies to internalClipboard
                        val text = internalClipboard.getText()?.text.orEmpty()
                        if (text.isNotBlank()) {
                            pendingWebLookup = WebLookupAction.Define(text)
                        }
                    },
                    onTranslate = {
                        selectionSession.copyAction?.invoke() // Copies to internalClipboard
                        val text = internalClipboard.getText()?.text.orEmpty()
                        if (text.isNotBlank()) {
                            pendingWebLookup = WebLookupAction.Translate(text, settings.targetTranslationLanguage)
                        }
                    },
                )
            }
        }

        // In-app WebView bottom sheet for Define/Translate
        pendingWebLookup?.let { action ->
            WebViewBottomSheet(
                url = action.url,
                onDismiss = { pendingWebLookup = null },
            )
        }
    } else {
        content()
    }
}

@Composable
private fun ReaderChapterText(
    element: ChapterElement.Text,
    settings: GlobalSettings,
    themeColors: ReaderTheme,
) {
    val style = if (element.type == "h") {
        MaterialTheme.typography.headlineSmall.copy(
            fontSize = (settings.fontSize + 4).sp,
            fontWeight = FontWeight.Bold
        )
    } else {
        MaterialTheme.typography.bodyLarge.copy(
            fontSize = settings.fontSize.sp,
            lineHeight = (settings.fontSize * settings.lineHeight).sp
        )
    }

    Text(
        text = element.content,
        style = style,
        fontFamily = readerFontFamily(settings.fontType),
        color = themeColors.foreground,
        textAlign = if (element.type == "h") TextAlign.Center else TextAlign.Start,
        modifier = Modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth()
    )
}

@Composable
private fun ReaderChapterImage(
    data: ByteArray,
    modifier: Modifier = Modifier,
) {
    val imageBitmap by produceState<ImageBitmap?>(initialValue = null, data) {
        value = if (data.isEmpty()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap()
            }
        }
    }

    imageBitmap?.let { bitmap ->
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun TextSelectionActionBar(
    themeColors: ReaderTheme,
    onCopy: () -> Unit,
    onDefine: () -> Unit,
    onTranslate: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .testTag("text_selection_action_bar")
            .shadow(8.dp, RoundedCornerShape(28.dp)),
        color = themeColors.background.copy(alpha = 0.97f),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextSelectionActionButton(
                icon = Icons.Default.ContentCopy,
                label = "Copy",
                themeColors = themeColors,
                onClick = onCopy,
            )
            TextSelectionActionButton(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                label = "Define",
                themeColors = themeColors,
                onClick = onDefine,
            )
            TextSelectionActionButton(
                icon = Icons.Default.GTranslate,
                label = "Translate",
                themeColors = themeColors,
                onClick = onTranslate,
            )
        }
    }
}

@Composable
private fun TextSelectionActionButton(
    icon: ImageVector,
    label: String,
    themeColors: ReaderTheme,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = themeColors.foreground,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = themeColors.foreground.copy(alpha = 0.8f),
            maxLines = 1,
        )
    }
}

/**
 * In-app WebView bottom sheet for Define/Translate lookups.
 *
 * - Uses the default ModalBottomSheet drag handle (pill) — no custom header.
 * - Nested scroll conflict is resolved by a custom WebView that calls
 *   requestDisallowInterceptTouchEvent while the user is actively scrolling
 *   web content. The sheet only consumes the drag when the WebView is at scrollY == 0.
 * - The sheet container and WebView follow the device system theme (dark/light)
 *   via MaterialTheme colors and WebView's algorithmic darkening, so the web
 *   content and the sheet always match the user's phone-wide dark mode setting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
private fun WebViewBottomSheet(
    url: String,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Use Material3 system theme colors so the sheet matches the device dark/light mode.
    val sheetBg = MaterialTheme.colorScheme.surface
    val handleColor = MaterialTheme.colorScheme.onSurfaceVariant

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        scrimColor = Color.Black.copy(alpha = 0.4f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(handleColor.copy(alpha = 0.4f))
            )
        },
    ) {
        val bgArgb = sheetBg.toArgb()
        AndroidView(
            factory = { ctx ->
                NestedScrollWebView(ctx).apply {
                    setBackgroundColor(bgArgb)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true

                    // Always allow algorithmic darkening — this makes the WebView
                    // respect the system's prefers-color-scheme media query, so
                    // Google Search, Translate, etc. naturally follow phone dark mode.
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                        WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, true)
                    }

                    webViewClient = WebViewClient()
                    loadUrl(url)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.55f)
                .testTag("web_lookup_webview"),
        )
    }
}

/**
 * WebView subclass that cooperates with Compose ModalBottomSheet gesture handling.
 *
 * When the user touches and starts scrolling *down* inside the WebView (i.e. the page
 * has content below the viewport), this view calls requestDisallowInterceptTouchEvent(true)
 * so the BottomSheet does not steal the gesture and dismiss itself.
 *
 * When the WebView is at the very top (scrollY == 0) and the user swipes down,
 * the touch is allowed to propagate so the BottomSheet can be dragged to dismiss.
 */
@SuppressLint("ClickableViewAccessibility")
private class NestedScrollWebView(context: android.content.Context) : WebView(context) {
    private var startY = 0f

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                startY = event.y
                // Optimistically claim the touch; we'll release if at top + swipe down.
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                val deltaY = event.y - startY
                if (deltaY > 0 && scrollY == 0) {
                    // User is swiping down and WebView is at the top — let sheet handle it.
                    parent?.requestDisallowInterceptTouchEvent(false)
                } else {
                    // User is scrolling up (into more content) or WebView is scrolled —
                    // keep the touch for the WebView.
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            android.view.MotionEvent.ACTION_UP,
            android.view.MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.onTouchEvent(event)
    }
}

@Composable
fun OverscrollIndicator(text: String, modifier: Modifier, color: Color) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.6f),
        shape = CircleShape
    ) {
        Text(
            text = text,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
fun ReaderControls(
    settings: GlobalSettings,
    onSettingsChange: (GlobalSettingsTransform) -> Unit,
    themeColors: ReaderTheme,
    onNavigatePrev: () -> Unit,
    onNavigateNext: () -> Unit,
    listState: LazyListState,
    itemCount: Int,
    currentChapterIndex: Int,
    totalChapters: Int,
    sectionLabel: String,
    progressPercentage: Float,
    onDismiss: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 8.dp)
            .padding(top = 8.dp),
    ) {
        val density = LocalDensity.current
        val maxSheetHeightPx = with(density) { maxHeight.toPx() }
        val snapHeightsPx = remember(maxHeight) {
            ReaderControlsSheetFractions.map { fraction ->
                with(density) { maxHeight.toPx() * fraction }
            }
        }
        val minSheetHeightPx = snapHeightsPx.first()
        val dismissThresholdPx = minSheetHeightPx * ReaderControlsDismissThresholdRatio
        var desiredSheetHeightPx by remember(maxHeight) { mutableFloatStateOf(minSheetHeightPx) }
        var sheetChromeHeightPx by remember { mutableIntStateOf(0) }
        val contentMaxHeightDp = with(density) {
            (desiredSheetHeightPx - sheetChromeHeightPx)
                .coerceAtLeast(0f)
                .toDp()
        }
        val contentScrollState = rememberScrollState()

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("reader_controls_sheet"),
            colors = CardDefaults.cardColors(
                containerColor = themeColors.background.copy(alpha = 0.98f),
                contentColor = themeColors.foreground
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column {
                Column(
                    modifier = Modifier.onSizeChanged { sheetChromeHeightPx = it.height }
                ) {
                    ReaderControlsDragHandle(
                        themeColors = themeColors,
                        onDragDelta = { delta ->
                            desiredSheetHeightPx =
                                (desiredSheetHeightPx - delta).coerceIn(0f, maxSheetHeightPx)
                        },
                        onDragStopped = { velocity ->
                            val dismissByHeight = desiredSheetHeightPx < dismissThresholdPx
                            val dismissByFling =
                                velocity > ReaderControlsDismissVelocityThreshold &&
                                    desiredSheetHeightPx <= minSheetHeightPx

                            if (dismissByHeight || dismissByFling) {
                                onDismiss()
                            } else {
                                desiredSheetHeightPx = nearestReaderControlsHeightPx(
                                    currentHeightPx = desiredSheetHeightPx.coerceAtLeast(minSheetHeightPx),
                                    snapHeightsPx = snapHeightsPx,
                                )
                            }
                        }
                    )

                    HorizontalDivider(
                        color = themeColors.foreground.copy(alpha = 0.2f)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = contentMaxHeightDp)
                        .verticalScroll(contentScrollState)
                        .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    ReaderChapterControlsSection(
                        themeColors = themeColors,
                        onNavigatePrev = onNavigatePrev,
                        onNavigateNext = onNavigateNext,
                        listState = listState,
                        itemCount = itemCount,
                        currentChapterIndex = currentChapterIndex,
                        totalChapters = totalChapters,
                        sectionLabel = sectionLabel,
                        progressPercentage = progressPercentage,
                    )
                    ReaderThemeControlsSection(
                        settings = settings,
                        onSettingsChange = onSettingsChange,
                    )
                    ReaderFontControlsSection(
                        settings = settings,
                        onSettingsChange = onSettingsChange,
                    )
                    ReaderReadingControlsSection(
                        settings = settings,
                        onSettingsChange = onSettingsChange,
                    )
                    ReaderOtherControlsSection(
                        settings = settings,
                        onSettingsChange = onSettingsChange,
                    )
                }
            }
        }
    }

}

@Composable
private fun ReaderControlsDragHandle(
    themeColors: ReaderTheme,
    onDragDelta: (Float) -> Unit,
    onDragStopped: suspend CoroutineScope.(Float) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .testTag("reader_controls_drag_handle")
                .size(width = 44.dp, height = 24.dp)
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta -> onDragDelta(delta) },
                    onDragStopped = onDragStopped,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(themeColors.foreground.copy(alpha = 0.28f))
            )
        }
    }
}

@Composable
private fun ReaderControlsSection(
    title: String,
    testTag: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = LocalContentColor.current.copy(alpha = 0.88f),
        )
        content()
    }
}

@Composable
private fun ReaderChapterControlsSection(
    themeColors: ReaderTheme,
    onNavigatePrev: () -> Unit,
    onNavigateNext: () -> Unit,
    listState: LazyListState,
    itemCount: Int,
    currentChapterIndex: Int,
    totalChapters: Int,
    sectionLabel: String,
    progressPercentage: Float,
) {
    val scope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var draggingValue by remember { mutableStateOf(0f) }

    LaunchedEffect(progressPercentage) {
        if (!isDragging) {
            draggingValue = progressPercentage
        }
    }

    ReaderControlsSection(
        title = "Chapter",
        testTag = "reader_controls_section_chapter",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$sectionLabel ${currentChapterIndex + 1} of $totalChapters",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${(draggingValue * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = themeColors.foreground.copy(alpha = 0.65f),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.width(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(onClick = onNavigatePrev) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous $sectionLabel")
                    }
                    Text(
                        text = if (currentChapterIndex > 0) "${sectionLabel.take(1)}. $currentChapterIndex" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = themeColors.foreground.copy(alpha = 0.6f),
                        maxLines = 1
                    )
                }

                Slider(
                    value = draggingValue,
                    onValueChange = { progress ->
                        isDragging = true
                        draggingValue = progress
                        val targetIndex = (progress * (itemCount - 1)).toInt().coerceIn(0, (itemCount - 1).coerceAtLeast(0))
                        scope.launch { listState.scrollToItem(targetIndex) }
                    },
                    onValueChangeFinished = {
                        isDragging = false
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )

                Column(
                    modifier = Modifier.width(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(onClick = onNavigateNext) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next $sectionLabel")
                    }
                    Text(
                        text = if (currentChapterIndex < totalChapters - 1) "${sectionLabel.take(1)}. ${currentChapterIndex + 2}" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = themeColors.foreground.copy(alpha = 0.6f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderFontControlsSection(
    settings: GlobalSettings,
    onSettingsChange: (GlobalSettingsTransform) -> Unit,
) {
    ReaderControlsSection(
        title = "Font",
        testTag = "reader_controls_section_font",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.TextFormat, null, modifier = Modifier.size(20.dp))
                }
                Slider(
                    value = settings.fontSize.toFloat(),
                    onValueChange = { fontSize ->
                        onSettingsChange { current -> current.copy(fontSize = fontSize.toInt()) }
                    },
                    valueRange = 12f..32f,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
                Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "${settings.fontSize}sp",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Line Height", style = MaterialTheme.typography.labelSmall)
                    Text(
                        String.format(Locale.getDefault(), "%.1f", settings.lineHeight),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Slider(
                    value = settings.lineHeight,
                    onValueChange = { lineHeight ->
                        onSettingsChange { current -> current.copy(lineHeight = lineHeight) }
                    },
                    valueRange = 1.2f..2.0f
                )
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Padding", style = MaterialTheme.typography.labelSmall)
                    Text("${settings.horizontalPadding}dp", style = MaterialTheme.typography.bodySmall)
                }
                Slider(
                    value = settings.horizontalPadding.toFloat(),
                    onValueChange = { padding ->
                        onSettingsChange { current -> current.copy(horizontalPadding = padding.toInt()) }
                    },
                    valueRange = 0f..32f
                )
            }
        }
    }

    ReaderControlsSection(
        title = "Font Family",
        testTag = "reader_controls_section_font_family",
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .testTag("reader_font_row"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val fonts = listOf("default", "serif", "sans-serif", "monospace", "karla")
            fonts.forEach { font ->
                FilterChip(
                    modifier = Modifier.testTag("reader_font_chip_$font"),
                    selected = settings.fontType == font,
                    onClick = {
                        onSettingsChange { current -> current.copy(fontType = font) }
                    },
                    label = { Text(font.replaceFirstChar { it.uppercase() }) },
                )
            }
        }
    }

}

@Composable
private fun ReaderThemeControlsSection(
    settings: GlobalSettings,
    onSettingsChange: (GlobalSettingsTransform) -> Unit,
) {
    ReaderControlsSection(
        title = "Theme",
        testTag = "reader_controls_section_theme",
    ) {
        val themeOptions = availableThemeOptions(settings.customThemes)
        val listState = rememberLazyListState()

        LaunchedEffect(settings.theme) {
            val index = themeOptions.indexOfFirst { it.id == settings.theme }
            if (index != -1) {
                listState.animateScrollToItem(index)
            }
        }

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("reader_theme_row"),
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(themeOptions) { _, option ->
                ReaderThemeButton(
                    name = option.name,
                    bg = Color(option.palette.readerBackground),
                    fg = Color(option.palette.readerForeground),
                    selected = settings.theme == option.id,
                    label = themeButtonLabel(option.name, option.id),
                ) {
                    onSettingsChange { current -> current.copy(theme = option.id) }
                }
            }
        }
    }

}

@Composable
private fun ReaderReadingControlsSection(
    settings: GlobalSettings,
    onSettingsChange: (GlobalSettingsTransform) -> Unit,
) {
    ReaderControlsSection(
        title = "Reading",
        testTag = "reader_controls_section_reading",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ReaderGeneralToggleRow(
                label = "Show Scrubber",
                checked = settings.showScrubber,
                onCheckedChange = { showScrubber ->
                    onSettingsChange { current -> current.copy(showScrubber = showScrubber) }
                }
            )

            ReaderGeneralToggleRow(
                label = "Show Scroll-to-Top",
                checked = settings.showScrollToTop,
                onCheckedChange = { show ->
                    onSettingsChange { it.copy(showScrollToTop = show) }
                }
            )

            ReaderGeneralToggleRow(
                label = "Selectable Text",
                checked = settings.selectableText,
                onCheckedChange = { selectableText ->
                    onSettingsChange { current -> current.copy(selectableText = selectableText) }
                }
            )

            ReaderStatusSettingsRow(
                settings = settings,
                onUpdateSettings = onSettingsChange,
                isReaderUI = true,
                isSystemBarVisible = settings.showSystemBar,
                showHeader = false,
            )
        }
    }
}

@Composable
private fun ReaderOtherControlsSection(
    settings: GlobalSettings,
    onSettingsChange: (GlobalSettingsTransform) -> Unit,
) {
    ReaderControlsSection(
        title = "Others",
        testTag = "reader_controls_section_others",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ReaderGeneralToggleRow(
                label = "Show System Bar",
                checked = settings.showSystemBar,
                onCheckedChange = { showSystemBar ->
                    onSettingsChange { current -> current.copy(showSystemBar = showSystemBar) }
                }
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Translate To", style = MaterialTheme.typography.labelSmall)
            val languages = listOf(
                "ar" to "العربية",
                "en" to "English",
                "es" to "Español",
                "fr" to "Français",
                "ja" to "日本語"
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(languages) { (code, name) ->
                    FilterChip(
                        selected = settings.targetTranslationLanguage == code,
                        onClick = {
                            onSettingsChange { it.copy(targetTranslationLanguage = code) }
                        },
                        label = { Text(name) }
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun ReaderGeneralToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun ReaderThemeButton(
    name: String,
    bg: Color,
    fg: Color,
    selected: Boolean,
    label: String = "A",
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .semantics {
                contentDescription = "Theme $name"
                this.selected = selected
            }
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable { onClick() }
            .padding(3.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(bg)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = fg, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun KeypadButton(
    text: String,
    modifier: Modifier = Modifier,
    themeColors: ReaderTheme,
    onClick: () -> Unit
) {
    val isAction = text == "Clear" || text == "Confirm"
    Surface(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        color = if (isAction) themeColors.foreground.copy(alpha = 0.15f) else themeColors.foreground.copy(alpha = 0.05f),
        shape = MaterialTheme.shapes.medium
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = if (isAction) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleLarge,
                color = themeColors.foreground,
                fontWeight = if (isAction) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

/**
 * VerticalScrubber: Provides a fast-scroll handle.
 * [AI_NOTE] Directly manipulates LazyListState.scrollToItem for instantaneous feedback.
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun VerticalScrubber(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    onDragStart: () -> Unit = {},
    isTOC: Boolean = false
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxHeight()) {
        val totalHeight = with(density) { maxHeight.toPx() }
        val thumbHeight = with(density) { 48.dp.toPx() }

        val scrollProgress by remember {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo
                val totalItems = layoutInfo.totalItemsCount

                if (visibleItems.isNotEmpty() && totalItems > 0) {
                    val viewportStart = layoutInfo.viewportStartOffset
                    val topItem = visibleItems.firstOrNull { it.offset + it.size > viewportStart } ?: visibleItems.first()
                    val itemTop = topItem.offset
                    val relativeOffset = (viewportStart - itemTop).coerceAtLeast(0)
                    val itemHeight = if (topItem.size > 0) topItem.size else 1

                    ((topItem.index.toFloat() + (relativeOffset.toFloat() / itemHeight.toFloat())) / totalItems.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
            }
        }

        val thumbOffset by remember {
            derivedStateOf {
                (totalHeight - thumbHeight) * scrollProgress.coerceIn(0f, 1f)
            }
        }

        val showScrubber by remember {
            derivedStateOf { listState.layoutInfo.totalItemsCount > 1 }
        }

        if (showScrubber) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(if (isTOC) 16.dp else 20.dp)
                    .pointerInput(totalHeight) {
                        detectDragGestures(
                            onDragStart = { onDragStart() },
                            onDrag = { change, _ ->
                                change.consume()
                                val y = change.position.y
                                val progress = (y / totalHeight).coerceIn(0f, 1f)
                                val currentTotalItems = listState.layoutInfo.totalItemsCount
                                val targetIndex = (progress * currentTotalItems).toInt().coerceIn(0, currentTotalItems - 1)
                                scope.launch { listState.scrollToItem(targetIndex) }
                            }
                        )
                    }
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = with(density) { thumbOffset.toDp() })
                        .width(if (isTOC) 6.dp else 4.dp)
                        .height(with(density) { thumbHeight.toDp() })
                        .background(color.copy(alpha = 0.5f), CircleShape)
                )
            }
        }
    }
}

private fun readerFontFamily(fontType: String): FontFamily {
    return when (fontType) {
        "serif" -> FontFamily.Serif
        "sans-serif" -> FontFamily.SansSerif
        "monospace" -> FontFamily.Monospace
        "karla" -> KarlaFont
        else -> FontFamily.Default
    }
}
