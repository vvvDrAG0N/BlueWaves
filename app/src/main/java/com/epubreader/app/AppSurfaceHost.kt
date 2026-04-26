package com.epubreader.app

import com.epubreader.feature.editbook.EditBookDependencies
import com.epubreader.feature.editbook.EditBookEvent
import com.epubreader.feature.library.LibraryDependencies
import com.epubreader.feature.library.LibraryEvent
import com.epubreader.feature.reader.ReaderDependencies
import com.epubreader.feature.reader.ReaderEvent
import com.epubreader.feature.settings.SettingsDependencies
import com.epubreader.feature.settings.SettingsEvent

internal data class AppSurfaceHost(
    val libraryDependencies: LibraryDependencies,
    val settingsDependencies: SettingsDependencies,
    val readerDependencies: ReaderDependencies,
    val editBookDependencies: EditBookDependencies,
    val onLibraryEvent: (LibraryEvent) -> Unit,
    val onSettingsEvent: (SettingsEvent) -> Unit,
    val onReaderEvent: (ReaderEvent) -> Unit,
    val onEditBookEvent: (EditBookEvent) -> Unit,
    val onBackToLibrary: () -> Unit,
)
