package com.example.drivetrak

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class FriendDashboard : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var preformance_container: LinearLayout


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_friend_dashboard)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        val friendId = intent.getStringExtra("FRIEND_ID") ?: return

        val profileImageView: ImageView = findViewById(R.id.profile_image)
        val profileNameTextView: TextView = findViewById(R.id.profile_name)
        val profileLocationTextView: TextView = findViewById(R.id.profile_location)
        val unfollowButton: Button = findViewById(R.id.unfollow_button)
        val score_text: TextView = findViewById(R.id.score_text)
        preformance_container = findViewById(R.id.preformance_container)


        unfollowButton.setOnClickListener {
            removeFriend(friendId)
        }

        firestore.collection("users").document(friendId).get()
            .addOnSuccessListener { document ->
                val username = document.getString("username") ?: "Unknown"
                val location = document.getString("profileLocation") ?: "Unknown"
                val profileImageUrl = document.getString("profileImageUrl")
                val score = document.getLong("score")
                val vin = document.getString("vin")
                preformance_container.removeAllViews()

                profileNameTextView.text = username
                profileLocationTextView.text = location
                loadPerformanceAlerts(vin ?: "")
                if (score != null){
                    score_text.text = "$score%"
                    setScoreBackgroundColor(score_text, score)
                }
                else
                {
                    score_text.text = "0%"
                    setScoreBackgroundColor(score_text, 0)
                    findViewById<TextView>(R.id.no_trips_message).visibility = View.VISIBLE

                }

                if (!profileImageUrl.isNullOrEmpty()) {
                    Glide.with(this)
                        .load(profileImageUrl)
                        .transform(CircleCrop())
                        .into(profileImageView)
                }
            }
            .addOnFailureListener { e ->
                // Handle error
            }
    }

    private fun removeFriend(friendId: String) {
        val currentUser = auth.currentUser
        currentUser?.let {
            val userRef = firestore.collection("users").document(it.uid)
            userRef.update("friends", FieldValue.arrayRemove(friendId))
                .addOnSuccessListener {
                    // Navigate back to the community screen
                    val intent = Intent(this, Community::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    finish()
                }
                .addOnFailureListener { e ->
                    // Handle error
                }
        }
    }
    private fun setScoreBackgroundColor(textView: TextView, score: Long) {
        val color = when {
            score >= 80 -> android.graphics.Color.GREEN
            score >= 50 -> android.graphics.Color.YELLOW
            else -> android.graphics.Color.RED
        }

        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.OVAL
        drawable.setColor(color)
        textView.background = drawable
    }

    private fun loadPerformanceAlerts(vinID: String) {
        // Retrieve the user's trips from Firestore
        firestore.collection("tripReports")
            .whereEqualTo("vin", vinID)
            .get()
            .addOnSuccessListener { result ->
                var totalBraking = 0
                var totalSpeeding = 0
                var totalDrowsiness = 0
                var totalHardAcceleration = 0
                var totalYawning = 0
                var fuelAlerts = 0
                var batteryAlerts = 0
                var totalTrips = 0
                var totalDrivingTimeMillis = 0L  // Keep this as Long

                // Loop through all trips and aggregate the alerts
                for (document in result) {
                    val alerts = document.get("alerts") as? Map<String, Long> ?: emptyMap()
                    totalBraking += alerts["emergencyBraking"]?.toInt() ?: 0
                    totalSpeeding += alerts["speeding"]?.toInt() ?: 0
                    totalDrowsiness += alerts["drowsiness"]?.toInt() ?: 0
                    totalHardAcceleration += alerts["hardAcceleration"]?.toInt() ?: 0
                    totalYawning += alerts["yawning"]?.toInt() ?: 0
                    fuelAlerts += alerts["fuelAlerts"]?.toInt() ?: 0
                    batteryAlerts += alerts["batteryAlerts"]?.toInt() ?: 0
                    totalTrips++

                    // Calculate and accumulate the total driving time in milliseconds
                    val totalTime = document.getString("totalTime") ?: "0:00"
                    totalDrivingTimeMillis += convertTimeToMillis(totalTime)
                }

                // Convert total time to formatted string before updating UI
                val totalDrivingTimeFormatted = convertMillisToHours(totalDrivingTimeMillis)

                // Now update the progress bars
                updatePerformanceReport(
                    totalBraking,
                    totalSpeeding,
                    totalDrowsiness,
                    totalHardAcceleration,
                    totalYawning,
                    fuelAlerts,
                    batteryAlerts,
                    totalTrips,
                    totalDrivingTimeFormatted
                )
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading alerts: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
    }
    // Convert time in "HH:mm" format to milliseconds
    private fun convertTimeToMillis(time: String): Long {
        val timeParts = time.split(":")
        return if (timeParts.size == 2) {
            val hours = timeParts[0].toLongOrNull() ?: 0
            val minutes = timeParts[1].toLongOrNull() ?: 0
            // Convert total time to milliseconds
            (hours * 60 * 60 * 1000) + (minutes * 60 * 1000)
        } else {
            0L
        }
    }

    // Convert total time in milliseconds to hours
    private fun convertMillisToHours(milliseconds: Long): String {
        val hours = milliseconds / (1000 * 60 * 60)
        val minutes = (milliseconds % (1000 * 60 * 60)) / (1000 * 60)
        return String.format("%02d:%02d", hours, minutes)  // Return in "HH:mm" format
    }

    private fun updatePerformanceReport(
        totalBraking: Int,
        totalSpeeding: Int,
        totalDrowsiness: Int,
        totalHardAcceleration: Int,
        totalYawning: Int,
        fuelAlerts: Int,
        batteryAlerts: Int,
        totalTrips: Int,
        totalDrivingTimeMillis: String
    ) {
        preformance_container.removeAllViews() // Clear existing views
        // Inflate the performance report layout
        val performanceReportView = layoutInflater.inflate(R.layout.performance_report, preformance_container, false)

        // Get the progress bars from the inflated layout
        val accelerationProgressBar: ProgressBar = performanceReportView.findViewById(R.id.acceleration_progress)
        val brakingProgressBar: ProgressBar = performanceReportView.findViewById(R.id.emergency_progress)
        val speedingProgressBar: ProgressBar = performanceReportView.findViewById(R.id.Speeding_progress)
        val drowsinessProgressBar: ProgressBar = performanceReportView.findViewById(R.id.Drowsiness_progress)
        val yawningProgressBar: ProgressBar = performanceReportView.findViewById(R.id.Yawning_progress)
        val batteryAlertProgressBar: ProgressBar = performanceReportView.findViewById(R.id.battery_alert_count_progress)
        val fuelAlertProgressBar: ProgressBar = performanceReportView.findViewById(R.id.fuel_progress)

        // Set progress bar values (multiplying by 10 to fit the 0-100 range for ProgressBar)
        batteryAlertProgressBar.progress = minOf(batteryAlerts * 10, 100)
        drowsinessProgressBar.progress = minOf(totalDrowsiness * 10, 100)
        brakingProgressBar.progress = minOf(totalBraking * 10, 100)
        fuelAlertProgressBar.progress = minOf(fuelAlerts * 10, 100)
        accelerationProgressBar.progress = minOf(totalHardAcceleration * 10, 100)
        speedingProgressBar.progress = minOf(totalSpeeding * 10, 100)
        yawningProgressBar.progress = minOf(totalYawning * 10, 100)

        // Update the text views
        performanceReportView.findViewById<TextView>(R.id.battery_alert_count).text = "Battery Alerts: $batteryAlerts"
        performanceReportView.findViewById<TextView>(R.id.Drowsiness_label).text = "Drowsy Alerts: $totalDrowsiness"
        performanceReportView.findViewById<TextView>(R.id.emergency_braking_count).text = "Emergency Braking Alerts: $totalBraking"
        performanceReportView.findViewById<TextView>(R.id.Fuel_label).text = "Low Fuel Level Alerts: $fuelAlerts"
        performanceReportView.findViewById<TextView>(R.id.hard_acceleration_count).text = "Hard Acceleration Alerts: $totalHardAcceleration"
        performanceReportView.findViewById<TextView>(R.id.Speeding_label).text = "Speeding Alerts: $totalSpeeding"
        performanceReportView.findViewById<TextView>(R.id.Yawning_label).text = "Yawning Alerts: $totalYawning"
        performanceReportView.findViewById<TextView>(R.id.total_trips).text = "$totalTrips"
        performanceReportView.findViewById<TextView>(R.id.total_hours).text = "$totalDrivingTimeMillis"

        // Add the inflated view to the container
        preformance_container.addView(performanceReportView)
    }






}

