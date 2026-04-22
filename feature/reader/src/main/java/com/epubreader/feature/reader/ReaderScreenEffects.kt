package com.epubreader.feature.reader

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.epubreader.core.model.GlobalSettings

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
