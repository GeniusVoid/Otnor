package com.brainer.otnor.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.brainer.otnor.MainActivity
import com.brainer.otnor.OtnorApp
import com.brainer.otnor.accessibility.ServiceHolder
import com.brainer.otnor.util.Constants
import com.brainer.otnor.util.PermissionManager
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

class BrainerForegroundService : Service() {

    companion object {
        private val _backendReachable = MutableStateFlow(false)
        val backendReachable: StateFlow<Boolean> = _backendReachable.asStateFlow()

        private val _lastPingTime = MutableStateFlow("Never")
        val lastPingTime: StateFlow<String> = _lastPingTime.asStateFlow()

        fun start(context: Context) {
            try {
                val intent = Intent(context, BrainerForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(Constants.TAG_OTNOR, "Failed starting BrainerForegroundService: ${e.message}", e)
            }
        }

        fun checkBackendOnce(): Boolean {
            return try {
                val url = URL(Constants.BACKEND_URL)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 2000
                    readTimeout = 2000
                    requestMethod = "GET"
                }
                val code = conn.responseCode
                conn.disconnect()
                val ok = code in 200..499
                _backendReachable.value = ok
                ok
            } catch (e: Exception) {
                _backendReachable.value = false
                false
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ServiceHolder.foregroundServiceRunning = true
        createNotificationChannel()
        val notification = buildNotification(
            backendUp = _backendReachable.value,
            accessibilityUp = PermissionManager.isAccessibilityServiceEnabled(this)
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    Constants.FOREGROUND_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(Constants.FOREGROUND_NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(Constants.TAG_OTNOR, "startForeground error: ${e.message}")
        }

        OtnorApp.ensureAndropiServerRunning(application)
        startMonitoringLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceHolder.foregroundServiceRunning = true
        OtnorApp.ensureAndropiServerRunning(application)
        return START_STICKY
    }

    private fun startMonitoringLoop() {
        serviceScope.launch {
            while (isActive) {
                OtnorApp.ensureAndropiServerRunning(application)
                val backendUp = checkBackendOnce()
                val accessibilityUp = PermissionManager.isAccessibilityServiceEnabled(this@BrainerForegroundService)
                _lastPingTime.value = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())

                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(
                    Constants.FOREGROUND_NOTIFICATION_ID,
                    buildNotification(backendUp, accessibilityUp)
                )
                delay(30_000L)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_FOREGROUND,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(backendUp: Boolean, accessibilityUp: Boolean): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = buildString {
            append("Andropi :8081 Ready • ")
            append(if (backendUp) "Termux :8080 Online" else "Termux :8080 Offline")
            if (!accessibilityUp) {
                append(" • Tap to enable Accessibility")
            }
        }

        val builder = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_FOREGROUND)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Brainer is running")
            .setContentText(statusText)
            .setContentIntent(pendingOpen)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (!accessibilityUp) {
            val accIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            val pendingAcc = PendingIntent.getActivity(
                this,
                1,
                accIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                R.drawable.ic_notification,
                "Enable Accessibility",
                pendingAcc
            )
        }

        return builder.build()
    }

    override fun onDestroy() {
        super.onDestroy()
        ServiceHolder.foregroundServiceRunning = false
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
