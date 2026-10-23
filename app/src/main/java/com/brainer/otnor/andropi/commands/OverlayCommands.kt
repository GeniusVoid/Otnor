package com.brainer.otnor.andropi.commands

import android.content.Context
import com.brainer.otnor.accessibility.PopupActionItem
import com.brainer.otnor.accessibility.ScreenFreezeOverlay
import com.brainer.otnor.accessibility.ServiceHolder
import com.brainer.otnor.andropi.CommandResult
import com.brainer.otnor.util.Constants
import com.brainer.otnor.util.JsonHelper.getIntOrDefault
import com.brainer.otnor.util.JsonHelper.getStringOrDefault
import com.google.gson.JsonObject

class OverlayCommands(private val context: Context) {

    fun freeze(): CommandResult {
        val svc = ServiceHolder.accessibilityService
            ?: return CommandResult.error("Accessibility service is not connected.")
        val result = ScreenFreezeOverlay.freezeCurrentScreen(svc)
        if (!result.frozen && result.error != null) {
            return CommandResult.error(result.error)
        }
        return CommandResult.ok(
            mapOf(
                "frozen" to result.frozen,
                "node_count" to result.node_count
            )
        )
    }

    fun unfreeze(): CommandResult {
        val result = ScreenFreezeOverlay.unfreezeScreen(context)
        return CommandResult.ok(result)
    }

    fun showPopup(body: JsonObject): CommandResult {
        val type = body.getStringOrDefault("type", "quick_actions").lowercase()
        val position = body.getStringOrDefault("position", "bottom_right")
        val widthDp = body.getIntOrDefault("width_dp", if (type == "webview") 350 else 220)
        val heightDp = body.getIntOrDefault("height_dp", if (type == "webview") 500 else 300)

        return if (type == "webview") {
            val url = body.getStringOrDefault("url", Constants.BACKEND_URL)
            val shown = ScreenFreezeOverlay.showWebViewPopup(context, url, position, widthDp, heightDp)
            if (shown) {
                CommandResult.ok(mapOf("popup_active" to true, "type" to "webview", "url" to url))
            } else {
                CommandResult.error("Overlay permission (SYSTEM_ALERT_WINDOW) is not granted")
            }
        } else {
            val actions = mutableListOf<PopupActionItem>()
            if (body.has("actions") && body.get("actions").isJsonArray) {
                val arr = body.getAsJsonArray("actions")
                for (elem in arr) {
                    if (elem.isJsonObject) {
                        val obj = elem.asJsonObject
                        val label = obj.getStringOrDefault("label", "Action")
                        val id = obj.getStringOrDefault("id", "action")
                        actions.add(PopupActionItem(label, id))
                    }
                }
            }
            val shown = if (actions.isNotEmpty()) {
                ScreenFreezeOverlay.showQuickActionsPopup(context, position, widthDp, heightDp, actions)
            } else {
                ScreenFreezeOverlay.showQuickActionsPopup(context, position, widthDp, heightDp)
            }
            if (shown) {
                CommandResult.ok(mapOf("popup_active" to true, "type" to "quick_actions"))
            } else {
                CommandResult.error("Overlay permission (SYSTEM_ALERT_WINDOW) is not granted")
            }
        }
    }

    fun dismissPopup(): CommandResult {
        ScreenFreezeOverlay.dismissPopup(context)
        return CommandResult.ok(mapOf("dismissed" to true))
    }

    fun getStatus(): CommandResult {
        return CommandResult.ok(
            mapOf(
                "freeze_active" to ScreenFreezeOverlay.isFreezeActive,
                "popup_active" to ScreenFreezeOverlay.isPopupActive,
                "popup_type" to ScreenFreezeOverlay.currentPopupType
            )
        )
    }
}
