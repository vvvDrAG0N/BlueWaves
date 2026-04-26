package com.epubreader.feature.reader.internal.shell

import android.app.Activity
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.epubreader.core.model.EpubBook
import com.epubreader.core.model.GlobalSettings
import com.epubreader.data.parser.EpubParser
import com.epubreader.feature.reader.ReaderTheme

private const val AdjacentChapterPrefetchDelayMillis = 2500L
private val ReaderImmersiveHideFlags =
    View.SYSTEM_UI_FLAG_FULLSCREEN or
        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
        View.SYSTEM_UI_FLAG_IMMERSIVE or
        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

internal fun clearReaderImmersiveSystemUiFlags(systemUiVisibility: Int): Int {
    return systemUiVisibility and ReaderImmersiveHideFlags.inv()
}

@Composable
internal fun ReaderSystemBarEffect(
    showControls: Boolean,
    isLookupSheetVisible: Boolean,
    globalSettings: GlobalSettings,
    themeColors: ReaderTheme,
    refreshToken: Int = 0,
) {
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

    LaunchedEffect(showControls, isLookupSheetVisible, globalSettings.showSystemBar, resumeTrigger, refreshToken) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        val lightSystemBarIcons = themeColors.background.luminance() > 0.5f

        windowInsetsController.isAppearanceLightStatusBars = lightSystemBarIcons
        windowInsetsController.isAppearanceLightNavigationBars = lightSystemBarIcons

        if (showControls || isLookupSheetVisible || globalSettings.showSystemBar) {
            window.statusBarColor = themeColors.background.toArgb()
            window.navigationBarColor = themeColors.background.toArgb()
            windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            withFrameNanos { }
            window.decorView.systemUiVisibility =
                clearReaderImmersiveSystemUiFlags(window.decorView.systemUiVisibility)
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        } else {
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            kotlinx.coroutines.delay(450)
            windowInsetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
internal fun ReaderChapterLoadingEffect(
    book: EpubBook,
    parser: EpubParser,
    currentChapterIndex: Int,
    hasChapterElements: Boolean,
    isLoadingChapter: Boolean,
    isChapterSettleComplete: Boolean,
    hasReaderUserInteracted: Boolean,
    onLoadingChapterChange: (Boolean) -> Unit,
    onChapterElementsChange: (List<com.epubreader.core.model.ChapterElement>) -> Unit,
    onChapterSettleCompleteChange: (Boolean) -> Unit,
) {
    LaunchedEffect(book.id, currentChapterIndex) {
        if (currentChapterIndex !in book.spineHrefs.indices) {
            return@LaunchedEffect
        }

        onChapterSettleCompleteChange(false)
        onLoadingChapterChange(true)
        val elements = loadReaderChapterElements(
            parser = parser,
            book = book,
            chapterIndex = currentChapterIndex,
        )
        onChapterElementsChange(elements)
        onLoadingChapterChange(false)
        if (elements.isEmpty()) {
            onChapterSettleCompleteChange(true)
        }
    }

    LaunchedEffect(book.id, currentChapterIndex, hasChapterElements, isLoadingChapter, isChapterSettleComplete, hasReaderUserInteracted) {
        if (!shouldPrefetchAdjacentReaderChapters(
                currentChapterIndex = currentChapterIndex,
                spineSize = book.spineHrefs.size,
                hasChapterElements = hasChapterElements,
                isLoadingChapter = isLoadingChapter,
                isChapterSettleComplete = isChapterSettleComplete,
                hasReaderUserInteracted = hasReaderUserInteracted,
            )
        ) {
            return@LaunchedEffect
        }

        kotlinx.coroutines.delay(AdjacentChapterPrefetchDelayMillis)

        if (!shouldPrefetchAdjacentReaderChapters(
                currentChapterIndex = currentChapterIndex,
                spineSize = book.spineHrefs.size,
                hasChapterElements = hasChapterElements,
                isLoadingChapter = isLoadingChapter,
                isChapterSettleComplete = isChapterSettleComplete,
                hasReaderUserInteracted = hasReaderUserInteracted,
            )
        ) {
            return@LaunchedEffect
        }

        prefetchAdjacentReaderChapters(
            parser = parser,
            book = book,
            chapterIndex = currentChapterIndex,
        )
    }
}
