package com.epubreader.feature.reader

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
import kotlinx.coroutines.withContext

data class ReaderRoute(
    val bookId: String,
)

data class ReaderDependencies(
    val parser: EpubParser,
    val settingsManager: SettingsManager,
)

sealed interface ReaderEvent {
    data object Back : ReaderEvent
}

object ReaderLegoPlugin : FeatureLegoPlugin<ReaderRoute, ReaderDependencies, ReaderEvent> {
    override val chromeSpec: ShellChromeSpec = ShellChromeSpec(immersiveSystemBars = true)

    @Composable
    override fun Render(
        route: ReaderRoute,
        dependencies: ReaderDependencies,
        onEvent: (ReaderEvent) -> Unit,
    ) {
        var screenState by remember(route.bookId) { mutableStateOf<ReaderScreenState>(ReaderScreenState.Loading) }

        LaunchedEffect(route.bookId) {
            screenState = loadReaderScreenState(route, dependencies)
        }

        when (val state = screenState) {
            ReaderScreenState.Loading -> ReaderPluginStatusScreen(
                title = "Reader",
                message = "Opening book...",
                showProgress = true,
                onBack = { onEvent(ReaderEvent.Back) },
            )

            is ReaderScreenState.Unavailable -> ReaderPluginStatusScreen(
                title = "Reader",
                message = state.message,
                showProgress = false,
                onBack = { onEvent(ReaderEvent.Back) },
            )

            is ReaderScreenState.Ready -> ReaderScreen(
                book = state.book,
                settingsManager = dependencies.settingsManager,
                parser = dependencies.parser,
                onBack = { onEvent(ReaderEvent.Back) },
            )
        }
    }
}

private sealed interface ReaderScreenState {
    data object Loading : ReaderScreenState
    data class Ready(val book: EpubBook) : ReaderScreenState
    data class Unavailable(val message: String) : ReaderScreenState
}

private suspend fun loadReaderScreenState(
    route: ReaderRoute,
    dependencies: ReaderDependencies,
): ReaderScreenState {
    val loadedBook = withContext(Dispatchers.IO) {
        dependencies.parser.loadBookById(route.bookId)
    } ?: return ReaderScreenState.Unavailable("This book is no longer available.")

    if (loadedBook.sourceFormat == BookFormat.PDF) {
        return ReaderScreenState.Unavailable("PDF reading remains disabled in the active shell.")
    }

    val preparedBook = withContext(Dispatchers.IO) {
        val prepared = dependencies.parser.prepareBookForReading(loadedBook)
        val updated = prepared.copy(lastRead = System.currentTimeMillis())
        dependencies.parser.updateLastRead(updated)
        updated
    }

    return ReaderScreenState.Ready(preparedBook)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderPluginStatusScreen(
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
