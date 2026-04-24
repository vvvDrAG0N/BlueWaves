package com.epubreader.feature.reader

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.epubreader.core.model.EpubBook
import com.epubreader.core.model.GlobalSettings
import com.epubreader.data.parser.EpubParser

private const val AdjacentChapterPrefetchDelayMillis = 2500L

@Composable
internal fun ReaderSystemBarEffect(
    showControls: Boolean,
    globalSettings: GlobalSettings,
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

    LaunchedEffect(showControls, globalSettings.showSystemBar, resumeTrigger) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)

        if (showControls || globalSettings.showSystemBar) {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        } else {
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
