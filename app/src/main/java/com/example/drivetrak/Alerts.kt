package com.example.drivetrak

data class Alerts(
    val batteryLevel: AlertCount? = null,
    val emergencyBraking: AlertCount? = null,
    val fuelLevel: AlertCount? = null,
    val hardAcceleration: AlertCount? = null,
    val speeding: AlertCount? = null,
    val drowsiness: AlertCount? = null,
    val yawning: AlertCount? = null
)

data class AlertCount(
    val count: Int
)

