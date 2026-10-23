package com.brainer.otnor.andropi.commands

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.brainer.otnor.andropi.CommandResult
import com.brainer.otnor.util.JsonHelper.getStringOrNull
import com.google.gson.JsonObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ClipboardCommands(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    fun get(): CommandResult {
        var text = ""
        var hasContent = false
        val latch = CountDownLatch(1)
        mainHandler.post {
            try {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = cm.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    text = clip.getItemAt(0).coerceToText(context)?.toString() ?: ""
                    hasContent = text.isNotEmpty()
                }
            } finally {
                latch.countDown()
            }
        }
        latch.await(2, TimeUnit.SECONDS)
        return CommandResult.ok(
            mapOf(
                "text" to text,
                "has_content" to hasContent
            )
        )
    }

    fun set(body: JsonObject): CommandResult {
        val text = body.getStringOrNull("text") ?: return CommandResult.error("Missing 'text'")
        val latch = CountDownLatch(1)
        mainHandler.post {
            try {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Otnor", text))
            } finally {
                latch.countDown()
            }
        }
        latch.await(2, TimeUnit.SECONDS)
        return CommandResult.ok(mapOf("set" to true, "length" to text.length))
    }
}
