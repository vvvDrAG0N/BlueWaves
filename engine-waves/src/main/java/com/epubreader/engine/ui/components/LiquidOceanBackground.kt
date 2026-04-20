package com.epubreader.engine.ui.components

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb

private const val BLUE_WAVES_SHADER = """
    uniform float2 uSize;
    uniform float uTime;
    layout(color) uniform float4 uBaseColor;
    
    float4 main(float2 fragCoord) {
        float2 uv = fragCoord / uSize;
        float wave = sin(uv.x * 10.0 + uTime) * 0.02 + sin(uv.y * 8.0 + uTime * 0.5) * 0.01;
        float3 color = uBaseColor.rgb + float3(wave, wave * 0.5, 0.1);
        return float4(color, 1.0);
    }
"""

@Composable
fun LiquidOceanBackground(
    baseColor: Color = Color(0xFFF4ECD8)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "Waves")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Restart),
        label = "Time"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val shader = RuntimeShader(BLUE_WAVES_SHADER)
            shader.setFloatUniform("uSize", size.width, size.height)
            shader.setFloatUniform("uTime", time)
            shader.setColorUniform("uBaseColor", baseColor.toArgb())
            
            drawRect(brush = ShaderBrush(shader))
        } else {
            drawRect(baseColor)
        }
    }
}
