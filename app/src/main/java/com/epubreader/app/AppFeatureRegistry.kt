package com.epubreader.app

import com.epubreader.core.ui.ShellChromeSpec
import com.epubreader.feature.editbook.EditBookLegoPlugin
import com.epubreader.feature.library.LibraryLegoPlugin
import com.epubreader.feature.pdf.legacy.PdfLegacyLegoPlugin
import com.epubreader.feature.reader.ReaderLegoPlugin
import com.epubreader.feature.settings.SettingsLegoPlugin

internal object AppFeatureRegistry {
    val library = LibraryLegoPlugin
    val settings = SettingsLegoPlugin
    val reader = ReaderLegoPlugin
    val editBook = EditBookLegoPlugin
    val pdfLegacy = PdfLegacyLegoPlugin

    fun chromeSpecFor(route: AppRoute): ShellChromeSpec {
        return when (route) {
            AppRoute.Library -> library.chromeSpec
            AppRoute.Settings -> settings.chromeSpec
            is AppRoute.Reader -> reader.chromeSpec
            is AppRoute.EditBook -> editBook.chromeSpec
        }
    }
}
