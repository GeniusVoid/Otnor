package com.brainer.otnor

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.brainer.otnor.accessibility.GestureActionRegistry
import com.brainer.otnor.andropi.AndropiServer
import com.brainer.otnor.util.Constants
import java.lang.ref.WeakReference

class OtnorApp : Application(), Application.ActivityLifecycleCallbacks {

    companion object {
        @Volatile
        lateinit var instance: OtnorApp
            private set

        @Volatile
        private var currentActivityRef: WeakReference<Activity>? = null

        val currentActivity: Activity?
            get() = currentActivityRef?.get()

        @Volatile
        var andropiServer: AndropiServer? = null
            private set

        val startTimeMs: Long = System.currentTimeMillis()

        @Synchronized
        fun ensureAndropiServerRunning(app: Application): AndropiServer {
            val existing = andropiServer
            if (existing != null && existing.isAlive) {
                return existing
            }
            val server = AndropiServer(app)
            try {
                server.start(15_000, false)
                andropiServer = server
                Log.i(Constants.TAG_ANDROPI, "Andropi HTTP Server started on 127.0.0.1:${Constants.ANDROPI_PORT}")
            } catch (e: Exception) {
                Log.e(Constants.TAG_ANDROPI, "Failed to start Andropi HTTP Server: ${e.message}", e)
            }
            return server
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        registerActivityLifecycleCallbacks(this)
        GestureActionRegistry.init(this)
        ensureAndropiServerRunning(this)
        Log.i(Constants.TAG_OTNOR, "OtnorApp initialized")
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        currentActivityRef = WeakReference(activity)
    }

    override fun onActivityStarted(activity: Activity) {
        currentActivityRef = WeakReference(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivityRef = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivityRef?.get() === activity) {
            currentActivityRef = null
        }
    }
}
