package com.epubreader.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.epubreader.core.model.GlobalSettings
import com.epubreader.core.model.themePaletteSeed
import com.epubreader.feature.editbook.EditBookDependencies
import com.epubreader.feature.editbook.EditBookEvent
import com.epubreader.feature.editbook.EditBookLegoPlugin
import com.epubreader.feature.editbook.EditBookRoute
import com.epubreader.feature.library.LibraryDependencies
import com.epubreader.feature.library.LibraryEvent
import com.epubreader.feature.library.LibraryLegoPlugin
import com.epubreader.feature.library.LibraryRoute
import com.epubreader.feature.reader.ReaderDependencies
import com.epubreader.feature.reader.ReaderEvent
import com.epubreader.feature.reader.ReaderLegoPlugin
import com.epubreader.feature.reader.ReaderRoute
import com.epubreader.feature.settings.SettingsDependencies
import com.epubreader.feature.settings.SettingsEvent
import com.epubreader.feature.settings.SettingsLegoPlugin
import com.epubreader.feature.settings.SettingsRoute

@Composable
internal fun AppNavigationScreenHost(
    currentRoute: AppRoute,
    startupState: AppStartupState,
    globalSettings: GlobalSettings,
    libraryDependencies: LibraryDependencies,
    settingsDependencies: SettingsDependencies,
    readerDependencies: ReaderDependencies,
    editBookDependencies: EditBookDependencies,
    settingsRoute: SettingsRoute,
    libraryRoute: LibraryRoute,
    onLibraryEvent: (LibraryEvent) -> Unit,
    onSettingsEvent: (SettingsEvent) -> Unit,
    onReaderEvent: (ReaderEvent) -> Unit,
    onEditBookEvent: (EditBookEvent) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedContent(
            targetState = currentRoute,
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
            label = "ScreenTransition",
        ) { route ->
            when (route) {
                AppRoute.Library -> LibraryLegoPlugin.Render(
                    route = libraryRoute,
                    dependencies = libraryDependencies,
                    onEvent = onLibraryEvent,
                )

                AppRoute.Settings -> SettingsLegoPlugin.Render(
                    route = settingsRoute,
                    dependencies = settingsDependencies,
                    onEvent = onSettingsEvent,
                )

                is AppRoute.Reader -> ReaderLegoPlugin.Render(
                    route = ReaderRoute(route.bookId),
                    dependencies = readerDependencies,
                    onEvent = onReaderEvent,
                )

                is AppRoute.EditBook -> EditBookLegoPlugin.Render(
                    route = EditBookRoute(route.bookId),
                    dependencies = editBookDependencies,
                    onEvent = onEditBookEvent,
                )
            }
        }

        if (startupState.isWarmUpVisible) {
            AppWarmUpScreen(
                phase = startupState.phase,
                palette = themePaletteSeed(globalSettings.theme, globalSettings.customThemes),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
