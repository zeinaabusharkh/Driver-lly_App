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
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.example.drivetrak.Community.Companion.REQUEST_CODE_INVITE
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class Dashboard : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var trips_container: LinearLayout
    private var tripListener: ListenerRegistration? = null
    private var userListener: ListenerRegistration? = null
    private lateinit var viewAll: TextView
    private lateinit var communityButton: Button
    private lateinit var usernameTextView: TextView
    private lateinit var profileImageView: ImageView
    private lateinit var profile_location: TextView
    private lateinit var logout_button: Button
    private lateinit var settingsButton: Button
    private lateinit var score_text: TextView
    private lateinit var leadershipButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_dashboard)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        val currentUser = auth.currentUser

        if (currentUser == null) {
            startActivity(Intent(this, Login::class.java))
            finish()
        } else {
            trips_container = findViewById(R.id.trips_container)
            viewAll = findViewById(R.id.view_all)
            communityButton = findViewById(R.id.community_button)
            usernameTextView = findViewById(R.id.usernameTextView)
            profileImageView = findViewById(R.id.profileImageView)
            profile_location = findViewById(R.id.profile_location)
            logout_button = findViewById(R.id.logout_button)
            settingsButton = findViewById(R.id.settings_button)
            leadershipButton = findViewById(R.id.leadership_button)
            score_text = findViewById(R.id.score_text)


            // Clear the trips container
            trips_container.removeAllViews()

            communityButton.setOnClickListener {
                startActivity(Intent(this, Community::class.java))
            }
            viewAll.setOnClickListener {
                startActivity(Intent(this, ViewAll::class.java))
            }
            settingsButton.setOnClickListener {
                startActivity(Intent(this, Settings::class.java))
            }
            leadershipButton.setOnClickListener {
                startActivity(Intent(this, Leadership::class.java))
            }
            logout_button.setOnClickListener {
                auth.signOut()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            loadUser()

            // Listen for real-time updates to trips
            tripListener = firestore.collection("trips")
                .addSnapshotListener { snapshots, e ->
                    if (e != null) {
                        Toast.makeText(this, "Listen failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        return@addSnapshotListener
                    }
                    loadTrip() // Refresh the trips list
                }

            // Listen for changes in tripIds field
            userListener = firestore.collection("users").document(currentUser.uid)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        Toast.makeText(this, "Listen failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        return@addSnapshotListener
                    }

                    if (snapshot != null && snapshot.exists()) {
                        loadUser()
                        loadTrip() // Refresh the trips list
                    }
                }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tripListener?.remove() // Remove the listener when the activity is destroyed
        userListener?.remove() // Remove the user listener when the activity is destroyed
    }

    private fun loadUser() {
        val currentUser = auth.currentUser
        currentUser?.let {
            // Retrieve user data from Firestore
            firestore.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { document ->
                    if (document != null) {
                        val username = document.getString("username") ?: "Unknown"
                        val profileImageUrl = document.getString("profileImageUrl")
                        val profileLocation = document.getString("profileLocation") ?: "Unknown"
                        val score = document.getLong("score") ?: 0L
                        val vin = document.getString("vehicle") ?: "Unknown"

                        usernameTextView.text = username
                        profile_location.text = profileLocation
                        score_text.text = "$score%"
                        setScoreBackgroundColor(score_text, score)

                        if (!profileImageUrl.isNullOrEmpty()) {
                            Glide.with(this)
                                .load(profileImageUrl)
                                .transform(CircleCrop())
                                .into(profileImageView)
                        }
                        loadPerformanceAlerts(vin)

                    } else {
                        // Handle the case where the document does not exist
                        usernameTextView.text = "No username found"
                        profile_location.text = "Unknown"
                        score_text.text = "0%"
                        setScoreBackgroundColor(score_text, 0)
                    }
                }
                .addOnFailureListener { e ->
                    // Handle the error
                    usernameTextView.text = "Error: ${e.message}"
                    profile_location.text = "Unknown"
                    score_text.text = "0%"
                    setScoreBackgroundColor(score_text, 0)
                }


            // create a function that reads all the alerts from the tripsReport collection to geenrate a overall performance report and show each alert count in a progress bar




        }
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
                Toast.makeText(this, "Error loading alerts: ${e.message}", Toast.LENGTH_SHORT).show()
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
        totalDrivingTimeMillis: String // Now this is a String
    ) {
        // Get the progress bars from the layout
        val accelerationProgressBar: ProgressBar = findViewById(R.id.acceleration_progress)
        val brakingProgressBar: ProgressBar = findViewById(R.id.emergency_progress)
        val speedingProgressBar: ProgressBar = findViewById(R.id.Speeding_progress)
        val drowsinessProgressBar: ProgressBar = findViewById(R.id.Drowsiness_progress)
        val yawningProgressBar: ProgressBar = findViewById(R.id.Yawning_progress)
        val batteryAlertProgressBar: ProgressBar = findViewById(R.id.battery_alert_count_progress)
        val fuelAlertProgressBar: ProgressBar = findViewById(R.id.fuel_progress)

        // Extract values from the 'alerts' map and update progress bars
        batteryAlertProgressBar.progress = batteryAlerts * 10
        drowsinessProgressBar.progress = totalDrowsiness * 10
        brakingProgressBar.progress = totalBraking * 10
        fuelAlertProgressBar.progress = fuelAlerts * 10
        accelerationProgressBar.progress = totalHardAcceleration * 10
        speedingProgressBar.progress = totalSpeeding * 10
        yawningProgressBar.progress = totalYawning * 10

        // Update alert count text views
        findViewById<TextView>(R.id.battery_alert_count).text = "Battery Alerts: $batteryAlerts"
        findViewById<TextView>(R.id.Drowsiness_label).text = "Drowsy Alerts: $totalDrowsiness"
        findViewById<TextView>(R.id.emergency_braking_count).text = "Emergency Braking Alerts: $totalBraking"
        findViewById<TextView>(R.id.Fuel_label).text = "Low Fuel Level Alerts: $fuelAlerts"
        findViewById<TextView>(R.id.hard_acceleration_count).text = "Hard Acceleration Alerts: $totalHardAcceleration"
        findViewById<TextView>(R.id.Speeding_label).text = "Speeding Alerts: $totalSpeeding"
        findViewById<TextView>(R.id.Yawning_label).text = "Yawning Alerts: $totalYawning"
        findViewById<TextView>(R.id.total_trips).text = "$totalTrips"
        findViewById<TextView>(R.id.total_hours).text = "$totalDrivingTimeMillis"
    }



    private fun loadTrip() {
        trips_container.removeAllViews()
        val currentUser = auth.currentUser
        currentUser?.let {
            firestore.collection("users").document(it.uid).get()
                .addOnSuccessListener { document ->
                    val tripIds = document.get("tripIds") as? List<String> ?: emptyList()
                    trips_container.removeAllViews() // Clear existing views
                    if (tripIds.isNotEmpty()) {
                        // Get the most recent trip ID (last entry in tripIds)
                        val mostRecentTripId = tripIds.last()

                        // Retrieve and load only the most recent trip document
                        firestore.collection("tripReports").document(mostRecentTripId).get()
                            .addOnSuccessListener { tripDocument ->
                                loadTriplast(tripDocument)
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Error loading trip: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        findViewById<TextView>(R.id.no_trips_message).visibility = View.VISIBLE
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }


    private fun loadTriplast(document: DocumentSnapshot) {
        trips_container.removeAllViews()
        val duration = document.getString("totalTime") ?: "Unknown"
        val date = document.getString("date") ?: "Unknown"
        val scoreTrip = document.getLong("OverAllScoreOfTrip")?.toLong() ?: 0L
        val tripId = document.id

        val tripView = layoutInflater.inflate(R.layout.trip_item, trips_container, false)
        val tripNumber = tripView.findViewById<TextView>(R.id.tripID)
        val durationTextView: TextView = tripView.findViewById(R.id.route_duration)
        val dateTextView: TextView = tripView.findViewById(R.id.date_of_route)
        val scoretext: TextView = tripView.findViewById(R.id.score_text)


        durationTextView.text = duration
        dateTextView.text = date
        scoretext.text = scoreTrip.toString()
        setScoreBackgroundColor(scoretext, scoreTrip)
        tripNumber.text = tripId
        trips_container.addView(tripView)

        tripView.setOnClickListener {
            val intent = Intent(this, TripDetail::class.java)
            intent.putExtra("tripId", tripId)
            startActivity(intent)
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