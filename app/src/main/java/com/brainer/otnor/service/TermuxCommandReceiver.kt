package com.brainer.otnor.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.brainer.otnor.accessibility.ScreenFreezeOverlay
import com.brainer.otnor.accessibility.ServiceHolder
import com.brainer.otnor.util.Constants
import com.brainer.otnor.webview.WebViewManager

class TermuxCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != "com.brainer.otnor.COMMAND") return
        val cmd = intent.getStringExtra("cmd") ?: intent.getStringExtra("command") ?: return
        val arg = intent.getStringExtra("arg") ?: intent.getStringExtra("message") ?: ""
        Log.i(Constants.TAG_OTNOR, "TermuxCommandReceiver received cmd=$cmd arg=$arg")

        Handler(Looper.getMainLooper()).post {
            when (cmd.lowercase()) {
                "reload" -> WebViewManager.reloadFromExternal()
                "load_url" -> if (arg.isNotBlank()) WebViewManager.loadUrlFromExternal(arg)
                "toast" -> Toast.makeText(context, arg.ifBlank { "Otnor Command Received" }, Toast.LENGTH_SHORT).show()
                "popup" -> ScreenFreezeOverlay.showQuickActionsPopup(context)
                "freeze" -> ServiceHolder.accessibilityService?.let { svc ->
                    Thread { ScreenFreezeOverlay.freezeCurrentScreen(svc) }.start()
                }
            }
        }
    }
}
