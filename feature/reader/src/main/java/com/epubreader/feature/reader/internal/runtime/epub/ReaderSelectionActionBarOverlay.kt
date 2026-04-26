package com.epubreader.feature.reader.internal.runtime.epub

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.epubreader.feature.reader.ReaderTheme
import com.epubreader.feature.reader.internal.ui.TextSelectionActionBar

@Composable
internal fun BoxScope.ReaderSelectionActionBarOverlay(
    visible: Boolean,
    moveToTop: Boolean,
    bottomPadding: Dp,
    themeColors: ReaderTheme,
    copyEnabled: Boolean = true,
    selectAllEnabled: Boolean = true,
    translateEnabled: Boolean = true,
    defineEnabled: Boolean = true,
    onHeightChanged: (Int) -> Unit,
    onCopy: () -> Unit,
    onSelectAll: () -> Unit,
    onTranslate: () -> Unit,
    onDefine: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
        modifier = Modifier
            .align(if (moveToTop) Alignment.TopCenter else Alignment.BottomCenter)
            .then(
                if (moveToTop) {
                    Modifier
                        .padding(top = 24.dp)
                        .statusBarsPadding()
                } else {
                    Modifier
                        .padding(bottom = bottomPadding)
                        .navigationBarsPadding()
                },
            )
            .onGloballyPositioned { coordinates ->
                onHeightChanged(coordinates.size.height)
            },
    ) {
        TextSelectionActionBar(
            themeColors = themeColors,
            copyEnabled = copyEnabled,
            selectAllEnabled = selectAllEnabled,
            translateEnabled = translateEnabled,
            defineEnabled = defineEnabled,
            onCopy = onCopy,
            onSelectAll = onSelectAll,
            onTranslate = onTranslate,
            onDefine = onDefine,
        )
    }
}
