package com.brainer.otnor.andropi.commands

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import com.brainer.otnor.OtnorApp
import com.brainer.otnor.andropi.CommandResult
import com.brainer.otnor.util.JsonHelper.getBooleanOrDefault
import com.brainer.otnor.util.JsonHelper.getIntOrDefault
import com.brainer.otnor.util.JsonHelper.getLongOrDefault
import com.brainer.otnor.util.JsonHelper.getStringOrDefault
import com.brainer.otnor.util.JsonHelper.getStringOrNull
import com.google.gson.JsonObject

class ScreenCommands(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    fun setBrightness(body: JsonObject): CommandResult {
        val value = body.getIntOrDefault("value", 128).coerceIn(0, 255)
        var systemUpdated = false

        if (Settings.System.canWrite(context)) {
            try {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    value
                )
                systemUpdated = true
            } catch (_: Exception) {
            }
        }

        mainHandler.post {
            OtnorApp.currentActivity?.window?.let { win ->
                val params = win.attributes
                params.screenBrightness = value / 255.0f
                win.attributes = params
            }
        }

        return CommandResult.ok(
            mapOf(
                "brightness" to value,
                "system_setting_updated" to systemUpdated,
                "window_updated" to true
            )
        )
    }

    fun setOrientation(body: JsonObject): CommandResult {
        val orientationStr = body.getStringOrDefault("orientation", "auto").lowercase()
        val activity = OtnorApp.currentActivity
            ?: return CommandResult.error("No foreground Otnor Activity available")

        val flag = when (orientationStr) {
            "portrait" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            "landscape" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        mainHandler.post {
            activity.requestedOrientation = flag
        }

        return CommandResult.ok(mapOf("orientation" to orientationStr, "applied" to true))
    }

    fun setKeepOn(body: JsonObject): CommandResult {
        val enabled = body.getBooleanOrDefault("enabled", true)
        val activity = OtnorApp.currentActivity
            ?: return CommandResult.error("No foreground Otnor Activity available")

        mainHandler.post {
            if (enabled) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        return CommandResult.ok(mapOf("keep_screen_on" to enabled))
    }

    fun showToast(body: JsonObject): CommandResult {
        val message = body.getStringOrNull("message") ?: return CommandResult.error("Missing 'message'")
        val durationStr = body.getStringOrDefault("duration", "short").lowercase()
        val length = if (durationStr == "long") Toast.LENGTH_LONG else Toast.LENGTH_SHORT

        mainHandler.post {
            Toast.makeText(context, message, length).show()
        }
        return CommandResult.ok(mapOf("shown" to true, "message" to message, "duration" to durationStr))
    }

    fun vibrate(body: JsonObject): CommandResult {
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (body.has("pattern") && body.get("pattern").isJsonArray) {
            val arr = body.getAsJsonArray("pattern")
            val pattern = LongArray(arr.size()) { i -> arr[i].asLong }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
            return CommandResult.ok(mapOf("vibrated" to true, "pattern" to pattern.toList()))
        } else {
            val durationMs = body.getLongOrDefault("duration_ms", 200L).coerceIn(10L, 10_000L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
            return CommandResult.ok(mapOf("vibrated" to true, "duration_ms" to durationMs))
        }
    }
}
