package com.brainer.otnor.andropi.commands

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import com.brainer.otnor.OtnorApp
import com.brainer.otnor.andropi.CommandResult
import com.brainer.otnor.util.JsonHelper.getBooleanOrDefault
import com.brainer.otnor.util.JsonHelper.getStringOrDefault
import com.google.gson.JsonObject
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DialogCommands {

    private val mainHandler = Handler(Looper.getMainLooper())

    fun showAlert(body: JsonObject): CommandResult {
        val activity = OtnorApp.currentActivity
            ?: return CommandResult.error("Otnor must be in foreground to show native dialogs")

        val title = body.getStringOrDefault("title", "Alert")
        val message = body.getStringOrDefault("message", "")
        val buttonText = body.getStringOrDefault("button_text", "OK")

        val latch = CountDownLatch(1)
        mainHandler.post {
            try {
                AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton(buttonText) { dlg, _ ->
                        dlg.dismiss()
                        latch.countDown()
                    }
                    .setOnCancelListener {
                        latch.countDown()
                    }
                    .show()
            } catch (e: Exception) {
                latch.countDown()
            }
        }

        latch.await(120, TimeUnit.SECONDS)
        return CommandResult.ok(mapOf("dismissed" to true))
    }

    fun showConfirm(body: JsonObject): CommandResult {
        val activity = OtnorApp.currentActivity
            ?: return CommandResult.error("Otnor must be in foreground to show native dialogs")

        val title = body.getStringOrDefault("title", "Confirm")
        val message = body.getStringOrDefault("message", "")
        val positive = body.getStringOrDefault("positive", "Yes")
        val negative = body.getStringOrDefault("negative", "No")

        val latch = CountDownLatch(1)
        var confirmed = false

        mainHandler.post {
            try {
                AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton(positive) { dlg, _ ->
                        confirmed = true
                        dlg.dismiss()
                        latch.countDown()
                    }
                    .setNegativeButton(negative) { dlg, _ ->
                        confirmed = false
                        dlg.dismiss()
                        latch.countDown()
                    }
                    .setOnCancelListener {
                        confirmed = false
                        latch.countDown()
                    }
                    .show()
            } catch (e: Exception) {
                latch.countDown()
            }
        }

        latch.await(120, TimeUnit.SECONDS)
        return CommandResult.ok(mapOf("confirmed" to confirmed))
    }

    fun showInput(body: JsonObject): CommandResult {
        val activity = OtnorApp.currentActivity
            ?: return CommandResult.error("Otnor must be in foreground to show native dialogs")

        val title = body.getStringOrDefault("title", "Input")
        val message = body.getStringOrDefault("message", "")
        val hint = body.getStringOrDefault("hint", "")
        val defaultValue = body.getStringOrDefault("default_value", "")
        val inputTypeStr = body.getStringOrDefault("input_type", "text").lowercase()

        val latch = CountDownLatch(1)
        var resultText = defaultValue
        var submitted = false

        mainHandler.post {
            try {
                val editText = EditText(activity).apply {
                    this.hint = hint
                    setText(defaultValue)
                    inputType = when (inputTypeStr) {
                        "number" -> InputType.TYPE_CLASS_NUMBER
                        "password" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                        "multiline" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        else -> InputType.TYPE_CLASS_TEXT
                    }
                }
                val container = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(48, 24, 48, 8)
                    addView(editText)
                }

                AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setMessage(message)
                    .setView(container)
                    .setPositiveButton("Submit") { dlg, _ ->
                        resultText = editText.text?.toString() ?: ""
                        submitted = true
                        dlg.dismiss()
                        latch.countDown()
                    }
                    .setNegativeButton("Cancel") { dlg, _ ->
                        submitted = false
                        dlg.dismiss()
                        latch.countDown()
                    }
                    .setOnCancelListener {
                        submitted = false
                        latch.countDown()
                    }
                    .show()
            } catch (e: Exception) {
                latch.countDown()
            }
        }

        latch.await(180, TimeUnit.SECONDS)
        return CommandResult.ok(
            mapOf(
                "text" to resultText,
                "submitted" to submitted
            )
        )
    }

    fun showDatePicker(body: JsonObject): CommandResult {
        val activity = OtnorApp.currentActivity
            ?: return CommandResult.error("Otnor must be in foreground to show native dialogs")

        val title = body.getStringOrDefault("title", "Select Date")
        val cal = Calendar.getInstance()
        var selYear = cal.get(Calendar.YEAR)
        var selMonth = cal.get(Calendar.MONTH) + 1
        var selDay = cal.get(Calendar.DAY_OF_MONTH)

        val latch = CountDownLatch(1)
        mainHandler.post {
            try {
                val dlg = DatePickerDialog(
                    activity,
                    { _, year, monthOfYear, dayOfMonth ->
                        selYear = year
                        selMonth = monthOfYear + 1
                        selDay = dayOfMonth
                        latch.countDown()
                    },
                    selYear,
                    selMonth - 1,
                    selDay
                )
                dlg.setTitle(title)
                dlg.setOnCancelListener { latch.countDown() }
                dlg.show()
            } catch (e: Exception) {
                latch.countDown()
            }
        }

        latch.await(120, TimeUnit.SECONDS)
        val dateString = String.format(Locale.US, "%04d-%02d-%02d", selYear, selMonth, selDay)
        return CommandResult.ok(
            mapOf(
                "year" to selYear,
                "month" to selMonth,
                "day" to selDay,
                "date_string" to dateString
            )
        )
    }

    fun showTimePicker(body: JsonObject): CommandResult {
        val activity = OtnorApp.currentActivity
            ?: return CommandResult.error("Otnor must be in foreground to show native dialogs")

        val title = body.getStringOrDefault("title", "Select Time")
        val is24Hour = body.getBooleanOrDefault("is_24_hour", true)
        val cal = Calendar.getInstance()
        var selHour = cal.get(Calendar.HOUR_OF_DAY)
        var selMinute = cal.get(Calendar.MINUTE)

        val latch = CountDownLatch(1)
        mainHandler.post {
            try {
                val dlg = TimePickerDialog(
                    activity,
                    { _, hourOfDay, minute ->
                        selHour = hourOfDay
                        selMinute = minute
                        latch.countDown()
                    },
                    selHour,
                    selMinute,
                    is24Hour
                )
                dlg.setTitle(title)
                dlg.setOnCancelListener { latch.countDown() }
                dlg.show()
            } catch (e: Exception) {
                latch.countDown()
            }
        }

        latch.await(120, TimeUnit.SECONDS)
        val timeString = String.format(Locale.US, "%02d:%02d", selHour, selMinute)
        return CommandResult.ok(
            mapOf(
                "hour" to selHour,
                "minute" to selMinute,
                "time_string" to timeString
            )
        )
    }
}
