package com.epubreader.engine.ui.navigation

import androidx.compose.runtime.*

/**
 * TitanScreen (V2)
 * 
 * The available navigation destinations in the Titan ecosystem.
 */
enum class TitanScreen {
    Library,
    Reader,
    Search
}

/**
 * TitanNavigationController (V2)
 * 
 * An atomic state manager for the Blue Waves navigation ecosystem.
 * This handles screen switching, back-stack logic, and navigation state.
 */
class TitanNavigationController(initialScreen: TitanScreen = TitanScreen.Library) {
    var currentScreen by mutableStateOf(initialScreen)
        private set

    fun navigateTo(screen: TitanScreen) {
        currentScreen = screen
    }
}

@Composable
fun rememberTitanNavigationController(
    initialScreen: TitanScreen = TitanScreen.Library
): TitanNavigationController {
    return remember { TitanNavigationController(initialScreen) }
}
