package com.brainer.otnor.andropi.commands

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import com.brainer.otnor.andropi.CommandResult
import com.brainer.otnor.util.JsonHelper.getStringOrDefault
import com.google.android.gms.location.LocationServices
import com.google.gson.JsonObject
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SensorCommands(private val context: Context) {

    private val sensorManager: SensorManager
        get() = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    fun listSensors(): CommandResult {
        val sensors = sensorManager.getSensorList(Sensor.TYPE_ALL).map { s ->
            mapOf(
                "type" to s.type,
                "string_type" to s.stringType,
                "name" to s.name,
                "vendor" to s.vendor,
                "max_range" to s.maximumRange,
                "resolution" to s.resolution,
                "power_ma" to s.power
            )
        }
        return CommandResult.ok(mapOf("sensors" to sensors, "count" to sensors.size))
    }

    fun readSensor(body: JsonObject): CommandResult {
        val typeName = body.getStringOrDefault("type", "accelerometer").lowercase()
        val sensorType = when (typeName) {
            "accelerometer" -> Sensor.TYPE_ACCELEROMETER
            "gyroscope" -> Sensor.TYPE_GYROSCOPE
            "light" -> Sensor.TYPE_LIGHT
            "proximity" -> Sensor.TYPE_PROXIMITY
            "pressure" -> Sensor.TYPE_PRESSURE
            "magnetic_field", "magnetometer" -> Sensor.TYPE_MAGNETIC_FIELD
            "gravity" -> Sensor.TYPE_GRAVITY
            "step_counter" -> Sensor.TYPE_STEP_COUNTER
            "linear_acceleration" -> Sensor.TYPE_LINEAR_ACCELERATION
            "rotation_vector" -> Sensor.TYPE_ROTATION_VECTOR
            else -> Sensor.TYPE_ACCELEROMETER
        }

        val sensor = sensorManager.getDefaultSensor(sensorType)
            ?: return CommandResult.error("Sensor '$typeName' not available on this device")

        val latch = CountDownLatch(1)
        var valuesList: List<Float> = emptyList()
        var accuracyVal = 0

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null) {
                    valuesList = event.values.toList()
                    accuracyVal = event.accuracy
                    latch.countDown()
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                accuracyVal = accuracy
            }
        }

        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        val received = latch.await(2, TimeUnit.SECONDS)
        sensorManager.unregisterListener(listener)

        if (!received) {
            return CommandResult.error("Timed out waiting for sensor '$typeName' reading")
        }

        return CommandResult.ok(
            mapOf(
                "type" to typeName,
                "values" to valuesList,
                "accuracy" to accuracyVal,
                "timestamp" to Instant.now().toString()
            )
        )
    }

    @SuppressLint("MissingPermission")
    fun getLocation(): CommandResult {
        var foundLocation: Location? = null
        val latch = CountDownLatch(1)

        try {
            val fused = LocationServices.getFusedLocationProviderClient(context)
            fused.lastLocation
                .addOnSuccessListener { loc ->
                    foundLocation = loc
                    latch.countDown()
                }
                .addOnFailureListener {
                    latch.countDown()
                }
            latch.await(3, TimeUnit.SECONDS)
        } catch (_: Exception) {
        }

        if (foundLocation == null) {
            try {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val providers = lm.getProviders(true)
                for (p in providers) {
                    val l = lm.getLastKnownLocation(p)
                    if (l != null && (foundLocation == null || l.accuracy < foundLocation!!.accuracy)) {
                        foundLocation = l
                    }
                }
            } catch (e: Exception) {
                return CommandResult.error("Failed reading location: ${e.message}")
            }
        }

        val loc = foundLocation
            ?: return CommandResult.error("Location unavailable (ensure GPS is enabled and location permission is granted)")

        return CommandResult.ok(
            mapOf(
                "latitude" to loc.latitude,
                "longitude" to loc.longitude,
                "altitude" to loc.altitude,
                "accuracy" to loc.accuracy,
                "speed" to loc.speed,
                "provider" to (loc.provider ?: "fused")
            )
        )
    }
}
