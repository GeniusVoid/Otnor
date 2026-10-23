package com.brainer.otnor.andropi.commands

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.view.WindowManager
import com.brainer.otnor.andropi.CommandResult
import kotlin.math.roundToInt

class DeviceCommands(private val context: Context) {

    fun getInfo(): CommandResult {
        val data = mapOf(
            "model" to Build.MODEL,
            "manufacturer" to Build.MANUFACTURER,
            "brand" to Build.BRAND,
            "device" to Build.DEVICE,
            "product" to Build.PRODUCT,
            "android_version" to Build.VERSION.RELEASE,
            "sdk_level" to Build.VERSION.SDK_INT,
            "build_id" to Build.ID,
            "board" to Build.BOARD,
            "hardware" to Build.HARDWARE,
            "display_id" to Build.DISPLAY,
            "fingerprint" to Build.FINGERPRINT,
            "serial" to "unknown",
            "bootloader" to Build.BOOTLOADER,
            "host" to Build.HOST,
            "uptime_ms" to SystemClock.elapsedRealtime()
        )
        return CommandResult.ok(data)
    }

    fun getBattery(): CommandResult {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) (level * 100f / scale).roundToInt() else 0

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val chargeType = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_USB -> "usb"
            BatteryManager.BATTERY_PLUGGED_AC -> "ac"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
            else -> "none"
        }

        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val healthInt = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val health = when (healthInt) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over_voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "cold"
            else -> "unknown"
        }

        return CommandResult.ok(
            mapOf(
                "level" to pct,
                "is_charging" to isCharging,
                "charge_type" to chargeType,
                "temperature" to (tempTenths / 10.0),
                "voltage" to voltage,
                "health" to health
            )
        )
    }

    fun getMemory(): CommandResult {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalMb = memInfo.totalMem / (1024 * 1024)
        val availMb = memInfo.availMem / (1024 * 1024)
        val usedMb = totalMb - availMb
        return CommandResult.ok(
            mapOf(
                "total_ram_mb" to totalMb,
                "available_ram_mb" to availMb,
                "used_ram_mb" to usedMb,
                "low_memory" to memInfo.lowMemory
            )
        )
    }

    fun getDisplay(): CommandResult {
        val dm = context.resources.displayMetrics
        val orientation = if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            "landscape"
        } else {
            "portrait"
        }
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        val refreshRate = wm.defaultDisplay?.refreshRate ?: 60.0f

        return CommandResult.ok(
            mapOf(
                "width_px" to dm.widthPixels,
                "height_px" to dm.heightPixels,
                "density_dpi" to dm.densityDpi,
                "density" to dm.density,
                "orientation" to orientation,
                "refresh_rate" to refreshRate
            )
        )
    }

    fun getStorageStats(): CommandResult {
        val internalStat = StatFs(Environment.getDataDirectory().path)
        val intTotalGb = roundGb(internalStat.totalBytes)
        val intFreeGb = roundGb(internalStat.availableBytes)
        val intUsedGb = roundDouble(intTotalGb - intFreeGb)

        val extState = Environment.getExternalStorageState()
        val extAvailable = extState == Environment.MEDIA_MOUNTED || extState == Environment.MEDIA_MOUNTED_READ_ONLY
        val extMap = if (extAvailable) {
            val extStat = StatFs(Environment.getExternalStorageDirectory().path)
            val extTotal = roundGb(extStat.totalBytes)
            val extFree = roundGb(extStat.availableBytes)
            mapOf(
                "available" to true,
                "total_gb" to extTotal,
                "free_gb" to extFree,
                "used_gb" to roundDouble(extTotal - extFree)
            )
        } else {
            mapOf(
                "available" to false,
                "total_gb" to 0.0,
                "free_gb" to 0.0,
                "used_gb" to 0.0
            )
        }

        return CommandResult.ok(
            mapOf(
                "internal" to mapOf(
                    "total_gb" to intTotalGb,
                    "free_gb" to intFreeGb,
                    "used_gb" to intUsedGb
                ),
                "external" to extMap
            )
        )
    }

    private fun roundGb(bytes: Long): Double {
        val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        return roundDouble(gb)
    }

    private fun roundDouble(v: Double): Double = (v * 10.0).roundToInt() / 10.0
}
