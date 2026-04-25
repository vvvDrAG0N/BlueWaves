package com.epubreader.core.model

enum class ReaderContentEngine(
    val storageValue: String,
    val displayName: String,
) {
    LEGACY("legacy", "Legacy"),
    COMPOSE_LAZY_IMPROVED("compose_lazy_improved", "Compose Lazy Improved"),
    TEXT_VIEW("text_view", "TextView");

    companion object {
        fun fromStorageValue(raw: String?): ReaderContentEngine {
            return entries.firstOrNull { it.storageValue == raw } ?: LEGACY
        }
    }
}
