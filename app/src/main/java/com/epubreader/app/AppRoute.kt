package com.epubreader.app

import com.epubreader.core.ui.SurfaceId
import com.epubreader.feature.editbook.EditBookRouteArgs
import com.epubreader.feature.editbook.EditBookSurfacePlugin
import com.epubreader.feature.library.LibrarySurfacePlugin
import com.epubreader.feature.reader.ReaderRouteArgs
import com.epubreader.feature.reader.ReaderSurfacePlugin
import com.epubreader.feature.settings.SettingsSurfacePlugin

internal data class AppRoute(
    val surfaceId: SurfaceId,
    val routeArgs: Any? = null,
) {
    companion object {
        val Library: AppRoute = AppRoute(surfaceId = LibrarySurfacePlugin.surfaceId)
        val Settings: AppRoute = AppRoute(surfaceId = SettingsSurfacePlugin.surfaceId)

        fun Reader(bookId: String): AppRoute {
            return AppRoute(
                surfaceId = ReaderSurfacePlugin.surfaceId,
                routeArgs = ReaderRouteArgs(bookId = bookId),
            )
        }

        fun EditBook(bookId: String): AppRoute {
            return AppRoute(
                surfaceId = EditBookSurfacePlugin.surfaceId,
                routeArgs = EditBookRouteArgs(bookId = bookId),
            )
        }
    }
}
