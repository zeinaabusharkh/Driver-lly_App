package com.example.drivetrak

import com.google.firebase.Timestamp

data class Trip(
    val tripId: String = "",
    val departure: String = "",
    val destination: String = "",
    val duration: String = "",
    val date: String = "",
    val mapUrl: String? = null,
    val timestamp: Timestamp = Timestamp.now()
)
