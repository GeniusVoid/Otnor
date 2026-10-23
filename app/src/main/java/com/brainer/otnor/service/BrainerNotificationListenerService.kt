package com.brainer.otnor.service

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.brainer.otnor.util.Constants
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

data class InterceptedMessage(
    val id: String,
    val package_name: String,
    val sender: String,
    val text: String,
    val timestamp: String,
    val can_reply: Boolean
)

class BrainerNotificationListenerService : NotificationListenerService() {

    private data class ReplyActionHolder(
        val action: Notification.Action,
        val remoteInputs: Array<RemoteInput>
    )

    companion object {
        private val interceptedMessages = CopyOnWriteArrayList<InterceptedMessage>()
        private val replyActions = ConcurrentHashMap<String, ReplyActionHolder>()

        @Volatile
        var instance: BrainerNotificationListenerService? = null
            private set

        fun getMessages(): List<InterceptedMessage> = interceptedMessages.toList()

        fun replyToNotification(notificationId: String, replyText: String): Boolean {
            val service = instance ?: return false
            val holder = replyActions[notificationId] ?: return false
            return try {
                val intent = Intent()
                val bundle = Bundle()
                for (remoteInput in holder.remoteInputs) {
                    bundle.putCharSequence(remoteInput.resultKey, replyText)
                }
                RemoteInput.addResultsToIntent(holder.remoteInputs, intent, bundle)
                holder.action.actionIntent.send(service, 0, intent)
                true
            } catch (e: Exception) {
                Log.e(Constants.TAG_OTNOR, "Failed replying to notification $notificationId: ${e.message}", e)
                false
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.i(Constants.TAG_OTNOR, "BrainerNotificationListenerService connected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (sbn.packageName == packageName) return
        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
            ?: ""

        if (title.isEmpty() && text.isEmpty()) return

        val msgId = sbn.id.toString()
        var canReply = false

        val actions = sbn.notification.actions
        if (actions != null) {
            for (action in actions) {
                val remoteInputs = action.remoteInputs
                if (!remoteInputs.isNullOrEmpty()) {
                    canReply = true
                    replyActions[msgId] = ReplyActionHolder(action, remoteInputs)
                    break
                }
            }
        }

        val item = InterceptedMessage(
            id = msgId,
            package_name = sbn.packageName,
            sender = title.ifEmpty { sbn.packageName },
            text = text,
            timestamp = Instant.ofEpochMilli(sbn.postTime).toString(),
            can_reply = canReply
        )

        interceptedMessages.removeAll { it.id == msgId && it.package_name == sbn.packageName }
        interceptedMessages.add(0, item)
        while (interceptedMessages.size > 100) {
            interceptedMessages.removeAt(interceptedMessages.lastIndex)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance === this) {
            instance = null
        }
    }
}
