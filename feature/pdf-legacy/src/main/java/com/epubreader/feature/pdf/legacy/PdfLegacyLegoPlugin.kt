package com.epubreader.feature.pdf.legacy

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.epubreader.core.model.BookFormat
import com.epubreader.core.model.EpubBook
import com.epubreader.core.ui.FeatureLegoPlugin
import com.epubreader.core.ui.ShellChromeSpec
import com.epubreader.core.ui.getStaticWindowInsets
import com.epubreader.data.parser.EpubParser
import com.epubreader.data.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PdfLegacyRoute(
    val bookId: String,
)

data class PdfLegacyDependencies(
    val parser: EpubParser,
    val settingsManager: SettingsManager,
)

sealed interface PdfLegacyEvent {
    data object Back : PdfLegacyEvent
    data class OpenGeneratedEpub(val bookId: String) : PdfLegacyEvent
}

object PdfLegacyLegoPlugin : FeatureLegoPlugin<PdfLegacyRoute, PdfLegacyDependencies, PdfLegacyEvent> {
    override val chromeSpec: ShellChromeSpec = ShellChromeSpec(immersiveSystemBars = true)

    @Composable
    override fun Render(
        route: PdfLegacyRoute,
        dependencies: PdfLegacyDependencies,
        onEvent: (PdfLegacyEvent) -> Unit,
    ) {
        val scope = rememberCoroutineScope()
        var screenState by remember(route.bookId) { mutableStateOf<PdfLegacyScreenState>(PdfLegacyScreenState.Loading) }

        LaunchedEffect(route.bookId) {
            screenState = loadPdfLegacyScreenState(route, dependencies)
        }

        when (val state = screenState) {
            PdfLegacyScreenState.Loading -> PdfLegacyPluginStatusScreen(
                title = "PDF Legacy",
                message = "Opening parked PDF runtime...",
                showProgress = true,
                onBack = { onEvent(PdfLegacyEvent.Back) },
            )

            is PdfLegacyScreenState.Unavailable -> PdfLegacyPluginStatusScreen(
                title = "PDF Legacy",
                message = state.message,
                showProgress = false,
                onBack = { onEvent(PdfLegacyEvent.Back) },
            )

            is PdfLegacyScreenState.Ready -> PdfReaderScreen(
                book = state.book,
                settingsManager = dependencies.settingsManager,
                parser = dependencies.parser,
                onBack = { onEvent(PdfLegacyEvent.Back) },
                onOpenGeneratedEpub = { onEvent(PdfLegacyEvent.OpenGeneratedEpub(state.book.id)) },
                onRetryPdfConversion = {
                    scope.launch {
                        val retriedBook = withContext(Dispatchers.IO) {
                            dependencies.parser.retryPdfConversion(state.book)
                        }
                        screenState = if (retriedBook != null) {
                            PdfLegacyScreenState.Ready(retriedBook)
                        } else {
                            PdfLegacyScreenState.Unavailable("Couldn't restart PDF conversion.")
                        }
                    }
                },
            )
        }
    }
}

private sealed interface PdfLegacyScreenState {
    data object Loading : PdfLegacyScreenState
    data class Ready(val book: EpubBook) : PdfLegacyScreenState
    data class Unavailable(val message: String) : PdfLegacyScreenState
}

private suspend fun loadPdfLegacyScreenState(
    route: PdfLegacyRoute,
    dependencies: PdfLegacyDependencies,
): PdfLegacyScreenState {
    val loadedBook = withContext(Dispatchers.IO) {
        dependencies.parser.loadBookById(route.bookId)
    } ?: return PdfLegacyScreenState.Unavailable("This PDF is no longer available.")

    if (loadedBook.sourceFormat != BookFormat.PDF) {
        return PdfLegacyScreenState.Unavailable("This route is reserved for parked PDF books.")
    }

    val preparedBook = withContext(Dispatchers.IO) {
        dependencies.parser.prepareBookForReading(loadedBook)
    }

    return PdfLegacyScreenState.Ready(preparedBook)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfLegacyPluginStatusScreen(
    title: String,
    message: String,
    showProgress: Boolean,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    Scaffold(
        contentWindowInsets = getStaticWindowInsets(),
        topBar = {
            TopAppBar(
                windowInsets = getStaticWindowInsets(),
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (showProgress) {
                    CircularProgressIndicator()
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                if (!showProgress) {
                    TextButton(onClick = onBack) {
                        Text("Back to Library")
                    }
                }
            }
        }
    }
}
