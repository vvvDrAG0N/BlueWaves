package com.epubreader.feature.settings

import com.epubreader.core.ui.SurfaceId
import com.epubreader.core.ui.SurfaceRouteDecodeResult
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsSurfacePluginTest {

    @Test
    fun surfaceId_matchesSettingsRoute() {
        assertEquals(SurfaceId("settings"), SettingsSurfacePlugin.surfaceId)
    }

    @Test
    fun decodeRouteArgs_acceptsNoArgs() {
        val result = SettingsSurfacePlugin.decodeRouteArgs(routeArgs = null)

        assertEquals(SurfaceRouteDecodeResult.Success(SettingsRoute), result)
    }

    @Test
    fun decodeRouteArgs_rejectsUnexpectedArgs() {
        val result = SettingsSurfacePlugin.decodeRouteArgs(routeArgs = Any())

        assertEquals(
            SurfaceRouteDecodeResult.Failure("Settings does not accept route arguments."),
            result,
        )
    }
}
