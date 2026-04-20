package com.epubreader.engine.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.epubreader.engine.ui.shaders.TitanShaders
import kotlinx.coroutines.delay

/**
 * VolumetricBookCard (V2 Lego)
 * 
 * A 3D-styled card with AGSL liquid wave effects.
 */
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import com.epubreader.engine.ui.physics.rememberLiquidPhysics

@Composable
fun VolumetricBookCard(
    title: String,
    coverPath: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var time by remember { mutableStateOf(0f) }
    val physics = rememberLiquidPhysics()
    var size by remember { mutableStateOf(IntSize.Zero) }
    
    // Animate time for shader
    LaunchedEffect(Unit) {
        while (true) {
            time += 0.016f
            delay(16)
        }
    }

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.7f)
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val position = event.changes.first().position
                        
                        when (event.type) {
                            androidx.compose.ui.input.pointer.PointerEventType.Move -> {
                                physics.updateInteraction(position, size)
                            }
                            androidx.compose.ui.input.pointer.PointerEventType.Exit -> {
                                physics.reset()
                            }
                        }
                    }
                }
            }
            .graphicsLayer {
                rotationX = physics.rotationX.value
                rotationY = physics.rotationY.value
                scaleX = physics.scale.value
                scaleY = physics.scale.value
                cameraDistance = 12f
            },
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Apply the Liquid Shader as a background brush
            val shaderBrush = TitanShaders.createLiquidWaveBrush(time, androidx.compose.ui.geometry.Size(400f, 600f))
            
            if (coverPath != null) {
                AsyncImage(
                    model = coverPath,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = shaderBrush ?: Brush.verticalGradient(
                                colors = listOf(Color(0xFF1A237E), Color(0xFF0D47A1))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            
            // Glass overlay / Shine
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.15f), Color.Transparent)
                        )
                    )
            )
        }
    }
}
