package com.brainer.otnor.andropi

import android.content.Context
import com.brainer.otnor.andropi.commands.AccessibilityCommands
import com.brainer.otnor.andropi.commands.AppCommands
import com.brainer.otnor.andropi.commands.BiometricCommands
import com.brainer.otnor.andropi.commands.ClipboardCommands
import com.brainer.otnor.andropi.commands.DeviceCommands
import com.brainer.otnor.andropi.commands.DialogCommands
import com.brainer.otnor.andropi.commands.MediaCommands
import com.brainer.otnor.andropi.commands.NetworkCommands
import com.brainer.otnor.andropi.commands.NotificationCommands
import com.brainer.otnor.andropi.commands.OverlayCommands
import com.brainer.otnor.andropi.commands.ScreenCommands
import com.brainer.otnor.andropi.commands.SensorCommands
import com.brainer.otnor.andropi.commands.StorageCommands
import com.brainer.otnor.andropi.commands.SystemCommands
import com.google.gson.JsonObject

class AndropiRouter(context: Context) {

    private val deviceCommands = DeviceCommands(context)
    private val storageCommands = StorageCommands()
    private val clipboardCommands = ClipboardCommands(context)
    private val notificationCommands = NotificationCommands(context)
    private val mediaCommands = MediaCommands(context)
    private val sensorCommands = SensorCommands(context)
    private val screenCommands = ScreenCommands(context)
    private val dialogCommands = DialogCommands()
    private val appCommands = AppCommands(context)
    private val networkCommands = NetworkCommands(context)
    private val biometricCommands = BiometricCommands(context)
    private val accessibilityCommands = AccessibilityCommands(context)
    private val overlayCommands = OverlayCommands(context)
    private val systemCommands = SystemCommands(context)

    fun route(method: String, path: String, body: JsonObject): CommandResult {
        val cleanPath = if (path.isEmpty()) "/" else path
        return try {
            when {
                // Device Commands
                cleanPath == "/andropi/v1/device/info" -> deviceCommands.getInfo()
                cleanPath == "/andropi/v1/device/battery" -> deviceCommands.getBattery()
                cleanPath == "/andropi/v1/device/memory" -> deviceCommands.getMemory()
                cleanPath == "/andropi/v1/device/display" -> deviceCommands.getDisplay()
                cleanPath == "/andropi/v1/device/storage-stats" -> deviceCommands.getStorageStats()

                // Storage Commands
                cleanPath == "/andropi/v1/storage/read" -> storageCommands.read(body)
                cleanPath == "/andropi/v1/storage/write" -> storageCommands.write(body)
                cleanPath == "/andropi/v1/storage/append" -> storageCommands.append(body)
                cleanPath == "/andropi/v1/storage/list" -> storageCommands.list(body)
                cleanPath == "/andropi/v1/storage/delete" -> storageCommands.delete(body)
                cleanPath == "/andropi/v1/storage/move" -> storageCommands.move(body)
                cleanPath == "/andropi/v1/storage/copy" -> storageCommands.copy(body)
                cleanPath == "/andropi/v1/storage/exists" -> storageCommands.exists(body)
                cleanPath == "/andropi/v1/storage/stat" -> storageCommands.stat(body)
                cleanPath == "/andropi/v1/storage/mkdir" -> storageCommands.mkdir(body)

                // Clipboard Commands
                cleanPath == "/andropi/v1/clipboard/get" -> clipboardCommands.get()
                cleanPath == "/andropi/v1/clipboard/set" -> clipboardCommands.set(body)

                // Notification Commands
                cleanPath == "/andropi/v1/notification/show" -> notificationCommands.show(body)
                cleanPath == "/andropi/v1/notification/cancel" -> notificationCommands.cancel(body)
                cleanPath == "/andropi/v1/notification/cancel-all" -> notificationCommands.cancelAll()
                cleanPath == "/andropi/v1/notification/active" -> notificationCommands.getActive()
                cleanPath == "/andropi/v1/notification/channel/create" -> notificationCommands.createChannel(body)
                cleanPath == "/andropi/v1/notification/listener/messages" -> notificationCommands.getInterceptedMessages()
                cleanPath == "/andropi/v1/notification/listener/reply" -> notificationCommands.replyToMessage(body)

                // Media Commands
                cleanPath == "/andropi/v1/media/tts/speak" -> mediaCommands.ttsSpeak(body)
                cleanPath == "/andropi/v1/media/tts/stop" -> mediaCommands.ttsStop()
                cleanPath == "/andropi/v1/media/tts/voices" -> mediaCommands.ttsVoices()
                cleanPath == "/andropi/v1/media/audio/record/start" -> mediaCommands.audioRecordStart(body)
                cleanPath == "/andropi/v1/media/audio/record/stop" -> mediaCommands.audioRecordStop()
                cleanPath == "/andropi/v1/media/audio/play" -> mediaCommands.audioPlay(body)
                cleanPath == "/andropi/v1/media/audio/stop" -> mediaCommands.audioStop()
                cleanPath == "/andropi/v1/media/camera/photo" -> mediaCommands.cameraPhoto(body)

                // Sensor Commands
                cleanPath == "/andropi/v1/sensors/list" -> sensorCommands.listSensors()
                cleanPath == "/andropi/v1/sensors/read" -> sensorCommands.readSensor(body)
                cleanPath == "/andropi/v1/sensors/location" -> sensorCommands.getLocation()

                // Screen & UI Commands
                cleanPath == "/andropi/v1/screen/brightness" -> screenCommands.setBrightness(body)
                cleanPath == "/andropi/v1/screen/orientation" -> screenCommands.setOrientation(body)
                cleanPath == "/andropi/v1/screen/keep-on" -> screenCommands.setKeepOn(body)
                cleanPath == "/andropi/v1/screen/toast" -> screenCommands.showToast(body)
                cleanPath == "/andropi/v1/screen/vibrate" -> screenCommands.vibrate(body)

                // Dialog Commands (blocking)
                cleanPath == "/andropi/v1/dialog/alert" -> dialogCommands.showAlert(body)
                cleanPath == "/andropi/v1/dialog/confirm" -> dialogCommands.showConfirm(body)
                cleanPath == "/andropi/v1/dialog/input" -> dialogCommands.showInput(body)
                cleanPath == "/andropi/v1/dialog/picker/date" -> dialogCommands.showDatePicker(body)
                cleanPath == "/andropi/v1/dialog/picker/time" -> dialogCommands.showTimePicker(body)

                // App Management Commands
                cleanPath == "/andropi/v1/apps/list" -> appCommands.listApps()
                cleanPath == "/andropi/v1/apps/launch" -> appCommands.launchApp(body)
                cleanPath == "/andropi/v1/apps/intent" -> appCommands.sendIntent(body)
                cleanPath == "/andropi/v1/apps/running" -> appCommands.getRunningApps()

                // Network Commands
                cleanPath == "/andropi/v1/network/wifi/info" -> networkCommands.getWifiInfo()
                cleanPath == "/andropi/v1/network/wifi/scan" -> networkCommands.scanWifi()
                cleanPath == "/andropi/v1/network/bluetooth/scan" -> networkCommands.scanBluetooth()
                cleanPath == "/andropi/v1/network/bluetooth/connect" -> networkCommands.connectBluetooth(body)

                // Biometric Commands
                cleanPath == "/andropi/v1/biometric/authenticate" -> biometricCommands.authenticate(body)
                cleanPath == "/andropi/v1/biometric/available" -> biometricCommands.isAvailable()

                // Accessibility Commands
                cleanPath == "/andropi/v1/accessibility/status" -> accessibilityCommands.getStatus()
                cleanPath == "/andropi/v1/accessibility/capture-text" -> accessibilityCommands.captureText()
                cleanPath == "/andropi/v1/accessibility/capture-app" -> accessibilityCommands.captureApp(body)
                cleanPath == "/andropi/v1/accessibility/find-text" -> accessibilityCommands.findText(body)
                cleanPath == "/andropi/v1/accessibility/click-node" -> accessibilityCommands.clickNode(body)
                cleanPath == "/andropi/v1/accessibility/perform-action" -> accessibilityCommands.performAction(body)
                cleanPath == "/andropi/v1/accessibility/global-action" -> accessibilityCommands.globalAction(body)
                cleanPath == "/andropi/v1/accessibility/dispatch-gesture" -> accessibilityCommands.dispatchGesture(body)
                cleanPath == "/andropi/v1/accessibility/scroll" -> accessibilityCommands.scroll(body)
                cleanPath == "/andropi/v1/accessibility/key-event/register" -> accessibilityCommands.registerKeyEvent(body)
                cleanPath == "/andropi/v1/accessibility/gesture/register" -> accessibilityCommands.registerGesture(body)
                cleanPath == "/andropi/v1/accessibility/gesture/unregister" -> accessibilityCommands.unregisterGesture(body)
                cleanPath == "/andropi/v1/accessibility/gesture/list" -> accessibilityCommands.listGestures()

                // Overlay Commands
                cleanPath == "/andropi/v1/overlay/freeze" -> overlayCommands.freeze()
                cleanPath == "/andropi/v1/overlay/unfreeze" -> overlayCommands.unfreeze()
                cleanPath == "/andropi/v1/overlay/popup" -> overlayCommands.showPopup(body)
                cleanPath == "/andropi/v1/overlay/dismiss" -> overlayCommands.dismissPopup()
                cleanPath == "/andropi/v1/overlay/status" -> overlayCommands.getStatus()

                // System Commands
                cleanPath == "/andropi/v1/system/status" || cleanPath == "/" -> systemCommands.getStatus()
                cleanPath == "/andropi/v1/system/permissions" -> systemCommands.getPermissions()
                cleanPath == "/andropi/v1/system/permission/request" -> systemCommands.requestPermission(body)
                cleanPath == "/andropi/v1/system/commands" -> systemCommands.getAllCommands()
                cleanPath == "/andropi/v1/system/version" -> systemCommands.getVersion()

                else -> CommandResult.error("Unknown endpoint: $method $cleanPath")
            }
        } catch (e: Exception) {
            CommandResult.error(e.message ?: "Error executing command at $cleanPath")
        }
    }
}
