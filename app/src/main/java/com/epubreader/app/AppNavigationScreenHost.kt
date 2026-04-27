package com.epubreader.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import com.epubreader.feature.reader.ReaderSurfacePlugin

@Composable
internal fun AppNavigationScreenHost(
    currentRoute: AppRoute,
    startupState: AppStartupState,
    globalSettings: GlobalSettings,
    surfaceHost: AppSurfaceHost,
) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedContent(
            targetState = currentRoute,
            transitionSpec = {
                if (shouldAnimateAppRouteTransition(initialState, targetState)) {
                    fadeIn(tween(250)) togetherWith fadeOut(tween(250))
                } else {
                    EnterTransition.None togetherWith ExitTransition.None
                }
            },
            label = "ScreenTransition",
        ) { route ->
            AppSurfaceRegistry.resolve(route).Render(surfaceHost)
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

internal fun shouldAnimateAppRouteTransition(
    initialRoute: AppRoute,
    targetRoute: AppRoute,
): Boolean {
    val readerSurfaceId = ReaderSurfacePlugin.surfaceId
    return initialRoute.surfaceId != readerSurfaceId && targetRoute.surfaceId != readerSurfaceId
}
