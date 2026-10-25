package com.brainer.otnor.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.brainer.otnor.util.Constants
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BrainerAccessibilityService : AccessibilityService() {

    private var fingerprintHandler: FingerprintGestureHandler? = null

    @Volatile
    var currentPackageName: String = ""
        private set

    @Volatile
    var currentActivityName: String = ""
        private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Explicitly strip any touch-hijacking flags so normal single-finger touch is never altered
        try {
            serviceInfo = serviceInfo?.apply {
                flags = flags and AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE.inv()
                flags = flags and AccessibilityServiceInfo.FLAG_REQUEST_ACCESSIBILITY_BUTTON.inv()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    flags = flags and AccessibilityServiceInfo.FLAG_SEND_MOTION_EVENTS.inv()
                }
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            }
        } catch (e: Exception) {
            Log.w(Constants.TAG_OTNOR, "Failed sanitizing AccessibilityServiceInfo flags: ${e.message}")
        }
        ServiceHolder.accessibilityService = this
        fingerprintHandler = FingerprintGestureHandler(this).also { it.setup() }
        Log.i(Constants.TAG_OTNOR, "BrainerAccessibilityService connected (Safe Touch Mode)")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString()
        val cls = event.className?.toString()
        if (!pkg.isNullOrEmpty()) {
            currentPackageName = pkg
            CaptureBuffer.lastPackageName = pkg
        }
        if (!cls.isNullOrEmpty() && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            currentActivityName = cls
            CaptureBuffer.lastActivityName = cls
        }

        if (CaptureBuffer.captureRequested) {
            val targetPkg = CaptureBuffer.targetPackage
            if (targetPkg == null || targetPkg == pkg) {
                val snapshot = ScreenTextCapture.captureCurrentWindow(this)
                if (snapshot != null) {
                    CaptureBuffer.storeResults(snapshot.package_name, snapshot.activity_name, snapshot.texts)
                }
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event != null && event.action == KeyEvent.ACTION_DOWN) {
            // Unconditionally pass Volume Down to system to decrease volume
            if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                return super.onKeyEvent(event)
            }
            // Trigger quick capture popup directly on Volume Up
            if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                ScreenFreezeOverlay.showWebViewPopup(
                    this,
                    "http://127.0.0.1:8080/popup.html",
                    "center",
                    350,
                    520
                )
                return true
            }
            val keyName = KeyEvent.keyCodeToString(event.keyCode).removePrefix("KEYCODE_")
            val action = GestureActionRegistry.getKeyAction(keyName)
            if (!action.isNullOrBlank() && action.lowercase() != "none") {
                GestureActionRegistry.executeNamedAction(action, null, this)
                return true
            }
        }
        return super.onKeyEvent(event)
    }

    fun isFingerprintGesturesAvailable(): Boolean {
        return fingerprintHandler?.isAvailable == true
    }

    fun performGlobalActionByName(actionName: String): Boolean {
        val actionId = when (actionName.uppercase()) {
            "GLOBAL_ACTION_BACK", "BACK" -> GLOBAL_ACTION_BACK
            "GLOBAL_ACTION_HOME", "HOME" -> GLOBAL_ACTION_HOME
            "GLOBAL_ACTION_RECENTS", "RECENTS" -> GLOBAL_ACTION_RECENTS
            "GLOBAL_ACTION_NOTIFICATIONS", "NOTIFICATIONS" -> GLOBAL_ACTION_NOTIFICATIONS
            "GLOBAL_ACTION_QUICK_SETTINGS", "QUICK_SETTINGS" -> GLOBAL_ACTION_QUICK_SETTINGS
            "GLOBAL_ACTION_POWER_DIALOG", "POWER_DIALOG" -> GLOBAL_ACTION_POWER_DIALOG
            "GLOBAL_ACTION_LOCK_SCREEN", "LOCK_SCREEN" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) GLOBAL_ACTION_LOCK_SCREEN else GLOBAL_ACTION_BACK
            }
            "GLOBAL_ACTION_TAKE_SCREENSHOT", "TAKE_SCREENSHOT" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) GLOBAL_ACTION_TAKE_SCREENSHOT else GLOBAL_ACTION_BACK
            }
            "GLOBAL_ACTION_DPAD_UP", "DPAD_UP" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) GLOBAL_ACTION_DPAD_UP else GLOBAL_ACTION_BACK
            }
            "GLOBAL_ACTION_DPAD_DOWN", "DPAD_DOWN" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) GLOBAL_ACTION_DPAD_DOWN else GLOBAL_ACTION_BACK
            }
            "GLOBAL_ACTION_DPAD_LEFT", "DPAD_LEFT" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) GLOBAL_ACTION_DPAD_LEFT else GLOBAL_ACTION_BACK
            }
            "GLOBAL_ACTION_DPAD_RIGHT", "DPAD_RIGHT" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) GLOBAL_ACTION_DPAD_RIGHT else GLOBAL_ACTION_BACK
            }
            "GLOBAL_ACTION_DPAD_CENTER", "DPAD_CENTER" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) GLOBAL_ACTION_DPAD_CENTER else GLOBAL_ACTION_BACK
            }
            else -> return false
        }
        return performGlobalAction(actionId)
    }

    fun dispatchCoordinateGesture(
        type: String,
        x: Float,
        y: Float,
        endX: Float = x,
        endY: Float = y,
        durationMs: Long = 100L
    ): Boolean {
        val path = Path()
        val effectiveDuration = when (type.lowercase()) {
            "click", "tap" -> {
                path.moveTo(x, y)
                50L
            }
            "long_press" -> {
                path.moveTo(x, y)
                durationMs.coerceAtLeast(600L)
            }
            "swipe", "drag" -> {
                path.moveTo(x, y)
                path.lineTo(endX, endY)
                durationMs.coerceIn(50L, 10_000L)
            }
            else -> {
                path.moveTo(x, y)
                durationMs.coerceIn(50L, 5000L)
            }
        }

        val stroke = GestureDescription.StrokeDescription(path, 0L, effectiveDuration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val latch = CountDownLatch(1)
        var completed = false

        val dispatched = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    completed = true
                    latch.countDown()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    completed = false
                    latch.countDown()
                }
            },
            null
        )

        if (!dispatched) return false
        latch.await((effectiveDuration + 2000L), TimeUnit.MILLISECONDS)
        return completed
    }

    override fun onInterrupt() {
        Log.w(Constants.TAG_OTNOR, "BrainerAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        fingerprintHandler?.teardown()
        fingerprintHandler = null
        if (ServiceHolder.accessibilityService === this) {
            ServiceHolder.accessibilityService = null
        }
        Log.i(Constants.TAG_OTNOR, "BrainerAccessibilityService destroyed")
    }
}
