package com.epubreader.data.settings

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsManagerJsonTest {

    @Test
    fun safeJson_parsers_fallBackForMalformedJson() {
        val jsonObject = safeJsonObject("{broken")
        val jsonArray = safeJsonArray("{broken")

        assertEquals(0, jsonObject.length())
        assertEquals(0, jsonArray.length())
    }

    @Test
    fun createFolder_styleUpdate_appendsOrderAndPreservesExistingSorts() {
        val folderSortsJson = safeJsonObject("""{"Sci-Fi":"title_asc"}""")
        folderSortsJson.put("Mystery", DefaultLibrarySort)

        val existingOrder = safeJsonArray("""["Sci-Fi"]""").toStringList()
        val updatedOrder = (existingOrder + "Mystery").toJsonArray()

        assertEquals("title_asc", folderSortsJson.getString("Sci-Fi"))
        assertEquals(DefaultLibrarySort, folderSortsJson.getString("Mystery"))
        assertEquals(listOf("Sci-Fi", "Mystery"), updatedOrder.toStringList())
    }

    @Test
    fun renameFolder_styleUpdate_movesMetadataWithoutLosingUnrelatedData() {
        val folderSortsJson = strictJsonObject(
            """{"Sci-Fi":"title_asc","History":"author_desc","Poetry":"recent_desc"}""",
        )
        folderSortsJson.renameStringEntry("Sci-Fi", "Classics")

        val folderOrderJson = strictJsonArray("""["History","Sci-Fi","Poetry"]""")
            .replacingEntry("Sci-Fi", "Classics")

        val bookGroupsJson = strictJsonObject(
            """{"book-1":"Sci-Fi","book-2":"History","book-3":"Sci-Fi","book-4":"Poetry"}""",
        )
        bookGroupsJson.replaceStringValues("Sci-Fi", "Classics")

        assertFalse(folderSortsJson.has("Sci-Fi"))
        assertEquals("title_asc", folderSortsJson.getString("Classics"))
        assertEquals("author_desc", folderSortsJson.getString("History"))
        assertEquals("recent_desc", folderSortsJson.getString("Poetry"))

        assertEquals(listOf("History", "Classics", "Poetry"), folderOrderJson.toStringList())

        assertEquals("Classics", bookGroupsJson.getString("book-1"))
        assertEquals("History", bookGroupsJson.getString("book-2"))
        assertEquals("Classics", bookGroupsJson.getString("book-3"))
        assertEquals("Poetry", bookGroupsJson.getString("book-4"))
    }

    @Test
    fun deleteFolder_styleUpdate_removesTargetFolderAndPreservesUnrelatedData() {
        val folderSortsJson = strictJsonObject(
            """{"Sci-Fi":"title_asc","History":"author_desc","Poetry":"recent_desc"}""",
        )
        folderSortsJson.remove("Sci-Fi")

        val folderOrderJson = strictJsonArray("""["History","Sci-Fi","Poetry"]""")
            .withoutEntry("Sci-Fi")

        val bookGroupsJson = strictJsonObject(
            """{"book-1":"Sci-Fi","book-2":"History","book-3":"Sci-Fi","book-4":"Poetry"}""",
        )
        bookGroupsJson.removeEntriesWithStringValue("Sci-Fi")

        assertFalse(folderSortsJson.has("Sci-Fi"))
        assertEquals("author_desc", folderSortsJson.getString("History"))
        assertEquals("recent_desc", folderSortsJson.getString("Poetry"))

        assertEquals(listOf("History", "Poetry"), folderOrderJson.toStringList())

        assertFalse(bookGroupsJson.has("book-1"))
        assertEquals("History", bookGroupsJson.getString("book-2"))
        assertFalse(bookGroupsJson.has("book-3"))
        assertEquals("Poetry", bookGroupsJson.getString("book-4"))
    }

    @Test
    fun renameStringEntry_isNoOpWhenSourceKeyMissing() {
        val jsonObject = JSONObject("""{"History":"author_desc"}""")

        jsonObject.renameStringEntry("Sci-Fi", "Classics")

        assertEquals(1, jsonObject.length())
        assertEquals("author_desc", jsonObject.getString("History"))
        assertFalse(jsonObject.has("Classics"))
    }

    @Test
    fun arrayHelpers_roundTripCreateAndDeleteStyleChanges() {
        val createdOrder = listOf("History", "Sci-Fi", "Mystery").toJsonArray()
        val renamedOrder = createdOrder.replacingEntry("Sci-Fi", "Classics")
        val deletedOrder = renamedOrder.withoutEntry("History")

        assertEquals(listOf("History", "Sci-Fi", "Mystery"), createdOrder.toStringList())
        assertEquals(listOf("History", "Classics", "Mystery"), renamedOrder.toStringList())
        assertEquals(listOf("Classics", "Mystery"), deletedOrder.toStringList())
    }

    @Test
    fun removeEntriesWithStringValue_onlyRemovesMatchingMappings() {
        val bookGroupsJson = JSONObject(
            """{"book-1":"Sci-Fi","book-2":"History","book-3":"Sci-Fi","book-4":"History"}""",
        )

        bookGroupsJson.removeEntriesWithStringValue("Sci-Fi")

        assertFalse(bookGroupsJson.has("book-1"))
        assertEquals("History", bookGroupsJson.getString("book-2"))
        assertFalse(bookGroupsJson.has("book-3"))
        assertEquals("History", bookGroupsJson.getString("book-4"))
        assertEquals(2, bookGroupsJson.length())
    }
}
