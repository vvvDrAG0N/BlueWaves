package com.epubreader.feature.settings

internal fun themeEditorColorTestTag(key: String): String? {
    return when (key) {
        "accent" -> "custom_theme_primary"
        "app_background" -> "custom_theme_background"
        "chrome_accent" -> "custom_theme_secondary"
        "app_surface" -> "custom_theme_surface"
        "app_surface_variant" -> "custom_theme_surface_variant"
        "app_outline" -> "custom_theme_outline"
        "reader_background" -> "custom_theme_reader_background"
        "reader_foreground" -> "custom_theme_reader_text"
        "app_foreground" -> "custom_theme_system_text"
        "app_foreground_muted" -> "custom_theme_app_text_muted"
        "reader_foreground_muted" -> "custom_theme_reader_text_muted"
        "reader_accent" -> "custom_theme_reader_accent"
        "overlay_scrim" -> "custom_theme_overlay_scrim"
        "startup_background" -> "custom_theme_startup_background"
        "startup_foreground" -> "custom_theme_startup_foreground"
        "favorite_accent" -> "custom_theme_favorite_accent"
        "cover_overlay_scrim" -> "custom_theme_cover_overlay_scrim"
        else -> null
    }
}
