package com.epubreader.feature.reader.internal.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.epubreader.core.model.GlobalSettings
import com.epubreader.feature.reader.GlobalSettingsTransform

@Composable
fun ReaderStatusSettingsRow(
    settings: GlobalSettings,
    onUpdateSettings: (GlobalSettingsTransform) -> Unit,
    modifier: Modifier = Modifier,
    isReaderUI: Boolean = false,
    isSystemBarVisible: Boolean = false,
    showHeader: Boolean = true,
    primaryColor: Color? = null,
    onSurfaceColor: Color? = null,
) {
    com.epubreader.core.ui.ReaderStatusSettingsRow(
        settings = settings,
        onUpdateSettings = onUpdateSettings,
        modifier = modifier,
        isReaderUI = isReaderUI,
        isSystemBarVisible = isSystemBarVisible,
        showHeader = showHeader,
        primaryColor = primaryColor,
        onSurfaceColor = onSurfaceColor,
    )
}
