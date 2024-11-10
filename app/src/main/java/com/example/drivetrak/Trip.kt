package com.example.drivetrak

import com.google.firebase.Timestamp

data class Trip(
    val batteryLevel: Double,
    val deceleration: Int,
    val fuelLevel: Double,
    val rpm: Int,
    val speed: Int,
    val carID: String,
    val deviceID: String,
    val timeStamp: Long,
    val tripID: String,
    val alerts: Alerts? = null
)
