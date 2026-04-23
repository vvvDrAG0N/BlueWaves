package com.epubreader.app

import android.app.Activity
import android.view.View
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.LifecycleOwner
import com.epubreader.Screen
import com.epubreader.core.model.EpubBook
import com.epubreader.core.model.GlobalSettings
import com.epubreader.data.parser.EpubParser
import kotlinx.coroutines.CoroutineScope

@Composable
internal fun AppNavigationSideEffects(
    view: View,
    globalSettings: GlobalSettings,
    drawerState: DrawerState,
    currentScreen: Screen,
    books: List<EpubBook>,
    selectedBook: EpubBook?,
    parser: EpubParser,
    lifecycleOwner: LifecycleOwner,
    scope: CoroutineScope,
    onClearFolderSelection: () -> Unit,
    onMovingModeChange: (Boolean) -> Unit,
    onBookUpdated: (EpubBook) -> Unit,
) {
    LaunchedEffect(globalSettings.hapticFeedback) {
        view.isHapticFeedbackEnabled = globalSettings.hapticFeedback
    }

    LaunchedEffect(drawerState.isOpen) {
        if (!drawerState.isOpen) {
            onClearFolderSelection()
            onMovingModeChange(false)
        }
    }

    val isLightAppTheme = androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() > 0.5f
    val lifecycleOwnerForResume = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var resumeTrigger by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwnerForResume) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                resumeTrigger++
            }
        }
        lifecycleOwnerForResume.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwnerForResume.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(currentScreen, globalSettings.showSystemBar, resumeTrigger, isLightAppTheme) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)

        if (currentScreen != Screen.Reader) {
            windowInsetsController.isAppearanceLightStatusBars = isLightAppTheme
            windowInsetsController.isAppearanceLightNavigationBars = isLightAppTheme

            if (globalSettings.showSystemBar) {
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            } else if (currentScreen == Screen.Library) {
                windowInsetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    ObserveLegacyPdfConversionState(
        context = view.context,
        books = books,
        selectedBook = selectedBook,
        parser = parser,
        lifecycleOwner = lifecycleOwner,
        scope = scope,
        onBookUpdated = onBookUpdated,
    )
}
