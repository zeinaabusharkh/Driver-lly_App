package com.example.drivetrak

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.firestore.FirebaseFirestore

class TripDetail : AppCompatActivity() {
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_trip_detail)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        firestore = FirebaseFirestore.getInstance()

        val tripId = intent.getStringExtra("tripId")
        if (tripId != null) {
            loadTripDetails(tripId)
        } else {
            Toast.makeText(this, "No trip ID provided", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadTripDetails(tripId: String) {
        firestore.collection("tripReports").document(tripId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {

                    // Retrieve data from the document
                    val duration = document.getString("totalTime") ?: "Unknown"
                    val date = document.getString("date") ?: "Unknown"
                    val startTime = document.getString("startTime") ?: "Unknown"
                    val endTime = document.getString("endTime") ?: "Unknown"
                    val overallScore = document.getDouble("OverAllScoreOfTrip")?.toInt() ?: 0  // Convert to Int
                    val averageSpeed = document.getLong("averageSpeed")?.toInt() ?: 0
                    val averageRpm = document.getLong("averageRpm")?.toInt() ?: 0
                    val highestSpeed = document.getLong("highestSpeed")?.toInt() ?: 0
                    val lowestSpeed = document.getLong("lowestSpeed")?.toInt() ?: 0
                    val totalFuelUsed = document.getDouble("totalFuelUsed") ?: 0.0
                    val feedbackMessage = document.getString("FeedbackMessage") ?: "Not Available"

                    // Retrieve the 'alerts' map (cast to Map<String, Long>)
                    val alerts = document.get("alerts") as? Map<String, Long> ?: emptyMap()

                    // Extract individual alert counts from the alerts map with default values
                    val emergencyBraking = alerts["emergencyBraking"]?.toInt() ?: 0
                    val hardAcceleration = alerts["hardAcceleration"]?.toInt() ?: 0
                    val speeding = alerts["speeding"]?.toInt() ?: 0
                    val batteryAlerts = alerts["batteryAlerts"]?.toInt() ?: 0
                    val fuelAlerts = alerts["fuelAlerts"]?.toInt() ?: 0
                    val drowsyCount = alerts["drowsiness"]?.toInt() ?: 0
                    val yawningCount = alerts["yawning"]?.toInt() ?: 0

                    // Views
                    val durationTextView: TextView = findViewById(R.id.trip_duration)
                    val dateTextView: TextView = findViewById(R.id.trip_date)
                    val tripTimeText: TextView = findViewById(R.id.trip_times)
                    val tripScoreText: TextView = findViewById(R.id.overall_score_text)
                    val emergencyBrakingTextView: TextView = findViewById(R.id.emergency_braking_count)
                    val hardAccelerationTextView: TextView = findViewById(R.id.hard_acceleration_count)
                    val speedingTextView: TextView = findViewById(R.id.speeding_count)
                    val batteryAlertTextView: TextView = findViewById(R.id.battery_alert_count)
                    val fuelAlertTextView: TextView = findViewById(R.id.fuel_alert_count)
                    val averageSpeedTextView: TextView = findViewById(R.id.average_speed)
                    val averageRpmTextView: TextView = findViewById(R.id.average_rpm)
                    val highestSpeedTextView: TextView = findViewById(R.id.highest_speed)
                    val lowestSpeedTextView: TextView = findViewById(R.id.lowest_speed)
                    val fuelTextView: TextView = findViewById(R.id.fuel_used)
                    val tripTitleTextView: TextView = findViewById(R.id.trip_title)
                    val drowsyCountTextView: TextView = findViewById(R.id.drowsy_count)
                    val yawningCountTextView: TextView = findViewById(R.id.yawning_count)
                    val feedbackMessageTextView: TextView = findViewById(R.id.feedback_message)

                    // Set the data to the respective TextViews
                    durationTextView.text = "Duration: $duration"
                    dateTextView.text = date
                    tripTimeText.text = "Start: $startTime | End: $endTime"
                    tripScoreText.text = "$overallScore %"
                    emergencyBrakingTextView.text = "Emergency Braking: $emergencyBraking"
                    hardAccelerationTextView.text = "Hard Acceleration: $hardAcceleration"
                    speedingTextView.text = "Speeding: $speeding"
                    batteryAlertTextView.text = "Battery Alerts: $batteryAlerts"
                    fuelAlertTextView.text = "Fuel Alerts: $fuelAlerts"
                    tripTitleTextView.text = tripId
                    drowsyCountTextView.text = "Drowsy: $drowsyCount"
                    yawningCountTextView.text = "Yawning: $yawningCount"
                    averageSpeedTextView.text = "Average Speed: $averageSpeed km/h"
                    averageRpmTextView.text = "Average RPM: $averageRpm RPM"
                    highestSpeedTextView.text = "Highest Speed: $highestSpeed km/h"
                    lowestSpeedTextView.text = "Lowest Speed: $lowestSpeed km/h"
                    fuelTextView.text = "Fuel Used: $totalFuelUsed ml"
                    feedbackMessageTextView.text = feedbackMessage

                    // Set background color based on overall score
                    setScoreBackgroundColor(tripScoreText, overallScore)

                } else {
                    Toast.makeText(this, "Trip not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }



    private fun setScoreBackgroundColor(textView: TextView, score: Int) {
        val color = when {
            score >= 80 -> android.graphics.Color.GREEN
            score >= 50 -> android.graphics.Color.argb(255, 255, 165, 0) // Orange
            score.toInt() == 0 -> android.graphics.Color.BLACK
            else -> android.graphics.Color.RED
        }

        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.OVAL
        drawable.setColor(color)
        textView.background = drawable
    }
}