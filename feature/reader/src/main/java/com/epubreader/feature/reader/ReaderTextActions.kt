/**
 * AI_READ_AFTER: ReaderScreenControls.kt
 * AI_RELEVANT_TO: [Text Selection, Define, Translate, In-App WebView]
 * PURPOSE: URL builders and sealed class for in-app WebView lookup actions.
 *
 * This file provides the URL construction for Define (Google search)
 * and Translate (Google Translate) actions. The actual WebView rendering
 * is handled by WebViewBottomSheet in ReaderScreenControls.kt.
 *
 * No external intents or PackageManager queries are used.
 */
package com.epubreader.feature.reader.internal.runtime.epub

import android.net.Uri

/**
 * Represents an in-app web lookup action with a title and URL.
 */
internal sealed class WebLookupAction(val title: String, val url: String) {
    class Define(text: String) : WebLookupAction(
        title = "Define",
        url = "https://www.google.com/search?q=define+${Uri.encode(text)}"
    )

    class Translate(text: String, targetLanguage: String = "ar") : WebLookupAction(
        title = "Translate",
        url = "https://translate.google.com/?sl=auto&tl=${targetLanguage}&text=${Uri.encode(text)}"
    )
}
