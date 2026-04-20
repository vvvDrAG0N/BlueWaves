package com.epubreader.engine.ui.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * TitanHaptics (V2 Atomic)
 */
class TitanHaptics(context: Context) {
    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    fun tap() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            vibrateComposition(VibrationEffect.Composition.PRIMITIVE_CLICK)
        } else {
            legacyVibrate(20)
        }
    }

    fun impact() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            vibrateComposition(VibrationEffect.Composition.PRIMITIVE_TICK)
        } else {
            legacyVibrate(10)
        }
    }

    fun heavy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        } else {
            legacyVibrate(50)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun vibrateComposition(primitiveId: Int) {
        val effect = VibrationEffect.startComposition()
            .addPrimitive(primitiveId)
            .compose()
        vibrator.vibrate(effect)
    }

    private fun legacyVibrate(millis: Long) {
        @Suppress("DEPRECATION")
        vibrator.vibrate(millis)
    }
}

@Composable
fun rememberTitanHaptics(): TitanHaptics {
    val context = LocalContext.current
    return remember(context) { TitanHaptics(context) }
}
