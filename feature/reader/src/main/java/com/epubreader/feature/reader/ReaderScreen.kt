package com.epubreader.feature.reader

import androidx.compose.runtime.Composable
import com.epubreader.core.model.EpubBook
import com.epubreader.core.model.GlobalSettings
import com.epubreader.data.parser.EpubParser
import com.epubreader.data.settings.SettingsManager
import com.epubreader.feature.reader.internal.shell.ReaderFeatureShell

@Composable
fun ReaderScreen(
    book: EpubBook,
    globalSettings: GlobalSettings = GlobalSettings(),
    settingsManager: SettingsManager,
    parser: EpubParser,
    hostExtensions: ReaderResolvedHostExtensions = ReaderResolvedHostExtensions(),
    onBack: () -> Unit,
) {
    ReaderFeatureShell(
        book = book,
        globalSettings = globalSettings,
        settingsManager = settingsManager,
        parser = parser,
        hostExtensions = hostExtensions,
        onBack = onBack,
    )
}
