package com.epubreader.app

import com.epubreader.Screen

internal sealed interface AppRoute {
    val screen: Screen

    data object Library : AppRoute {
        override val screen: Screen = Screen.Library
    }

    data object Settings : AppRoute {
        override val screen: Screen = Screen.Settings
    }

    data class Reader(
        val bookId: String,
    ) : AppRoute {
        override val screen: Screen = Screen.Reader
    }

    data class EditBook(
        val bookId: String,
    ) : AppRoute {
        override val screen: Screen = Screen.EditBook
    }
}
