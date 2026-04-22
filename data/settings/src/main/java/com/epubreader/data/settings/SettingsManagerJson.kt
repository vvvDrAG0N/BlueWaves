package com.epubreader.data.settings

import org.json.JSONArray
import org.json.JSONObject

/**
 * AI_LOAD_STRATEGY
 * - Read this file only when changing JSON-backed folder/group/order persistence behavior.
 * - `safe*` parsers preserve existing fallback behavior.
 * - `strict*` parsers preserve existing fail-fast behavior where the original code built JSON directly.
 */
internal fun safeJsonObject(raw: String?, fallback: String = EmptyJsonObject): JSONObject {
    return try {
        JSONObject(raw ?: fallback)
    } catch (_: Exception) {
        JSONObject(fallback)
    }
}

internal fun safeJsonArray(raw: String?, fallback: String = EmptyJsonArray): JSONArray {
    return try {
        JSONArray(raw ?: fallback)
    } catch (_: Exception) {
        JSONArray(fallback)
    }
}

internal fun strictJsonObject(raw: String?, fallback: String = EmptyJsonObject): JSONObject {
    return JSONObject(raw ?: fallback)
}

internal fun strictJsonArray(raw: String?, fallback: String = EmptyJsonArray): JSONArray {
    return JSONArray(raw ?: fallback)
}

internal fun JSONArray.toStringList(): List<String> {
    return buildList {
        for (index in 0 until length()) {
            add(getString(index))
        }
    }
}

internal fun Iterable<String>.toJsonArray(): JSONArray {
    val jsonArray = JSONArray()
    forEach { jsonArray.put(it) }
    return jsonArray
}

internal fun JSONArray.replacingEntry(oldValue: String, newValue: String): JSONArray {
    val updated = JSONArray()
    for (index in 0 until length()) {
        val value = getString(index)
        updated.put(if (value == oldValue) newValue else value)
    }
    return updated
}

internal fun JSONArray.withoutEntry(targetValue: String): JSONArray {
    val updated = JSONArray()
    for (index in 0 until length()) {
        val value = getString(index)
        if (value != targetValue) {
            updated.put(value)
        }
    }
    return updated
}

internal fun JSONObject.renameStringEntry(oldName: String, newName: String) {
    if (!has(oldName)) {
        return
    }

    val value = getString(oldName)
    remove(oldName)
    put(newName, value)
}

internal fun JSONObject.replaceStringValues(oldValue: String, newValue: String) {
    val keys = keys()
    while (keys.hasNext()) {
        val key = keys.next()
        if (getString(key) == oldValue) {
            put(key, newValue)
        }
    }
}

internal fun JSONObject.removeEntriesWithStringValue(targetValue: String) {
    val keysToRemove = mutableListOf<String>()
    val keys = keys()
    while (keys.hasNext()) {
        val key = keys.next()
        if (getString(key) == targetValue) {
            keysToRemove.add(key)
        }
    }

    keysToRemove.forEach(::remove)
}
