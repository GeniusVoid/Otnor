package com.brainer.otnor.andropi.commands

import android.content.Context
import android.os.Build
import com.brainer.otnor.accessibility.CaptureBuffer
import com.brainer.otnor.accessibility.GestureActionRegistry
import com.brainer.otnor.accessibility.ScreenTextCapture
import com.brainer.otnor.accessibility.ServiceHolder
import com.brainer.otnor.andropi.CommandResult
import com.brainer.otnor.util.JsonHelper.getBooleanOrDefault
import com.brainer.otnor.util.JsonHelper.getFloatOrDefault
import com.brainer.otnor.util.JsonHelper.getLongOrDefault
import com.brainer.otnor.util.JsonHelper.getStringOrDefault
import com.brainer.otnor.util.JsonHelper.getStringOrNull
import com.brainer.otnor.util.PermissionManager
import com.google.gson.JsonObject

class AccessibilityCommands(private val context: Context) {

    fun getStatus(): CommandResult {
        val svc = ServiceHolder.accessibilityService
        val enabled = PermissionManager.isAccessibilityServiceEnabled(context)
        val connected = svc != null
        val fpAvailable = svc?.isFingerprintGesturesAvailable() == true

        return CommandResult.ok(
            mapOf(
                "service_enabled" to enabled,
                "is_connected" to connected,
                "fingerprint_gestures_available" to fpAvailable,
                "can_retrieve_window_content" to connected,
                "can_take_screenshot" to (connected && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R),
                "can_perform_gestures" to connected
            )
        )
    }

    fun captureText(): CommandResult {
        if (ServiceHolder.accessibilityService == null) {
            return CommandResult.error("Accessibility service is not connected. Enable Brainer Accessibility Service in Android Settings.")
        }
        val snapshot = CaptureBuffer.requestCapture(targetPkg = null, timeoutSeconds = 5)
        return CommandResult.ok(snapshot)
    }

    fun captureApp(body: JsonObject): CommandResult {
        if (ServiceHolder.accessibilityService == null) {
            return CommandResult.error("Accessibility service is not connected.")
        }
        val pkg = body.getStringOrNull("package")
            ?: return CommandResult.error("Missing 'package'")
        val timeoutSec = body.getLongOrDefault("timeout_seconds", 10L).coerceIn(1L, 30L)
        val snapshot = CaptureBuffer.requestCapture(targetPkg = pkg, timeoutSeconds = timeoutSec)
        return CommandResult.ok(snapshot)
    }

    fun findText(body: JsonObject): CommandResult {
        val svc = ServiceHolder.accessibilityService
            ?: return CommandResult.error("Accessibility service is not connected.")
        val query = body.getStringOrNull("query")
            ?: return CommandResult.error("Missing 'query'")
        val exactMatch = body.getBooleanOrDefault("exact_match", false)

        val matches = ScreenTextCapture.findNodesByText(svc, query, exactMatch)
        return CommandResult.ok(mapOf("matches" to matches, "count" to matches.size))
    }

    fun clickNode(body: JsonObject): CommandResult {
        val svc = ServiceHolder.accessibilityService
            ?: return CommandResult.error("Accessibility service is not connected.")
        val text = body.getStringOrNull("text")
        val resourceId = body.getStringOrNull("resource_id")
        if (text.isNullOrBlank() && resourceId.isNullOrBlank()) {
            return CommandResult.error("Provide 'text' or 'resource_id'")
        }

        val clicked = ScreenTextCapture.clickNode(svc, text, resourceId)
        return CommandResult.ok(mapOf("clicked" to clicked))
    }

    fun performAction(body: JsonObject): CommandResult {
        val svc = ServiceHolder.accessibilityService
            ?: return CommandResult.error("Accessibility service is not connected.")
        val text = body.getStringOrNull("text")
        val resourceId = body.getStringOrNull("resource_id")
        val action = body.getStringOrDefault("action", "ACTION_CLICK")

        val argsMap = mutableMapOf<String, String>()
        if (body.has("arguments") && body.get("arguments").isJsonObject) {
            val argsObj = body.getAsJsonObject("arguments")
            for ((k, v) in argsObj.entrySet()) {
                if (!v.isJsonNull) argsMap[k] = v.asString
            }
        }

        val ok = ScreenTextCapture.performNodeAction(svc, text, resourceId, action, argsMap)
        return CommandResult.ok(mapOf("performed" to ok, "action" to action))
    }

    fun globalAction(body: JsonObject): CommandResult {
        val svc = ServiceHolder.accessibilityService
            ?: return CommandResult.error("Accessibility service is not connected.")
        val action = body.getStringOrNull("action")
            ?: return CommandResult.error("Missing 'action'")

        val ok = svc.performGlobalActionByName(action)
        return CommandResult.ok(mapOf("performed" to ok, "action" to action))
    }

    fun dispatchGesture(body: JsonObject): CommandResult {
        val svc = ServiceHolder.accessibilityService
            ?: return CommandResult.error("Accessibility service is not connected.")

        val type = body.getStringOrDefault("type", "click")
        val x = body.getFloatOrDefault("x", body.getFloatOrDefault("start_x", 540f))
        val y = body.getFloatOrDefault("y", body.getFloatOrDefault("start_y", 1200f))
        val endX = body.getFloatOrDefault("end_x", x)
        val endY = body.getFloatOrDefault("end_y", y)
        val durationMs = body.getLongOrDefault("duration_ms", 150L)

        val ok = svc.dispatchCoordinateGesture(type, x, y, endX, endY, durationMs)
        return CommandResult.ok(
            mapOf(
                "dispatched" to ok,
                "type" to type,
                "x" to x,
                "y" to y,
                "end_x" to endX,
                "end_y" to endY,
                "duration_ms" to durationMs
            )
        )
    }

    fun scroll(body: JsonObject): CommandResult {
        val svc = ServiceHolder.accessibilityService
            ?: return CommandResult.error("Accessibility service is not connected.")
        val direction = body.getStringOrDefault("direction", "forward")
        val ok = ScreenTextCapture.performScroll(svc, direction)
        return CommandResult.ok(mapOf("scrolled" to ok, "direction" to direction))
    }

    fun registerKeyEvent(body: JsonObject): CommandResult {
        val key = body.getStringOrNull("key") ?: return CommandResult.error("Missing 'key'")
        val action = body.getStringOrNull("action") ?: return CommandResult.error("Missing 'action'")
        GestureActionRegistry.registerKeyEvent(key, action)
        return CommandResult.ok(mapOf("registered" to true, "key" to key, "action" to action))
    }

    fun registerGesture(body: JsonObject): CommandResult {
        val gesture = body.getStringOrNull("gesture") ?: return CommandResult.error("Missing 'gesture'")
        val action = body.getStringOrNull("action") ?: return CommandResult.error("Missing 'action'")
        val data = body.getStringOrNull("data") ?: body.getStringOrNull("url") ?: body.getStringOrNull("package")
        GestureActionRegistry.register(gesture, action, data)
        return CommandResult.ok(
            mapOf(
                "registered" to true,
                "gesture" to gesture,
                "action" to action,
                "data" to data
            )
        )
    }

    fun unregisterGesture(body: JsonObject): CommandResult {
        val gesture = body.getStringOrNull("gesture") ?: return CommandResult.error("Missing 'gesture'")
        GestureActionRegistry.unregister(gesture)
        return CommandResult.ok(mapOf("unregistered" to true, "gesture" to gesture))
    }

    fun listGestures(): CommandResult {
        return CommandResult.ok(
            mapOf(
                "gestures" to GestureActionRegistry.listGestures(),
                "key_events" to GestureActionRegistry.listKeyEvents()
            )
        )
    }
}
