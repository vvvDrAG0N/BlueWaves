package com.epubreader.engine.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.epubreader.engine.ui.navigation.TitanScreen

import com.epubreader.engine.ui.utils.rememberTitanHaptics

/**
 * TitanNavbar (V2)
 */
@Composable
fun TitanNavbar(
    currentScreen: TitanScreen,
    onNavigate: (TitanScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberTitanHaptics()
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp)
            .height(64.dp)
            .background(Color.Black.copy(alpha = 0.2f), CircleShape)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavbarItem(
            label = "LIB",
            isSelected = currentScreen == TitanScreen.Library,
            onClick = { 
                haptics.tap()
                onNavigate(TitanScreen.Library) 
            }
        )
        NavbarItem(
            label = "SRC",
            isSelected = currentScreen == TitanScreen.Search,
            onClick = { 
                haptics.tap()
                onNavigate(TitanScreen.Search) 
            }
        )
    }
}

@Composable
private fun NavbarItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (isSelected) Color.White.copy(alpha = 0.1f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Text(
            text = label,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f)
        )
    }
}
