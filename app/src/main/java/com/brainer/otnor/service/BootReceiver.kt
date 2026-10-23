package com.brainer.otnor.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.brainer.otnor.util.Constants

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.i(Constants.TAG_OTNOR, "BootReceiver triggered ($action) — starting BrainerForegroundService")
            BrainerForegroundService.start(context)
        }
    }
}
