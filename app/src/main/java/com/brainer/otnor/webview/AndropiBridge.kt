package com.brainer.otnor.webview

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.brainer.otnor.OtnorApp
import com.brainer.otnor.util.Constants
import com.brainer.otnor.util.JsonHelper
import com.brainer.otnor.util.PermissionManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class AndropiBridge(
    private val context: Context,
    private val onReloadRequested: () -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun vibrate(milliseconds: Long) {
        val duration = milliseconds.coerceIn(10L, 10_000L)
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }

    @JavascriptInterface
    fun getClipboard(): String {
        var result = ""
        val latch = CountDownLatch(1)
        mainHandler.post {
            try {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = cm.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    result = clip.getItemAt(0).coerceToText(context)?.toString() ?: ""
                }
            } finally {
                latch.countDown()
            }
        }
        latch.await(2, TimeUnit.SECONDS)
        return result
    }

    @JavascriptInterface
    fun setClipboard(text: String) {
        mainHandler.post {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Otnor", text))
        }
    }

    @JavascriptInterface
    fun getDeviceInfo(): String {
        return JsonHelper.toJson(
            mapOf(
                "model" to Build.MODEL,
                "manufacturer" to Build.MANUFACTURER,
                "brand" to Build.BRAND,
                "device" to Build.DEVICE,
                "android_version" to Build.VERSION.RELEASE,
                "sdk" to Build.VERSION.SDK_INT
            )
        )
    }

    @JavascriptInterface
    fun getBatteryLevel(): Int {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100f / scale).roundToInt() else 0
    }

    @JavascriptInterface
    fun getBatteryCharging(): Boolean {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }

    @JavascriptInterface
    fun getNetworkType(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return "none"
        val caps = cm.getNetworkCapabilities(net) ?: return "none"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "none"
        }
    }

    @SuppressLint("MissingPermission")
    @JavascriptInterface
    fun getWifiSSID(): String {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wm.connectionInfo?.ssid?.trim('"') ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    @JavascriptInterface
    fun isAccessibilityEnabled(): Boolean {
        return PermissionManager.isAccessibilityServiceEnabled(context)
    }

    @JavascriptInterface
    fun isAndropiRunning(): Boolean {
        return OtnorApp.andropiServer?.isAlive == true
    }

    @JavascriptInterface
    fun getAndropiPort(): Int {
        return Constants.ANDROPI_PORT
    }

    @JavascriptInterface
    fun openAccessibilitySettings() {
        PermissionManager.openAccessibilitySettings(context)
    }

    @JavascriptInterface
    fun openAppSettings() {
        PermissionManager.openAppDetailsSettings(context)
    }

    @JavascriptInterface
    fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(Constants.TAG_OTNOR, "Failed opening URL $url: ${e.message}")
        }
    }

    @JavascriptInterface
    fun shareText(text: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(sendIntent, "Share via Otnor").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    @JavascriptInterface
    fun log(tag: String, message: String) {
        Log.d(tag.ifBlank { Constants.TAG_OTNOR }, message)
    }

    @JavascriptInterface
    fun getScreenBrightness(): Int {
        return try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (e: Exception) {
            128
        }
    }

    @JavascriptInterface
    fun setScreenBrightness(value: Int) {
        val clamped = value.coerceIn(0, 255)
        if (Settings.System.canWrite(context)) {
            try {
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, clamped)
            } catch (_: Exception) {
            }
        }
        mainHandler.post {
            OtnorApp.currentActivity?.window?.let { win ->
                val attr = win.attributes
                attr.screenBrightness = clamped / 255f
                win.attributes = attr
            }
        }
    }

    @JavascriptInterface
    fun keepScreenOn(enabled: Boolean) {
        mainHandler.post {
            OtnorApp.currentActivity?.window?.let { win ->
                if (enabled) {
                    win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    win.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
    }

    @JavascriptInterface
    fun reloadWebView() {
        mainHandler.post {
            onReloadRequested()
        }
    }
}
