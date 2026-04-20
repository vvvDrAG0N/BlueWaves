package com.epubreader.engine.ui.shaders

import android.graphics.RuntimeShader
import androidx.compose.ui.graphics.ShaderBrush
import org.intellij.lang.annotations.Language

/**
 * TitanShaders (V2 Lego)
 * 
 * High-performance AGSL shaders for the Blue Waves ocean ecosystem.
 */
object TitanShaders {

    @Language("AGSL")
    private val LIQUID_WAVE_SRC = """
        uniform float2 iResolution;
        uniform float iTime;
        uniform shader iContent;

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / iResolution.xy;
            
            // Distort UVs like water surface
            float wave = sin(uv.y * 10.0 + iTime * 2.0) * 0.005;
            float wave2 = cos(uv.x * 8.0 + iTime * 1.5) * 0.005;
            uv += float2(wave, wave2);
            
            half4 color = iContent.eval(uv * iResolution.xy);
            
            // Add a subtle "shimmer" / caustics overlay
            float shimmer = sin(uv.x * 20.0 + uv.y * 20.0 + iTime * 5.0) * 0.05;
            color.rgb += shimmer;
            
            return color;
        }
    """.trimIndent()

    /**
     * Creates a Liquid Wave shader brush.
     * Falls back to a solid color/empty brush on devices below API 33.
     */
    fun createLiquidWaveBrush(time: Float, resolution: androidx.compose.ui.geometry.Size): ShaderBrush? {
        if (android.os.Build.VERSION.SDK_INT < 33) return null
        
        val shader = RuntimeShader(LIQUID_WAVE_SRC)
        shader.setFloatUniform("iTime", time)
        shader.setFloatUniform("iResolution", resolution.width, resolution.height)
        return ShaderBrush(shader)
    }
}
