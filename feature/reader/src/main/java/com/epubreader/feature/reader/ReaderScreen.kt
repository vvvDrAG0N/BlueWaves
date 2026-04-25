package com.epubreader.feature.reader

import androidx.compose.runtime.Composable
import com.epubreader.core.model.EpubBook
import com.epubreader.data.parser.EpubParser
import com.epubreader.data.settings.SettingsManager
import com.epubreader.feature.reader.internal.shell.ReaderFeatureShell

@Composable
fun ReaderScreen(
    book: EpubBook,
    settingsManager: SettingsManager,
    parser: EpubParser,
    onBack: () -> Unit,
) {
    ReaderFeatureShell(
        book = book,
        settingsManager = settingsManager,
        parser = parser,
        onBack = onBack,
    )
}
