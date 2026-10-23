package com.brainer.otnor.andropi.commands

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.brainer.otnor.OtnorApp
import com.brainer.otnor.andropi.CommandResult
import com.brainer.otnor.util.JsonHelper.getStringOrDefault
import com.google.gson.JsonObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BiometricCommands(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    fun isAvailable(): CommandResult {
        val bm = BiometricManager.from(context)
        val res = bm.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        val canAuth = (res == BiometricManager.BIOMETRIC_SUCCESS)
        return CommandResult.ok(
            mapOf(
                "available" to canAuth,
                "types" to if (canAuth) listOf("fingerprint", "biometric") else emptyList(),
                "can_authenticate" to canAuth,
                "status_code" to res
            )
        )
    }

    fun authenticate(body: JsonObject): CommandResult {
        val activity = OtnorApp.currentActivity as? FragmentActivity
            ?: return CommandResult.error("Otnor MainActivity must be in foreground for biometric authentication")

        val title = body.getStringOrDefault("title", "Confirm Identity")
        val subtitle = body.getStringOrDefault("subtitle", "Verify to continue")
        val negativeText = body.getStringOrDefault("negative_text", "Cancel")

        val latch = CountDownLatch(1)
        var authenticated = false
        var authError: String? = null

        mainHandler.post {
            try {
                val executor = ContextCompat.getMainExecutor(activity)
                val prompt = BiometricPrompt(
                    activity,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            authenticated = true
                            latch.countDown()
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            authenticated = false
                            authError = errString.toString()
                            latch.countDown()
                        }

                        override fun onAuthenticationFailed() {
                            // User can retry while prompt is open
                        }
                    }
                )

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setNegativeButtonText(negativeText)
                    .build()

                prompt.authenticate(promptInfo)
            } catch (e: Exception) {
                authError = e.message
                latch.countDown()
            }
        }

        latch.await(90, TimeUnit.SECONDS)

        return CommandResult.ok(
            mapOf(
                "authenticated" to authenticated,
                "auth_type" to "fingerprint",
                "error" to authError
            )
        )
    }
}
