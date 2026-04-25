package com.epubreader.feature.reader.internal.runtime.epub

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
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
import com.epubreader.feature.reader.ReaderTheme
import com.epubreader.feature.reader.internal.ui.readerFontFamily
import java.io.File

@Composable
internal fun ReaderChapterLoadingContent(themeColors: ReaderTheme) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = themeColors.foreground)
    }
}

@Composable
internal fun ReaderChapterTextBlock(
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
internal fun ReaderChapterImage(
    filePath: String,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
) {
    if (filePath.isBlank()) {
        return
    }

    val tapModifier = if (onTap != null) {
        Modifier.pointerInput(onTap) {
            detectTapGestures(onTap = { onTap() })
        }
    } else {
        Modifier
    }

    AsyncImage(
        model = File(filePath),
        contentDescription = null,
        modifier = modifier.then(tapModifier),
        contentScale = ContentScale.Fit,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
internal fun ReaderLookupWebViewBottomSheet(
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
                ReaderNestedScrollWebView(ctx).apply {
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
private class ReaderNestedScrollWebView(context: android.content.Context) : WebView(context) {
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
