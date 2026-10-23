package com.brainer.otnor.andropi.commands

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.brainer.otnor.andropi.CommandResult
import com.brainer.otnor.util.JsonHelper.getStringOrDefault
import com.brainer.otnor.util.JsonHelper.getStringOrNull
import com.google.gson.JsonObject
import java.time.Instant

class AppCommands(private val context: Context) {

    fun listApps(): CommandResult {
        val pm = context.packageManager
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(0)
        }

        val apps = packages.map { pkg ->
            val appInfo = pkg.applicationInfo
            val appName = if (appInfo != null) {
                pm.getApplicationLabel(appInfo).toString()
            } else {
                pkg.packageName
            }
            val isSystem = appInfo != null && (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            mapOf(
                "package_name" to pkg.packageName,
                "app_name" to appName,
                "version_name" to (pkg.versionName ?: "1.0"),
                "install_date" to Instant.ofEpochMilli(pkg.firstInstallTime).toString(),
                "is_system_app" to isSystem
            )
        }

        return CommandResult.ok(mapOf("apps" to apps, "count" to apps.size))
    }

    fun launchApp(body: JsonObject): CommandResult {
        val pkgName = body.getStringOrNull("package")
            ?: body.getStringOrNull("package_name")
            ?: return CommandResult.error("Missing 'package'")

        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkgName)
            ?: return CommandResult.error("App not found or has no launchable activity: $pkgName")

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return CommandResult.ok(mapOf("launched" to true, "package" to pkgName))
    }

    fun sendIntent(body: JsonObject): CommandResult {
        val actionStr = body.getStringOrDefault("action", Intent.ACTION_VIEW)
        val dataUri = body.getStringOrNull("data")
        val mimeType = body.getStringOrNull("type")
        val targetPkg = body.getStringOrNull("package")

        val intent = Intent(actionStr).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (!dataUri.isNullOrBlank() && !mimeType.isNullOrBlank()) {
                setDataAndType(Uri.parse(dataUri), mimeType)
            } else if (!dataUri.isNullOrBlank()) {
                data = Uri.parse(dataUri)
            } else if (!mimeType.isNullOrBlank()) {
                type = mimeType
            }
            if (!targetPkg.isNullOrBlank()) {
                setPackage(targetPkg)
            }
            if (body.has("extras") && body.get("extras").isJsonObject) {
                val extrasObj = body.getAsJsonObject("extras")
                for ((k, v) in extrasObj.entrySet()) {
                    if (v.isJsonPrimitive) {
                        val prim = v.asJsonPrimitive
                        when {
                            prim.isBoolean -> putExtra(k, prim.asBoolean)
                            prim.isNumber -> putExtra(k, prim.asString)
                            else -> putExtra(k, prim.asString)
                        }
                    }
                }
            }
        }

        context.startActivity(intent)
        return CommandResult.ok(mapOf("sent" to true, "action" to actionStr, "data" to dataUri))
    }

    fun getRunningApps(): CommandResult {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val processes = am.runningAppProcesses?.map { proc ->
            mapOf(
                "process_name" to proc.processName,
                "pid" to proc.pid,
                "uid" to proc.uid,
                "importance" to proc.importance
            )
        }?.toMutableList() ?: mutableListOf()

        // Also include recent foreground usage stats if granted
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60_000L * 15, now)
            val recentPackages = stats
                ?.filter { it.lastTimeUsed > now - 60_000L * 15 }
                ?.sortedByDescending { it.lastTimeUsed }
                ?.take(20)
                ?.map {
                    mapOf(
                        "package_name" to it.packageName,
                        "last_time_used" to Instant.ofEpochMilli(it.lastTimeUsed).toString(),
                        "total_time_in_foreground_ms" to it.totalTimeInForeground
                    )
                } ?: emptyList()

            return CommandResult.ok(
                mapOf(
                    "running_processes" to processes,
                    "recent_foreground_apps" to recentPackages
                )
            )
        } catch (_: Exception) {
            return CommandResult.ok(mapOf("running_processes" to processes))
        }
    }
}
