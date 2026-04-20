package com.epubreader.engine.ui.components

import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import com.epubreader.engine.ui.utils.TitanHaptics
import kotlin.math.abs

/**
 * LiquidOverscrollState (V2 Standalone)
 */
class LiquidOverscrollState(private val haptics: TitanHaptics?) {
    var stretchAmount by mutableFloatStateOf(0f)
        private set
    
    private var hasTriggeredHaptic = false

    fun onScroll(delta: Float) {
        stretchAmount += delta * 0.2f
        
        // Trigger haptic at impact threshold
        if (abs(stretchAmount) > 20f && !hasTriggeredHaptic) {
            haptics?.impact()
            hasTriggeredHaptic = true
        }
    }

    fun update() {
        if (stretchAmount != 0f) {
            stretchAmount *= 0.85f // Slightly stickier return
            if (abs(stretchAmount) < 0.1f) {
                stretchAmount = 0f
                hasTriggeredHaptic = false
            }
        }
    }

    val modifier: Modifier
        get() = Modifier.offset(y = stretchAmount.dp)
}

@Composable
fun rememberLiquidOverscrollState(haptics: TitanHaptics? = null): LiquidOverscrollState {
    val state = remember { LiquidOverscrollState(haptics) }
    
    // Continuous physics update
    LaunchedEffect(Unit) {
        while(true) {
            withFrameMillis { _ ->
                state.update()
            }
        }
    }
    
    return state
}
