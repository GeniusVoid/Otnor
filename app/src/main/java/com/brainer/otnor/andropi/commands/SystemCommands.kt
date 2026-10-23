package com.brainer.otnor.andropi.commands

import android.content.Context
import android.os.Build
import com.brainer.otnor.OtnorApp
import com.brainer.otnor.accessibility.ServiceHolder
import com.brainer.otnor.andropi.CommandResult
import com.brainer.otnor.service.BrainerForegroundService
import com.brainer.otnor.util.Constants
import com.brainer.otnor.util.JsonHelper.getStringOrNull
import com.brainer.otnor.util.PermissionManager
import com.google.gson.JsonObject

class SystemCommands(private val context: Context) {

    fun getStatus(): CommandResult {
        val uptimeSeconds = (System.currentTimeMillis() - OtnorApp.startTimeMs) / 1000L
        val backendReachable = BrainerForegroundService.backendReachable.value
        val accessibilityEnabled = PermissionManager.isAccessibilityServiceEnabled(context)

        return CommandResult.ok(
            mapOf(
                "server_running" to true,
                "port" to Constants.ANDROPI_PORT,
                "uptime_seconds" to uptimeSeconds,
                "version" to Constants.APP_VERSION,
                "accessibility_service_enabled" to accessibilityEnabled,
                "foreground_service_running" to ServiceHolder.foregroundServiceRunning,
                "backend_reachable" to backendReachable,
                "backend_url" to Constants.BACKEND_URL
            )
        )
    }

    fun getPermissions(): CommandResult {
        val status = PermissionManager.getPermissionsStatus(context)
        return CommandResult.ok(
            mapOf(
                "granted" to status.granted,
                "denied" to status.denied,
                "special_permissions" to mapOf(
                    "accessibility_service" to status.specialPermissions.accessibilityService,
                    "overlay_permission" to status.specialPermissions.overlayPermission,
                    "manage_external_storage" to status.specialPermissions.manageExternalStorage,
                    "battery_optimization_exempt" to status.specialPermissions.batteryOptimizationExempt,
                    "notification_listener" to status.specialPermissions.notificationListener,
                    "usage_stats" to status.specialPermissions.usageStats,
                    "write_settings" to status.specialPermissions.writeSettings
                )
            )
        )
    }

    fun requestPermission(body: JsonObject): CommandResult {
        val perm = body.getStringOrNull("permission")
            ?: return CommandResult.error("Missing 'permission'")

        val activity = OtnorApp.currentActivity
            ?: return CommandResult.error("Otnor must be in foreground to request runtime permissions")

        val granted = PermissionManager.requestSinglePermissionBlocking(activity, perm)
        return CommandResult.ok(
            mapOf(
                "permission" to perm,
                "granted" to granted
            )
        )
    }

    fun getVersion(): CommandResult {
        return CommandResult.ok(
            mapOf(
                "app_version" to Constants.APP_VERSION,
                "andropi_version" to Constants.ANDROPI_VERSION,
                "android_version" to Build.VERSION.RELEASE,
                "sdk_level" to Build.VERSION.SDK_INT
            )
        )
    }

    fun getAllCommands(): CommandResult {
        val commands = listOf(
            cmd("GET", "/andropi/v1/device/info", "Comprehensive device & hardware information"),
            cmd("GET", "/andropi/v1/device/battery", "Battery level, charging status, temperature, voltage"),
            cmd("GET", "/andropi/v1/device/memory", "Total, available, and used RAM statistics"),
            cmd("GET", "/andropi/v1/device/display", "Screen resolution, density, orientation, refresh rate"),
            cmd("GET", "/andropi/v1/device/storage-stats", "Internal & external storage capacity and free space"),

            cmd("POST", "/andropi/v1/storage/read", "Read file contents (utf-8 or base64)"),
            cmd("POST", "/andropi/v1/storage/write", "Write file contents with optional directory creation"),
            cmd("POST", "/andropi/v1/storage/append", "Append text to file"),
            cmd("POST", "/andropi/v1/storage/list", "List directory entries"),
            cmd("POST", "/andropi/v1/storage/delete", "Delete file or directory"),
            cmd("POST", "/andropi/v1/storage/move", "Move/rename file or directory"),
            cmd("POST", "/andropi/v1/storage/copy", "Copy file or directory"),
            cmd("POST", "/andropi/v1/storage/exists", "Check if path exists"),
            cmd("POST", "/andropi/v1/storage/stat", "Get detailed file metadata"),
            cmd("POST", "/andropi/v1/storage/mkdir", "Create directory"),

            cmd("GET", "/andropi/v1/clipboard/get", "Read system clipboard text"),
            cmd("POST", "/andropi/v1/clipboard/set", "Write text to system clipboard"),

            cmd("POST", "/andropi/v1/notification/show", "Post native Android notification"),
            cmd("POST", "/andropi/v1/notification/cancel", "Cancel notification by ID"),
            cmd("POST", "/andropi/v1/notification/cancel-all", "Cancel all notifications"),
            cmd("GET", "/andropi/v1/notification/active", "List active notifications posted by Otnor"),
            cmd("POST", "/andropi/v1/notification/channel/create", "Create notification channel"),
            cmd("GET", "/andropi/v1/notification/listener/messages", "List intercepted messages across apps"),
            cmd("POST", "/andropi/v1/notification/listener/reply", "Reply directly to an intercepted notification"),

            cmd("POST", "/andropi/v1/media/tts/speak", "Speak text via Android TextToSpeech"),
            cmd("POST", "/andropi/v1/media/tts/stop", "Stop active TTS speech"),
            cmd("GET", "/andropi/v1/media/tts/voices", "List available TTS voices"),
            cmd("POST", "/andropi/v1/media/audio/record/start", "Start microphone audio recording (.m4a)"),
            cmd("POST", "/andropi/v1/media/audio/record/stop", "Stop microphone audio recording"),
            cmd("POST", "/andropi/v1/media/audio/play", "Play audio file"),
            cmd("POST", "/andropi/v1/media/audio/stop", "Stop audio playback"),
            cmd("POST", "/andropi/v1/media/camera/photo", "Capture still photo from front or back camera"),

            cmd("GET", "/andropi/v1/sensors/list", "List all hardware sensors on device"),
            cmd("POST", "/andropi/v1/sensors/read", "Read live sensor values by type"),
            cmd("GET", "/andropi/v1/sensors/location", "Get current GPS/fused device location"),

            cmd("POST", "/andropi/v1/screen/brightness", "Set screen brightness (0-255)"),
            cmd("POST", "/andropi/v1/screen/orientation", "Set screen orientation (portrait, landscape, auto)"),
            cmd("POST", "/andropi/v1/screen/keep-on", "Toggle keep screen awake flag"),
            cmd("POST", "/andropi/v1/screen/toast", "Show native Android Toast"),
            cmd("POST", "/andropi/v1/screen/vibrate", "Vibrate device with duration or pattern"),

            cmd("POST", "/andropi/v1/dialog/alert", "Show blocking native Alert dialog"),
            cmd("POST", "/andropi/v1/dialog/confirm", "Show blocking native Confirm dialog"),
            cmd("POST", "/andropi/v1/dialog/input", "Show blocking native Text Input dialog"),
            cmd("POST", "/andropi/v1/dialog/picker/date", "Show blocking native Date Picker dialog"),
            cmd("POST", "/andropi/v1/dialog/picker/time", "Show blocking native Time Picker dialog"),

            cmd("GET", "/andropi/v1/apps/list", "List all installed packages"),
            cmd("POST", "/andropi/v1/apps/launch", "Launch installed app by package name"),
            cmd("POST", "/andropi/v1/apps/intent", "Send arbitrary Android Intent"),
            cmd("GET", "/andropi/v1/apps/running", "List running processes and recent foreground apps"),

            cmd("GET", "/andropi/v1/network/wifi/info", "Get current WiFi connection details"),
            cmd("GET", "/andropi/v1/network/wifi/scan", "Scan nearby WiFi access points"),
            cmd("GET", "/andropi/v1/network/bluetooth/scan", "List bonded/nearby Bluetooth devices"),
            cmd("POST", "/andropi/v1/network/bluetooth/connect", "Pair/bond with Bluetooth device by MAC"),

            cmd("POST", "/andropi/v1/biometric/authenticate", "Show blocking Biometric authentication prompt"),
            cmd("GET", "/andropi/v1/biometric/available", "Check biometric hardware availability"),

            cmd("GET", "/andropi/v1/accessibility/status", "Get BrainerAccessibilityService status"),
            cmd("POST", "/andropi/v1/accessibility/capture-text", "Capture all visible text nodes from active screen"),
            cmd("POST", "/andropi/v1/accessibility/capture-app", "Wait for and capture text from specific package"),
            cmd("POST", "/andropi/v1/accessibility/find-text", "Find UI nodes matching query text"),
            cmd("POST", "/andropi/v1/accessibility/click-node", "Click node by text or view resource ID"),
            cmd("POST", "/andropi/v1/accessibility/perform-action", "Perform AccessibilityNodeInfo action"),
            cmd("POST", "/andropi/v1/accessibility/global-action", "Perform global action (BACK, HOME, RECENTS, etc.)"),
            cmd("POST", "/andropi/v1/accessibility/dispatch-gesture", "Dispatch tap, long_press, or swipe at X,Y coordinates"),
            cmd("POST", "/andropi/v1/accessibility/scroll", "Scroll active window forward/backward/top/bottom"),
            cmd("POST", "/andropi/v1/accessibility/key-event/register", "Register hardware volume/key trigger"),
            cmd("POST", "/andropi/v1/accessibility/gesture/register", "Register fingerprint swipe gesture trigger"),
            cmd("POST", "/andropi/v1/accessibility/gesture/unregister", "Unregister fingerprint swipe gesture"),
            cmd("GET", "/andropi/v1/accessibility/gesture/list", "List registered fingerprint and key triggers"),

            cmd("POST", "/andropi/v1/overlay/freeze", "Freeze screen screenshot overlay for word selection"),
            cmd("POST", "/andropi/v1/overlay/unfreeze", "Unfreeze screen and return selected words/nodes"),
            cmd("POST", "/andropi/v1/overlay/popup", "Show floating quick-actions or WebView overlay"),
            cmd("POST", "/andropi/v1/overlay/dismiss", "Dismiss active floating popup overlay"),
            cmd("GET", "/andropi/v1/overlay/status", "Get freeze and popup overlay status"),

            cmd("GET", "/andropi/v1/system/status", "Andropi server and Brainer health check"),
            cmd("GET", "/andropi/v1/system/permissions", "Status of all runtime and special permissions"),
            cmd("POST", "/andropi/v1/system/permission/request", "Request runtime permission interactively"),
            cmd("GET", "/andropi/v1/system/commands", "List all Andropi REST API endpoints"),
            cmd("GET", "/andropi/v1/system/version", "Get Otnor and Andropi version info")
        )
        return CommandResult.ok(mapOf("commands" to commands, "total" to commands.size))
    }

    private fun cmd(method: String, path: String, description: String): Map<String, String> =
        mapOf("method" to method, "path" to path, "description" to description)
}
