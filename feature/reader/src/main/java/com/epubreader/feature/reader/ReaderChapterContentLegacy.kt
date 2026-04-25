package com.epubreader.feature.reader

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import coil.compose.AsyncImage
import com.epubreader.core.model.ChapterElement
import com.epubreader.core.model.GlobalSettings
import com.epubreader.core.model.themePaletteSeed
import java.io.File

@Composable
internal fun ReaderChapterContentLegacy(
    settings: GlobalSettings,
    themeColors: ReaderTheme,
    listState: LazyListState,
    chapterElements: List<ChapterElement>,
    isLoadingChapter: Boolean,
    currentChapterIndex: Int,
    selectionResetToken: Int = 0,
    onSelectionActiveChange: (Boolean) -> Unit = {},
) {
    if (currentChapterIndex == -1 || isLoadingChapter) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = themeColors.foreground)
        }
        return
    }

    val clipboardManager = LocalClipboardManager.current
    val platformTextToolbar = LocalTextToolbar.current
    val textSelectionScope = rememberCoroutineScope()
    val selectionActiveChangeState = rememberUpdatedState(onSelectionActiveChange)
    val density = LocalDensity.current
    val overlayScrim = remember(settings.theme, settings.customThemes) {
        Color(themePaletteSeed(settings.theme, settings.customThemes).overlayScrim).copy(alpha = 0.45f)
    }

    val internalClipboard = remember {
        object : ClipboardManager {
            private var content: AnnotatedString? = null

            override fun setText(annotatedString: AnnotatedString) {
                content = annotatedString
            }

            override fun getText(): AnnotatedString? = content

            override fun hasText(): Boolean = content != null
        }
    }

    val selectionSession = remember(textSelectionScope) {
        ReaderTextSelectionSession(
            scheduler = createReaderTextSelectionScheduler(textSelectionScope),
            onActiveChanged = { isActive ->
                selectionActiveChangeState.value(isActive)
            },
        )
    }
    var selectionMenuRect by remember { mutableStateOf<Rect?>(null) }
    var contentHeightPx by remember { mutableIntStateOf(0) }
    var actionBarHeightPx by remember { mutableIntStateOf(0) }
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
                onSelectAllRequested: (() -> Unit)?,
            ) {
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
                vertical = 80.dp,
            ),
        ) {
            items(chapterElements, key = { it.id }) { element ->
                when (element) {
                    is ChapterElement.Text -> {
                        val textContent = @Composable {
                            ReaderLegacyChapterText(
                                element = element,
                                settings = settings,
                                themeColors = themeColors,
                            )
                        }

                        if (settings.selectableText) {
                            SelectionContainer(
                                modifier = Modifier.testTag("reader_selectable_text_item"),
                            ) {
                                textContent()
                            }
                        } else {
                            textContent()
                        }
                    }

                    is ChapterElement.Image -> {
                        ReaderLegacyChapterImage(
                            filePath = element.filePath,
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
                },
        ) {
            key(selectionResetToken) {
                CompositionLocalProvider(
                    LocalTextToolbar provides trackingTextToolbar,
                    LocalClipboardManager provides internalClipboard,
                ) {
                    content()
                }
            }

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
                        },
                    )
                    .onSizeChanged { size ->
                        actionBarHeightPx = size.height
                    },
            ) {
                TextSelectionActionBar(
                    themeColors = themeColors,
                    onCopy = {
                        selectionSession.copyAction?.invoke()
                        internalClipboard.getText()?.let { clipboardManager.setText(it) }
                    },
                    onDefine = {
                        selectionSession.copyAction?.invoke()
                        val text = internalClipboard.getText()?.text.orEmpty()
                        if (text.isNotBlank()) {
                            pendingWebLookup = WebLookupAction.Define(text)
                        }
                    },
                    onTranslate = {
                        selectionSession.copyAction?.invoke()
                        val text = internalClipboard.getText()?.text.orEmpty()
                        if (text.isNotBlank()) {
                            pendingWebLookup = WebLookupAction.Translate(text, settings.targetTranslationLanguage)
                        }
                    },
                )
            }
        }

        pendingWebLookup?.let { action ->
            ReaderLegacyWebViewBottomSheet(
                url = action.url,
                scrimColor = overlayScrim,
                onDismiss = { pendingWebLookup = null },
            )
        }
    } else {
        content()
    }
}

@Composable
private fun ReaderLegacyChapterText(
    element: ChapterElement.Text,
    settings: GlobalSettings,
    themeColors: ReaderTheme,
) {
    val style = if (element.type == "h") {
        MaterialTheme.typography.headlineSmall.copy(
            fontSize = (settings.fontSize + 4).sp,
            fontWeight = FontWeight.Bold,
        )
    } else {
        MaterialTheme.typography.bodyLarge.copy(
            fontSize = settings.fontSize.sp,
            lineHeight = (settings.fontSize * settings.lineHeight).sp,
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
            .fillMaxWidth(),
    )
}

@Composable
private fun ReaderLegacyChapterImage(
    filePath: String,
    modifier: Modifier = Modifier,
) {
    if (filePath.isBlank()) {
        return
    }

    AsyncImage(
        model = File(filePath),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
private fun ReaderLegacyWebViewBottomSheet(
    url: String,
    scrimColor: Color,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetBg = MaterialTheme.colorScheme.surface
    val handleColor = MaterialTheme.colorScheme.onSurfaceVariant

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        scrimColor = scrimColor,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(handleColor.copy(alpha = 0.4f))
                    .padding(horizontal = 16.dp, vertical = 2.dp),
            )
        },
    ) {
        val bgArgb = sheetBg.toArgb()
        AndroidView(
            factory = { ctx ->
                ReaderLegacyNestedScrollWebView(ctx).apply {
                    setBackgroundColor(bgArgb)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
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

@SuppressLint("ClickableViewAccessibility")
private class ReaderLegacyNestedScrollWebView(context: android.content.Context) : WebView(context) {
    private var startY = 0f

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                startY = event.y
                parent?.requestDisallowInterceptTouchEvent(true)
            }

            android.view.MotionEvent.ACTION_MOVE -> {
                val deltaY = event.y - startY
                if (deltaY > 0 && scrollY == 0) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                } else {
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
