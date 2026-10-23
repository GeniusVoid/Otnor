package com.brainer.otnor.andropi.commands

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.wifi.WifiManager
import com.brainer.otnor.andropi.CommandResult
import com.brainer.otnor.util.JsonHelper.getStringOrNull
import com.google.gson.JsonObject
import java.util.Locale

class NetworkCommands(private val context: Context) {

    private val wifiManager: WifiManager
        get() = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val bluetoothAdapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    @SuppressLint("MissingPermission")
    fun getWifiInfo(): CommandResult {
        @Suppress("DEPRECATION")
        val info = wifiManager.connectionInfo
        @Suppress("DEPRECATION")
        val ipInt = info.ipAddress
        val ipStr = String.format(
            Locale.US,
            "%d.%d.%d.%d",
            ipInt and 0xff,
            ipInt shr 8 and 0xff,
            ipInt shr 16 and 0xff,
            ipInt shr 24 and 0xff
        )

        return CommandResult.ok(
            mapOf(
                "ssid" to (info.ssid?.trim('"') ?: "<unknown>"),
                "bssid" to (info.bssid ?: ""),
                "ip_address" to ipStr,
                "link_speed_mbps" to info.linkSpeed,
                "frequency_mhz" to info.frequency,
                "signal_strength_dbm" to info.rssi,
                "wifi_enabled" to wifiManager.isWifiEnabled
            )
        )
    }

    @SuppressLint("MissingPermission")
    fun scanWifi(): CommandResult {
        try {
            @Suppress("DEPRECATION")
            wifiManager.startScan()
        } catch (_: Exception) {
        }

        val results = try {
            wifiManager.scanResults.map { r ->
                @Suppress("DEPRECATION")
                val ssid = r.SSID ?: ""
                mapOf(
                    "ssid" to ssid,
                    "bssid" to (r.BSSID ?: ""),
                    "signal_level_dbm" to r.level,
                    "frequency_mhz" to r.frequency,
                    "capabilities" to (r.capabilities ?: "")
                )
            }
        } catch (e: Exception) {
            return CommandResult.error("Failed scanning WiFi networks: ${e.message}")
        }

        return CommandResult.ok(mapOf("networks" to results, "count" to results.size))
    }

    @SuppressLint("MissingPermission")
    fun scanBluetooth(): CommandResult {
        val adapter = bluetoothAdapter
            ?: return CommandResult.error("Bluetooth not supported on this device")

        val devices = mutableListOf<Map<String, Any?>>()
        try {
            adapter.bondedDevices?.forEach { dev ->
                devices.add(
                    mapOf(
                        "name" to (dev.name ?: "Unknown"),
                        "address" to dev.address,
                        "type" to dev.type,
                        "bond_state" to bondStateToString(dev.bondState)
                    )
                )
            }
        } catch (e: Exception) {
            return CommandResult.error("Bluetooth permission error: ${e.message}")
        }

        return CommandResult.ok(
            mapOf(
                "bluetooth_enabled" to adapter.isEnabled,
                "devices" to devices,
                "count" to devices.size
            )
        )
    }

    @SuppressLint("MissingPermission")
    fun connectBluetooth(body: JsonObject): CommandResult {
        val address = body.getStringOrNull("address")
            ?: return CommandResult.error("Missing Bluetooth 'address'")
        val adapter = bluetoothAdapter
            ?: return CommandResult.error("Bluetooth not supported on this device")

        return try {
            val device = adapter.getRemoteDevice(address)
            val bonded = device.createBond()
            CommandResult.ok(
                mapOf(
                    "address" to address,
                    "name" to (device.name ?: "Unknown"),
                    "bond_initiated" to bonded,
                    "bond_state" to bondStateToString(device.bondState)
                )
            )
        } catch (e: Exception) {
            CommandResult.error("Failed connecting to Bluetooth device $address: ${e.message}")
        }
    }

    private fun bondStateToString(state: Int): String = when (state) {
        BluetoothDevice.BOND_BONDED -> "bonded"
        BluetoothDevice.BOND_BONDING -> "bonding"
        else -> "none"
    }
}
