package com.brainer.otnor.accessibility

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.util.Log
import com.brainer.otnor.util.Constants
import com.brainer.otnor.util.JsonHelper
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

data class BoundsRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    fun contains(x: Int, y: Int): Boolean = x in left..right && y in top..bottom
    fun centerX(): Int = (left + right) / 2
    fun centerY(): Int = (top + bottom) / 2

    companion object {
        fun fromRect(rect: Rect): BoundsRect = BoundsRect(rect.left, rect.top, rect.right, rect.bottom)
    }
}

data class CapturedNode(
    val text: String,
    val view_id: String?,
    val `class`: String?,
    val bounds: BoundsRect,
    val clickable: Boolean = false,
    val scrollable: Boolean = false,
    val editable: Boolean = false,
    val content_description: String? = null
)

data class CaptureSnapshot(
    val package_name: String,
    val activity_name: String,
    val texts: List<CapturedNode>,
    val capture_time: String
)

object ServiceHolder {
    @Volatile
    var accessibilityService: BrainerAccessibilityService? = null

    @Volatile
    var foregroundServiceRunning: Boolean = false
}

object CaptureBuffer {
    @Volatile
    var captureRequested = false

    @Volatile
    var targetPackage: String? = null

    @Volatile
    var lastPackageName: String = ""

    @Volatile
    var lastActivityName: String = ""

    @Volatile
    var lastQuickActionId: String? = null

    private val results = mutableListOf<CapturedNode>()
    private val lock = Object()
    private var resultReady = CountDownLatch(1)

    fun requestCapture(targetPkg: String? = null, timeoutSeconds: Long = 10): CaptureSnapshot {
        val service = ServiceHolder.accessibilityService
        // If no target package filter is required (or already on that package), capture immediately from active window
        if (service != null && (targetPkg == null || targetPkg == lastPackageName)) {
            val immediate = ScreenTextCapture.captureCurrentWindow(service)
            if (immediate != null && (targetPkg == null || immediate.package_name == targetPkg)) {
                synchronized(lock) {
                    results.clear()
                    results.addAll(immediate.texts)
                    lastPackageName = immediate.package_name
                    lastActivityName = immediate.activity_name
                }
                return immediate
            }
        }

        synchronized(lock) {
            results.clear()
            targetPackage = targetPkg
            resultReady = CountDownLatch(1)
            captureRequested = true
        }

        resultReady.await(timeoutSeconds, TimeUnit.SECONDS)

        return synchronized(lock) {
            CaptureSnapshot(
                package_name = lastPackageName.ifEmpty { targetPkg ?: "unknown" },
                activity_name = lastActivityName,
                texts = results.toList(),
                capture_time = Instant.now().toString()
            )
        }
    }

    fun storeResults(pkg: String?, activity: String?, capturedNodes: List<CapturedNode>) {
        synchronized(lock) {
            if (!pkg.isNullOrEmpty()) lastPackageName = pkg
            if (!activity.isNullOrEmpty()) lastActivityName = activity
            results.clear()
            results.addAll(capturedNodes)
            captureRequested = false
            resultReady.countDown()
        }
    }

    fun recordQuickAction(actionId: String) {
        lastQuickActionId = actionId
    }
}

data class GestureAction(
    val gesture: String,
    val action: String,
    val data: String? = null
)

object GestureActionRegistry {
    private val actions = ConcurrentHashMap<String, GestureAction>()
    private val keyActions = ConcurrentHashMap<String, String>()
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val savedJson = prefs.getString(Constants.PREF_GESTURE_MAPPINGS, null)
        if (!savedJson.isNullOrBlank()) {
            val map = JsonHelper.fromJson<Map<String, GestureAction>>(savedJson)
            if (map != null) {
                actions.putAll(map)
            }
        } else {
            // Default helpful gesture registrations
            actions["swipe_up"] = GestureAction("swipe_up", "open_capture_popup")
            actions["swipe_down"] = GestureAction("swipe_down", "capture_screen_text")
        }

        val savedKeyJson = prefs.getString(Constants.PREF_KEY_EVENT_MAPPINGS, null)
        if (!savedKeyJson.isNullOrBlank()) {
            val map = JsonHelper.fromJson<Map<String, String>>(savedKeyJson)
            if (map != null) {
                keyActions.putAll(map)
            }
        }
    }

    private fun saveToPrefs() {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(Constants.PREF_GESTURE_MAPPINGS, JsonHelper.toJson(actions))
            .putString(Constants.PREF_KEY_EVENT_MAPPINGS, JsonHelper.toJson(keyActions))
            .apply()
    }

    fun register(gesture: String, action: String, data: String? = null) {
        actions[gesture] = GestureAction(gesture, action, data)
        saveToPrefs()
    }

    fun unregister(gesture: String) {
        actions.remove(gesture)
        saveToPrefs()
    }

    fun listGestures(): Map<String, GestureAction> = actions.toMap()

    fun registerKeyEvent(key: String, action: String) {
        keyActions[key.uppercase()] = action
        saveToPrefs()
    }

    fun getKeyAction(key: String): String? = keyActions[key.uppercase()]

    fun listKeyEvents(): Map<String, String> = keyActions.toMap()

    fun executeAction(gesture: String, service: BrainerAccessibilityService) {
        val gestureAction = actions[gesture] ?: return
        executeNamedAction(gestureAction.action, gestureAction.data, service)
    }

    fun executeNamedAction(actionType: String, actionData: String?, service: BrainerAccessibilityService) {
        Log.i(Constants.TAG_OTNOR, "Executing accessibility trigger action: $actionType")
        when (actionType) {
            "open_capture_popup", "quick_capture" -> {
                ScreenFreezeOverlay.showQuickActionsPopup(service)
            }
            "open_app" -> {
                val pkg = actionData ?: service.packageName
                val launchIntent = service.packageManager.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    service.startActivity(launchIntent)
                }
            }
            "capture_screen_text" -> {
                thread {
                    CaptureBuffer.requestCapture()
                }
            }
            "freeze_screen" -> {
                ScreenFreezeOverlay.freezeCurrentScreen(service)
            }
            "custom_url" -> {
                if (!actionData.isNullOrBlank()) {
                    thread {
                        try {
                            val conn = URL(actionData).openConnection() as HttpURLConnection
                            conn.connectTimeout = 5000
                            conn.readTimeout = 5000
                            conn.requestMethod = "GET"
                            conn.responseCode
                            conn.disconnect()
                        } catch (e: Exception) {
                            Log.e(Constants.TAG_OTNOR, "Failed calling custom_url $actionData", e)
                        }
                    }
                }
            }
        }
    }
}
