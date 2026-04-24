package com.epubreader.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.epubreader.R
import com.epubreader.core.model.ThemePalette

internal const val AppWarmUpScreenTag = "app_warm_up_screen"

@Composable
internal fun AppWarmUpScreen(
    phase: StartupPhase,
    palette: ThemePalette,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val backgroundColor = Color(palette.startupBackground)
    val titleColor = Color(palette.startupForeground)
    val messageColor = Color(palette.startupForeground).copy(alpha = 0.72f)
    val progressColor = Color(palette.accent)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {},
            )
            .testTag(AppWarmUpScreenTag),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_icon_og),
                contentDescription = null,
                modifier = Modifier.size(88.dp),
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = warmUpStatusText(phase),
                style = MaterialTheme.typography.bodyLarge,
                color = messageColor,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(22.dp))

            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 3.dp,
                color = progressColor,
                trackColor = Color.Transparent,
            )
        }
    }
}

internal fun warmUpStatusText(phase: StartupPhase): String {
    return when (phase) {
        StartupPhase.WaitingForSettings -> "Loading your settings"
        StartupPhase.EvaluatingStartup -> "Checking startup state"
        StartupPhase.LoadingLibrary -> "Preparing your library"
        StartupPhase.Ready -> "Preparing your library"
    }
}
