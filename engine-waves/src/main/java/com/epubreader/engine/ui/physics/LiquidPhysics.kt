package com.epubreader.engine.ui.physics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * LiquidPhysics (V2 Lego)
 * 
 * High-performance spring dynamics for 3D UI elements.
 */
class LiquidPhysicsState(private val scope: CoroutineScope) {
    val rotationX = Animatable(0f)
    val rotationY = Animatable(0f)
    val scale = Animatable(1f)

    private val springSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )

    fun updateInteraction(offset: Offset, size: IntSize) {
        if (size.width == 0 || size.height == 0) return
        
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        
        val tiltY = (offset.x - centerX) / centerX * 15f
        val tiltX = (offset.y - centerY) / centerY * -15f
        
        scope.launch { rotationX.animateTo(tiltX, springSpec) }
        scope.launch { rotationY.animateTo(tiltY, springSpec) }
        scope.launch { scale.animateTo(1.05f, springSpec) }
    }

    fun reset() {
        scope.launch { rotationX.animateTo(0f, springSpec) }
        scope.launch { rotationY.animateTo(0f, springSpec) }
        scope.launch { scale.animateTo(1f, springSpec) }
    }
}

@Composable
fun rememberLiquidPhysics(): LiquidPhysicsState {
    val scope = rememberCoroutineScope()
    return remember { LiquidPhysicsState(scope) }
}
