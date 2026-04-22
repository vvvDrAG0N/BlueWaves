package com.epubreader.core.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

@Composable
fun getStaticWindowInsets(): WindowInsets {
    val view = LocalView.current
    val density = LocalDensity.current
    val insetsPx = remember(view) {
        ViewCompat.getRootWindowInsets(view)?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())
    }
    
    val topDp = with(density) { (insetsPx?.top?.takeIf { it > 0 } ?: 72).toDp() }
    val bottomDp = with(density) { (insetsPx?.bottom ?: 0).toDp() }
    val leftDp = with(density) { (insetsPx?.left ?: 0).toDp() }
    val rightDp = with(density) { (insetsPx?.right ?: 0).toDp() }
    
    return WindowInsets(left = leftDp, top = topDp, right = rightDp, bottom = bottomDp)
}
