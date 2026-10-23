package com.brainer.otnor

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import com.brainer.otnor.service.BrainerForegroundService
import com.brainer.otnor.util.Constants
import com.brainer.otnor.util.PermissionManager
import com.brainer.otnor.webview.UIVersionManager
import com.brainer.otnor.webview.WebViewManager
import com.brainer.otnor.webview.WebViewScreen
import com.example.ui.theme.OtnorTheme
import kotlin.concurrent.thread

open class MainActivity : FragmentActivity() {

    private lateinit var uiVersionManager: UIVersionManager
    private lateinit var webViewManager: WebViewManager
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        fileChooserCallback?.onReceiveValue(uris.toTypedArray())
        fileChooserCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Ensure Andropi HTTP Server (:8081) and BrainerForegroundService are active
        OtnorApp.ensureAndropiServerRunning(application)
        BrainerForegroundService.start(this)
        thread {
            BrainerForegroundService.checkBackendOnce()
        }

        // 2. Initialize UIVersionManager & WebViewManager
        uiVersionManager = UIVersionManager(applicationContext)
        webViewManager = WebViewManager(
            context = this,
            uiVersionManager = uiVersionManager,
            onFileChooserRequest = { callback ->
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = callback
                fileChooserLauncher.launch("*/*")
            }
        )

        // 3. Request all runtime permissions upfront
        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val setupCompleted = prefs.getBoolean(Constants.PREF_SETUP_COMPLETED, false)
        PermissionManager.requestAllRuntimePermissions(this)

        setContent {
            OtnorTheme {
                WebViewScreen(
                    webViewManager = webViewManager,
                    showSetupWizardInitially = !setupCompleted,
                    onRequestRuntimePermissions = {
                        PermissionManager.requestAllRuntimePermissions(this)
                    },
                    onRequestBackgroundLocation = {
                        PermissionManager.requestBackgroundLocationIfNeeded(this)
                    },
                    onSetupCompleted = {
                        prefs.edit().putBoolean(Constants.PREF_SETUP_COMPLETED, true).apply()
                    }
                )
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        @Suppress("DEPRECATION")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        PermissionManager.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PermissionManager.REQUEST_CODE_BATCH) {
            val deniedCount = grantResults.count { it != PackageManager.PERMISSION_GRANTED }
            if (deniedCount > 0) {
                Toast.makeText(
                    this,
                    "Otnor requires all permissions for full Andropi capabilities ($deniedCount remaining).",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                // Once foreground location is granted, prompt for background location on Android 10+
                PermissionManager.requestBackgroundLocationIfNeeded(this)
            }
        }
    }
}
