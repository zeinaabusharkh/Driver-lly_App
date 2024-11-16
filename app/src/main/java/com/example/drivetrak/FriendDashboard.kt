package com.example.drivetrak

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import android.util.Log

class FriendDashboard : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var preformance_container: LinearLayout // Note the typo to match XML

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_friend_dashboard)

        // Initialize Firebase Auth and Firestore
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Retrieve Friend ID from Intent
        val friendId = intent.getStringExtra("FRIEND_ID")
        if (friendId.isNullOrEmpty()) {
            Toast.makeText(this, "Friend ID not found.", Toast.LENGTH_SHORT).show()
            Log.e("FriendDashboard", "Friend ID is null or empty.")
            finish()
            return
        }

        // Initialize UI Components
        val profileImageView: ImageView = findViewById(R.id.profile_image)
        val profileNameTextView: TextView = findViewById(R.id.profile_name)
        val profileLocationTextView: TextView = findViewById(R.id.profile_location)
        val unfollowButton: Button = findViewById(R.id.unfollow_button)
        val score_text: TextView = findViewById(R.id.score_text)
        preformance_container = findViewById(R.id.preformance_container) // Match XML ID

        // Set OnClickListener for Unfollow Button
        unfollowButton.setOnClickListener {
            removeFriend(friendId)
        }

        // Optional: Show a loading indicator while fetching data
        // Ensure that your activity_friend_dashboard.xml includes a ProgressBar with id 'loading_indicator'
        val loadingIndicator: ProgressBar? = try {
            findViewById(R.id.loading_indicator)
        } catch (e: Exception) {
            null
        }
        loadingIndicator?.visibility = View.VISIBLE

        // Fetch Friend's User Data from Firestore
        firestore.collection("users").document(friendId).get()
            .addOnSuccessListener { document ->
                loadingIndicator?.visibility = View.GONE
                if (document.exists()) {
                    // Extract User Data
                    val username = document.getString("username") ?: "Unknown"
                    val location = document.getString("profileLocation") ?: "Unknown"
                    val profileImageUrl = document.getString("profileImageUrl")
                    val score = document.getLong("score")
                    val vin = document.getString("vehicle")

                    Log.d("FriendDashboard", "Fetched user data for friendId: $friendId")

                    // Update UI with User Data
                    profileNameTextView.text = username
                    profileLocationTextView.text = location

                    if (score != null) {
                        score_text.text = "$score%"
                        setScoreBackgroundColor(score_text, score)
                        loadPerformanceAlerts(vin ?: "")
                    } else {
                        score_text.text = "0%"
                        setScoreBackgroundColor(score_text, 0)
                        findViewById<TextView>(R.id.no_trips_message)?.visibility = View.VISIBLE
                        Log.d("FriendDashboard", "Score is null for friendId: $friendId")
                    }

                    // Load Profile Image using Glide
                    if (!profileImageUrl.isNullOrEmpty()) {
                        Glide.with(this)
                            .load(profileImageUrl)
                            .transform(CircleCrop())
                            .into(profileImageView)
                        Log.d("FriendDashboard", "Loaded profile image from URL: $profileImageUrl")
                    } else {
                        Log.d("FriendDashboard", "No profile image URL provided for friendId: $friendId")
                    }
                } else {
                    Toast.makeText(this, "User does not exist.", Toast.LENGTH_SHORT).show()
                    Log.e("FriendDashboard", "User document does not exist for friendId: $friendId")
                    finish()
                }
            }
            .addOnFailureListener { e ->
                loadingIndicator?.visibility = View.GONE
                Toast.makeText(this, "Error fetching user data: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("FriendDashboard", "Error fetching user data for friendId: $friendId", e)
            }
    }

    /**
     * Removes a friend from the current user's friends list.
     */
    private fun removeFriend(friendId: String) {
        val currentUser = auth.currentUser
        currentUser?.let {
            val userRef = firestore.collection("users").document(it.uid)
            userRef.update("friends", FieldValue.arrayRemove(friendId))
                .addOnSuccessListener {
                    Toast.makeText(this, "Unfollowed successfully.", Toast.LENGTH_SHORT).show()
                    // Navigate back to the community screen
                    val intent = Intent(this, Community::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error unfollowing: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("FriendDashboard", "Error removing friendId: $friendId", e)
                }
        } ?: run {
            Toast.makeText(this, "User not authenticated.", Toast.LENGTH_SHORT).show()
            Log.e("FriendDashboard", "Attempted to remove friend while not authenticated.")
        }
    }

    /**
     * Sets the background color of the score TextView based on the score value.
     */
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

    /**
     * Loads and aggregates performance alerts based on the VIN ID.
     */
    private fun loadPerformanceAlerts(vinID: String) {
        if (vinID.isEmpty()) {
            Toast.makeText(this, "VIN ID is empty.", Toast.LENGTH_SHORT).show()
            Log.e("FriendDashboard", "VIN ID is empty.")
            return
        }

        // Retrieve the user's trips from Firestore
        firestore.collection("tripReports")
            .whereEqualTo("vin", vinID)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    Log.d("PerformanceAlerts", "No trip reports found for VIN: $vinID")
                    Toast.makeText(this, "No trip reports available for this user.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                Log.d("PerformanceAlerts", "Fetched ${result.size()} trip reports for VIN: $vinID")

                // Initialize Aggregation Variables
                var totalBraking = 0
                var totalSpeeding = 0
                var totalDrowsiness = 0
                var totalHardAcceleration = 0
                var totalYawning = 0
                var fuelAlerts = 0
                var batteryAlerts = 0
                var totalTrips = 0
                var totalDrivingTimeMillis = 0L // Keep this as Long

                // Loop through all trips and aggregate the alerts
                for (document in result) {
                    val alerts = document.get("alerts") as? Map<String, Number> ?: emptyMap()
                    Log.d("PerformanceAlerts", "Processing tripId: ${document.id}, Alerts: $alerts")
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
                    val drivingTimeMillis = convertTimeToMillis(totalTime)
                    Log.d("PerformanceAlerts", "TripId: ${document.id}, TotalTime: $totalTime, DrivingTimeMillis: $drivingTimeMillis")
                    totalDrivingTimeMillis += drivingTimeMillis
                }

                // Convert total time to formatted string before updating UI
                val totalDrivingTimeFormatted = convertMillisToHours(totalDrivingTimeMillis)

                Log.d("PerformanceAlerts", "Aggregated Data - Braking: $totalBraking, Speeding: $totalSpeeding, " +
                        "Drowsiness: $totalDrowsiness, Hard Acceleration: $totalHardAcceleration, Yawning: $totalYawning, " +
                        "Fuel Alerts: $fuelAlerts, Battery Alerts: $batteryAlerts, Total Trips: $totalTrips, " +
                        "Total Driving Time (Formatted): $totalDrivingTimeFormatted")

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
                Toast.makeText(this, "Error loading alerts: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("PerformanceAlerts", "Error fetching trip reports for VIN: $vinID", e)
            }
    }


    // Convert time in "mm:ss" format to milliseconds
    private fun convertTimeToMillis(time: String): Long {
        Log.d("Time", time)
        val timeParts = time.split(":")
        return if (timeParts.size == 2) {
            val minutes = timeParts[0].toLongOrNull() ?: 0
            val seconds = timeParts[1].toLongOrNull() ?: 0
            Log.d("Time", "$minutes minutes and $seconds seconds")
            // Convert total time to milliseconds
            (minutes * 60 * 1000) + (seconds * 1000)
        } else {
            0L
        }
    }

    // Convert total time in milliseconds to hours:minutes format
    private fun convertMillisToHours(milliseconds: Long): String {
        val hours = milliseconds / (1000 * 60 * 60)
        val minutes = (milliseconds % (1000 * 60 * 60)) / (1000 * 60)
        Log.d("Time", "$hours hours and $minutes minutes")
        return String.format("%02d:%02d", hours, minutes)  // Return in "HH:mm" format
    }

    /**
     * Updates the performance report UI with aggregated data.
     */
    private fun updatePerformanceReport(
        totalBraking: Int,
        totalSpeeding: Int,
        totalDrowsiness: Int,
        totalHardAcceleration: Int,
        totalYawning: Int,
        fuelAlerts: Int,
        batteryAlerts: Int,
        totalTrips: Int,
        totalDrivingTimeFormatted: String
    ) {
        Log.d("PerformanceReport", "Updating performance report with the following data:")
        Log.d("PerformanceReport", "Braking: $totalBraking, Speeding: $totalSpeeding, Drowsiness: $totalDrowsiness, " +
                "Hard Acceleration: $totalHardAcceleration, Yawning: $totalYawning, Fuel Alerts: $fuelAlerts, " +
                "Battery Alerts: $batteryAlerts, Total Trips: $totalTrips, Total Driving Time: $totalDrivingTimeFormatted")

        preformance_container.removeAllViews() // Clear existing views

        if (totalTrips == 0) {
            // If there are no trips, display a message
            val noDataView = layoutInflater.inflate(R.layout.no_performance_data, preformance_container, false)
            preformance_container.addView(noDataView)
            Log.d("PerformanceReport", "No performance data available.")
            return
        }

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

        Log.d("PerformanceReport", "Progress bars updated.")

        // Update the text views
        performanceReportView.findViewById<TextView>(R.id.battery_alert_count).text = "Battery Alerts: $batteryAlerts"
        performanceReportView.findViewById<TextView>(R.id.Drowsiness_label).text = "Drowsiness: $totalDrowsiness"
        performanceReportView.findViewById<TextView>(R.id.emergency_braking_count).text = "Emergency Braking: $totalBraking"
        performanceReportView.findViewById<TextView>(R.id.Fuel_label).text = "Low Fuel Level: $fuelAlerts"
        performanceReportView.findViewById<TextView>(R.id.hard_acceleration_count).text = "Hard Acceleration: $totalHardAcceleration"
        performanceReportView.findViewById<TextView>(R.id.Speeding_label).text = "Speeding: $totalSpeeding"
        performanceReportView.findViewById<TextView>(R.id.Yawning_label).text = "Yawning: $totalYawning"
        performanceReportView.findViewById<TextView>(R.id.total_trips).text = "$totalTrips"
        performanceReportView.findViewById<TextView>(R.id.total_hours).text = "$totalDrivingTimeFormatted"

        Log.d("PerformanceReport", "Text views updated.")

        // Add the inflated view to the container
        preformance_container.addView(performanceReportView)
        Log.d("PerformanceReport", "Performance report view added to container.")
    }
}
