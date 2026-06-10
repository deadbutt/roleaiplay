package com.example.iot.util

import android.content.Context
import android.content.SharedPreferences
import com.example.iot.model.BoundDevice
import org.json.JSONArray
import org.json.JSONObject

object DeviceStorage {
    private const val PREFS_NAME = "bound_devices"
    private const val KEY_DEVICES = "devices"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveDevice(context: Context, device: BoundDevice) {
        val devices = getDevices(context).toMutableList()
        // 去重：如果已存在相同 deviceId，先移除
        devices.removeAll { it.deviceId == device.deviceId }
        devices.add(device)
        saveDevices(context, devices)
    }

    fun getDevices(context: Context): List<BoundDevice> {
        val jsonStr = getPrefs(context).getString(KEY_DEVICES, "[]") ?: "[]"
        return parseDevices(jsonStr)
    }

    fun removeDevice(context: Context, deviceId: String) {
        val devices = getDevices(context).filter { it.deviceId != deviceId }
        saveDevices(context, devices)
    }

    fun clearDevices(context: Context) {
        getPrefs(context).edit().remove(KEY_DEVICES).apply()
    }

    private fun saveDevices(context: Context, devices: List<BoundDevice>) {
        val jsonArray = JSONArray()
        devices.forEach { device ->
            val obj = JSONObject().apply {
                put("deviceId", device.deviceId)
                put("deviceName", device.deviceName)
                put("macAddress", device.macAddress)
                put("bindTime", device.bindTime)
                put("isOnline", device.isOnline)
            }
            jsonArray.put(obj)
        }
        getPrefs(context).edit().putString(KEY_DEVICES, jsonArray.toString()).apply()
    }

    private fun parseDevices(jsonStr: String): List<BoundDevice> {
        val list = mutableListOf<BoundDevice>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(BoundDevice(
                    deviceId = obj.getString("deviceId"),
                    deviceName = obj.getString("deviceName"),
                    macAddress = obj.getString("macAddress"),
                    bindTime = obj.optLong("bindTime", System.currentTimeMillis()),
                    isOnline = obj.optBoolean("isOnline", false)
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}