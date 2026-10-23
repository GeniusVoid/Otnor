package com.brainer.otnor.andropi.commands

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.brainer.otnor.MainActivity
import com.brainer.otnor.andropi.CommandResult
import com.brainer.otnor.service.BrainerNotificationListenerService
import com.brainer.otnor.util.Constants
import com.brainer.otnor.util.JsonHelper.getBooleanOrDefault
import com.brainer.otnor.util.JsonHelper.getIntOrDefault
import com.brainer.otnor.util.JsonHelper.getStringOrDefault
import com.brainer.otnor.util.JsonHelper.getStringOrNull
import com.example.R
import com.google.gson.JsonObject
import java.time.Instant

class NotificationCommands(private val context: Context) {

    private val nm: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun show(body: JsonObject): CommandResult {
        val id = body.getIntOrDefault("id", (System.currentTimeMillis() % 100000).toInt())
        val title = body.getStringOrDefault("title", "Brainer")
        val text = body.getStringOrDefault("body", "")
        val channelId = body.getStringOrDefault("channel_id", Constants.NOTIFICATION_CHANNEL_REMINDERS)
        val priorityStr = body.getStringOrDefault("priority", "high").lowercase()
        val ongoing = body.getBooleanOrDefault("ongoing", false)
        val autoCancel = body.getBooleanOrDefault("auto_cancel", true)
        val vibrate = body.getBooleanOrDefault("vibrate", true)
        val sound = body.getBooleanOrDefault("sound", true)

        ensureChannel(channelId, channelId, priorityStr)

        val priority = when (priorityStr) {
            "high", "max" -> NotificationCompat.PRIORITY_HIGH
            "low" -> NotificationCompat.PRIORITY_LOW
            "min" -> NotificationCompat.PRIORITY_MIN
            else -> NotificationCompat.PRIORITY_DEFAULT
        }

        val openIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(priority)
            .setOngoing(ongoing)
            .setAutoCancel(autoCancel)
            .setContentIntent(pendingIntent)

        var defaults = 0
        if (vibrate) defaults = defaults or Notification.DEFAULT_VIBRATE
        if (sound) defaults = defaults or Notification.DEFAULT_SOUND
        if (defaults != 0) builder.setDefaults(defaults)

        nm.notify(id, builder.build())
        return CommandResult.ok(mapOf("id" to id, "shown" to true))
    }

    fun cancel(body: JsonObject): CommandResult {
        val id = body.getIntOrDefault("id", -1)
        if (id == -1) return CommandResult.error("Missing notification 'id'")
        nm.cancel(id)
        return CommandResult.ok(mapOf("id" to id, "cancelled" to true))
    }

    fun cancelAll(): CommandResult {
        nm.cancelAll()
        return CommandResult.ok(mapOf("cancelled_all" to true))
    }

    fun getActive(): CommandResult {
        val active = nm.activeNotifications.map { sbn ->
            val extras = sbn.notification?.extras
            mapOf(
                "id" to sbn.id,
                "package_name" to sbn.packageName,
                "title" to (extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""),
                "body" to (extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""),
                "post_time" to Instant.ofEpochMilli(sbn.postTime).toString(),
                "is_ongoing" to sbn.isOngoing
            )
        }
        return CommandResult.ok(mapOf("notifications" to active, "count" to active.size))
    }

    fun createChannel(body: JsonObject): CommandResult {
        val id = body.getStringOrNull("id") ?: return CommandResult.error("Missing channel 'id'")
        val name = body.getStringOrDefault("name", id)
        val importanceStr = body.getStringOrDefault("importance", "high").lowercase()
        val description = body.getStringOrDefault("description", "")

        ensureChannel(id, name, importanceStr, description)
        return CommandResult.ok(mapOf("id" to id, "name" to name, "created" to true))
    }

    fun getInterceptedMessages(): CommandResult {
        val messages = BrainerNotificationListenerService.getMessages()
        return CommandResult.ok(mapOf("messages" to messages))
    }

    fun replyToMessage(body: JsonObject): CommandResult {
        val notifId = body.getStringOrNull("notification_id")
            ?: return CommandResult.error("Missing 'notification_id'")
        val replyText = body.getStringOrNull("reply_text")
            ?: return CommandResult.error("Missing 'reply_text'")

        val ok = BrainerNotificationListenerService.replyToNotification(notifId, replyText)
        return if (ok) {
            CommandResult.ok(mapOf("replied" to true, "notification_id" to notifId))
        } else {
            CommandResult.error("Could not reply to notification $notifId (not found or no RemoteInput action)")
        }
    }

    private fun ensureChannel(
        id: String,
        name: String,
        importanceStr: String,
        description: String = ""
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = when (importanceStr) {
                "high", "max" -> NotificationManager.IMPORTANCE_HIGH
                "low" -> NotificationManager.IMPORTANCE_LOW
                "min" -> NotificationManager.IMPORTANCE_MIN
                else -> NotificationManager.IMPORTANCE_DEFAULT
            }
            val channel = NotificationChannel(id, name, importance).apply {
                this.description = description
            }
            nm.createNotificationChannel(channel)
        }
    }
}
