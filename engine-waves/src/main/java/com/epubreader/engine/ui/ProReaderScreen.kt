package com.epubreader.engine.ui

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import com.epubreader.engine.WavesEngine

private const val BLUE_WAVES_SHADER = """
    uniform float2 uSize;
    uniform float uTime;
    uniform float4 uBaseColor;
    
    float4 main(float2 fragCoord) {
        float2 uv = fragCoord / uSize;
        float wave = sin(uv.x * 10.0 + uTime) * 0.02 + sin(uv.y * 8.0 + uTime * 0.5) * 0.01;
        float3 color = uBaseColor.rgb + float3(wave, wave * 0.5, 0.1);
        return float4(color, 1.0);
    }
"""

@Composable
fun ProReaderScreen(
    rawContent: String,
    engine: WavesEngine,
    fontSize: Float = 18f,
    baseColor: Color = Color(0xFFF4ECD8)
) {
    val context = LocalContext.current
    var lineCount by remember { mutableIntStateOf(0) }
    
    val infiniteTransition = rememberInfiniteTransition(label = "Waves")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Restart),
        label = "Time"
    )

    // Trigger Native Layout
    LaunchedEffect(rawContent) {
        engine.layoutChapter(rawContent, 1080f, fontSize)
        lineCount = engine.getLineCount()
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        // 1. (Background handled by TitanOceanShell)

        // 2. Draw the Native Lines
        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = fontSize * context.resources.displayMetrics.density
                isAntiAlias = true
                typeface = android.graphics.Typeface.SERIF
            }

            for (i in 0 until lineCount) {
                val text = engine.getLineText(i) ?: ""
                val y = (engine.getLineY(i) + 100f) * context.resources.displayMetrics.density
                
                canvas.nativeCanvas.drawText(text, 60f, y, paint)
            }
        }
    }
}
