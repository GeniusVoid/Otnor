package com.brainer.otnor.accessibility

import android.accessibilityservice.FingerprintGestureController
import android.util.Log
import com.brainer.otnor.util.Constants

class FingerprintGestureHandler(private val service: BrainerAccessibilityService) {

    private var callback: FingerprintGestureController.FingerprintGestureCallback? = null
    var isAvailable: Boolean = false
        private set

    fun setup() {
        try {
            val controller = service.fingerprintGestureController
            if (controller == null) {
                isAvailable = false
                Log.w(
                    Constants.TAG_OTNOR,
                    "FingerprintGestureController is null on this device (side-mounted sensors may not support swipe gestures)"
                )
                return
            }

            isAvailable = controller.isGestureDetectionAvailable
            if (!isAvailable) {
                Log.i(
                    Constants.TAG_OTNOR,
                    "Fingerprint swipe gesture detection not available on hardware; fallback triggers (volume keys / quick popup) active."
                )
            }

            callback = object : FingerprintGestureController.FingerprintGestureCallback() {
                override fun onGestureDetectionAvailabilityChanged(available: Boolean) {
                    isAvailable = available
                    Log.i(Constants.TAG_OTNOR, "Fingerprint gesture availability changed: $available")
                }

                override fun onGestureDetected(gesture: Int) {
                    val gestureName = when (gesture) {
                        FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_UP -> "swipe_up"
                        FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_DOWN -> "swipe_down"
                        FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_LEFT -> "swipe_left"
                        FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_RIGHT -> "swipe_right"
                        else -> return
                    }
                    Log.i(Constants.TAG_OTNOR, "Fingerprint gesture detected: $gestureName")
                    GestureActionRegistry.executeAction(gestureName, service)
                }
            }

            controller.registerFingerprintGestureCallback(callback!!, null)
        } catch (e: Exception) {
            isAvailable = false
            Log.e(Constants.TAG_OTNOR, "Error registering fingerprint gesture callback: ${e.message}", e)
        }
    }

    fun teardown() {
        try {
            callback?.let {
                service.fingerprintGestureController?.unregisterFingerprintGestureCallback(it)
            }
        } catch (e: Exception) {
            Log.e(Constants.TAG_OTNOR, "Error unregistering fingerprint gesture callback: ${e.message}")
        }
        callback = null
    }
}
