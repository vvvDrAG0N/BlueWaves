package com.epubreader.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.epubreader.core.model.GlobalSettings

@Composable
fun ReaderStatusSettingsRow(
    settings: GlobalSettings,
    onUpdateSettings: (GlobalSettingsTransform) -> Unit,
    modifier: Modifier = Modifier,
    isReaderUI: Boolean = false,
    isSystemBarVisible: Boolean = false,
    showHeader: Boolean = true,
) {
    com.epubreader.core.ui.ReaderStatusSettingsRow(
        settings = settings,
        onUpdateSettings = onUpdateSettings,
        modifier = modifier,
        isReaderUI = isReaderUI,
        isSystemBarVisible = isSystemBarVisible,
        showHeader = showHeader,
    )
}
