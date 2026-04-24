package com.epubreader.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import java.io.File

@Composable
internal fun rememberLibraryCoverModel(
    coverPath: String,
    width: Dp,
    height: Dp,
): ImageRequest {
    val context = LocalContext.current
    val density = LocalDensity.current
    val pixelWidth = with(density) { width.roundToPx().coerceAtLeast(1) }
    val pixelHeight = with(density) { height.roundToPx().coerceAtLeast(1) }

    return remember(context, coverPath, pixelWidth, pixelHeight) {
        ImageRequest.Builder(context)
            .data(File(coverPath))
            .size(pixelWidth, pixelHeight)
            .precision(Precision.INEXACT)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(false)
            .build()
    }
}
