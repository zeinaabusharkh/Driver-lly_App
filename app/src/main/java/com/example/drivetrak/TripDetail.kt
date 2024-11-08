package com.example.drivetrak

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
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
                if (document != null) {

            // Retrieve data from the document with default values if missing
                    val duration = document.getString("totalTime") ?: "Unknown"
                    val date = document.getString("date") ?: "Unknown"
                    val startTime = document.getString("startTime") ?: "Unknown"
                    val endTime = document.getString("endTime") ?: "Unknown"
                    val overallScore = document.getLong("OverAllScoreOfTrip") ?: 0
                    val emergencyBraking = document.getLong("emergencyBraking") ?: 0
                    val hardAcceleration = document.getLong("hardAcceleration") ?: 0
                    val speeding = document.getLong("speeding") ?: 0
                    val batteryAlerts = document.getLong("batteryAlerts") ?: 0
                    val fuelAlerts = document.getLong("fuelAlerts") ?: 0
                    val averageSpeed = document.getLong("averageSpeed") ?: 0
                    val averageRpm = document.getLong("averageRpm") ?: 0
                    val highestSpeed = document.getLong("highestSpeed") ?: 0
                    val lowestSpeed = document.getLong("lowestSpeed") ?: 0
                    val totalFuelUsed = document.getDouble("totalFuelUsed") ?: 0.0
                    val drowsy_count = document.getLong("drowsy_count") ?: 0
                    val yanwing_count = document.getLong("yanwing_count") ?: 0
                    val suggestions_message = document.getString("suggestions_message") ?: "Unknown"
                    val feedback_message =  document.getString("feedback_message") ?: "Unknown"

                    val durationTextView: TextView = findViewById(R.id.trip_duration)
                    val dateTextView: TextView = findViewById(R.id.trip_date)
                    val tripTimeText: TextView = findViewById(R.id.trip_times)
                    val tripScoreText: TextView = findViewById(R.id.overall_score_text)
                    val emergency_brackingTextView: TextView = findViewById(R.id.emergency_braking_count)
                    val hard_accelerationTextView: TextView = findViewById(R.id.hard_acceleration_count)
                    val speedingTextView: TextView = findViewById(R.id.speeding_count)
                    val batteryalertTextView: TextView = findViewById(R.id.battery_alert_count)
                    val fuelalertTextView: TextView = findViewById(R.id.fuel_alert_count)
                    val averagespeedTextView: TextView = findViewById(R.id.average_speed)
                    val averagerpmTextView: TextView = findViewById(R.id.average_rpm)
                    val highestspeedTextView:TextView= findViewById(R.id.highest_speed)
                    val lowestspeedTextView:TextView= findViewById(R.id.lowest_speed)
                    val fuelTextView:TextView= findViewById(R.id.fuel_used)
                    val triptitleTextView: TextView = findViewById(R.id.trip_title)
                    val drowsy_countTextView: TextView = findViewById(R.id.drowsy_count)
                    val yanwing_countTextView: TextView = findViewById(R.id.yawning_count)
                    val suggestions_messageTextView: TextView = findViewById(R.id.suggestions_message)
                    val feedback_messageTextView: TextView = findViewById(R.id.feedback_message)

                    // Set the data to the respective TextViews
                    durationTextView.text = "Duration: $duration"
                    dateTextView.text = date
                    tripTimeText.text = "Start: $startTime| End: $endTime"
                    tripScoreText.text = "$overallScore %"
                    emergency_brackingTextView.text = "Emergency Braking: $emergencyBraking"
                    hard_accelerationTextView.text = "Hard Acceleration: $hardAcceleration"
                    speedingTextView.text = "Speeding: $speeding"
                    batteryalertTextView.text = "Battery Alerts: $batteryAlerts"
                    fuelalertTextView.text = "Fuel Alerts: $fuelAlerts"
                    triptitleTextView.text = "$tripId"
                    drowsy_countTextView.text = "Drowsy: $drowsy_count"
                    yanwing_countTextView.text = "Yawning: $yanwing_count"

                    averagespeedTextView.text = "Average Speed: $averageSpeed km/h"
                    averagerpmTextView.text = "Average RPM: $averageRpm RPM"
                    highestspeedTextView.text = "Highest Speed: $highestSpeed km/h"
                    lowestspeedTextView.text = "Lowest Speed: $lowestSpeed km/h"
                    fuelTextView.text = "Fuel Used: $totalFuelUsed ml"
                    suggestions_messageTextView.text = "$suggestions_message"
                    feedback_messageTextView.text = "$feedback_message"
                    setScoreBackgroundColor(tripScoreText, overallScore)



                } else {
                    Toast.makeText(this, "Trip not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setScoreBackgroundColor(textView: TextView, score: Long) {
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