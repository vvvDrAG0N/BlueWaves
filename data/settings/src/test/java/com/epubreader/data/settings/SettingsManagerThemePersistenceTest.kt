package com.epubreader.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.epubreader.core.model.CustomTheme
import com.epubreader.core.model.DarkThemeId
import com.epubreader.core.model.LightThemeId
import com.epubreader.core.model.ThemePalette
import com.epubreader.core.model.availableThemeOptions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SettingsManagerThemePersistenceTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val settingsManager = SettingsManager(context)

    @Before
    fun setUp() = runBlocking {
        resetDataStore()
    }

    @After
    fun tearDown() = runBlocking {
        resetDataStore()
    }

    @Test
    fun saveCustomTheme_withActivation_persistsThemeAndSelection() = runBlocking {
        val theme = customTheme()

        settingsManager.saveCustomTheme(theme, activate = true)

        val settings = settingsManager.globalSettings.first()
        assertEquals(theme.id, settings.theme)
        assertEquals(listOf(theme), settings.customThemes)
    }

    @Test
    fun deleteActiveCustomTheme_removesThemeAndFallsBackToPreviousThemeSlot() = runBlocking {
        val theme = customTheme()
        settingsManager.saveCustomTheme(theme, activate = true)
        val expectedFallbackId = availableThemeOptions(listOf(theme))
            .let { options ->
                val activeIndexBefore = options.indexOfFirst { it.id == theme.id }
                val targetIndex = activeIndexBefore - 1
                availableThemeOptions(emptyList()).getOrNull(targetIndex)?.id ?: LightThemeId
            }

        settingsManager.deleteCustomTheme(theme.id)

        val settings = settingsManager.globalSettings.first()
        assertEquals(expectedFallbackId, settings.theme)
        assertTrue(settings.customThemes.isEmpty())
    }

    @Test
    fun saveCustomTheme_rejectsBuiltInThemeIds() = runBlocking {
        settingsManager.setActiveTheme(DarkThemeId)

        settingsManager.saveCustomTheme(
            customTheme().copy(id = DarkThemeId),
            activate = true,
        )

        val settings = settingsManager.globalSettings.first()
        assertEquals(DarkThemeId, settings.theme)
        assertTrue(settings.customThemes.isEmpty())
    }

    private suspend fun resetDataStore() {
        context.settingsDataStore.edit { preferences ->
            preferences.clear()
        }
    }

    private fun customTheme(): CustomTheme {
        return CustomTheme(
            id = "custom-ocean",
            name = "Ocean",
            palette = ThemePalette(
                primary = 0xFF2A6F97,
                secondary = 0xFF468FAF,
                background = 0xFFF4FAFF,
                surface = 0xFFFFFFFF,
                surfaceVariant = 0xFFD7EAF7,
                outline = 0xFF8AA7BB,
                readerBackground = 0xFFEEF8FF,
                readerForeground = 0xFF10212D,
                systemForeground = 0xFF000000,
            ),
        )
    }
}
