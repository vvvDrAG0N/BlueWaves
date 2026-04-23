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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.epubreader.R

internal const val AppWarmUpScreenTag = "app_warm_up_screen"

@Composable
internal fun AppWarmUpScreen(
    phase: StartupPhase,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {},
            )
            .testTag(AppWarmUpScreenTag),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            colorScheme.primary.copy(alpha = 0.08f),
                            colorScheme.background,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .size(280.dp)
                .offset(x = 140.dp, y = (-60).dp)
                .clip(CircleShape)
                .background(colorScheme.primary.copy(alpha = 0.12f)),
        )
        Box(
            modifier = Modifier
                .size(220.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-72).dp, y = 48.dp)
                .clip(CircleShape)
                .background(colorScheme.secondary.copy(alpha = 0.08f)),
        )

        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            shape = RoundedCornerShape(30.dp),
            color = colorScheme.surface.copy(alpha = 0.94f),
            tonalElevation = 10.dp,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = colorScheme.primaryContainer.copy(alpha = 0.82f),
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_warm_up_mark),
                        contentDescription = null,
                        modifier = Modifier
                            .size(88.dp)
                            .padding(14.dp),
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = stringResource(id = R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(12.dp))

                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp,
                )

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = warmUpStatusText(phase),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
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
