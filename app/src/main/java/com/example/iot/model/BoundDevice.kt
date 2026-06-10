package com.example.iot.model

import java.io.Serializable

data class BoundDevice(
    val deviceId: String,
    val deviceName: String,
    val macAddress: String,
    val bindTime: Long = System.currentTimeMillis(),
    var isOnline: Boolean = false
) : Serializable