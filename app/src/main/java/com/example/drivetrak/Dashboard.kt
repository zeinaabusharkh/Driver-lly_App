package com.example.drivetrak

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
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
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

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
    private lateinit var preformance_container: LinearLayout
    private lateinit var ai_container: LinearLayout

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
            preformance_container = findViewById(R.id.preformance_container)
            ai_container = findViewById(R.id.ai_container)
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
            preformance_container.removeAllViews()

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
                        Toast.makeText(this, "Listen failed: ${e.message}", Toast.LENGTH_SHORT)
                            .show()
                        return@addSnapshotListener
                    }
                    loadTrip() // Refresh the trips list
                }

            // Listen for changes in tripIds field
            userListener = firestore.collection("users").document(currentUser.uid)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        Toast.makeText(this, "Listen failed: ${e.message}", Toast.LENGTH_SHORT)
                            .show()
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

        }
    }


    // function to generate AI feedback about the user driving performance


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


                // genertae feedback based on the user driving performance over view
                if(totalTrips > 0) {
                    generateFeedback(
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
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading alerts: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
    }
    private fun generateFeedback(
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

        val apiKey = "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"
        val prompt = """
        Generate a less than 200 word feedback for a driver based on the following trip data:
        total Emergency Braking alert count: $totalBraking 
        total Speeding alert count: $totalSpeeding 
        total Drowsiness alert count: $totalDrowsiness 
        total Hard Acceleration alert count: $totalHardAcceleration
        total Yawning alert count: $totalYawning
        total Low Fuel Alerts count: $fuelAlerts
        Low battery Alerts count: $batteryAlerts
        Total Number of Trips: $totalTrips
        Total time driving: $totalDrivingTimeFormatted

        Provide useful insights and suggestions for improvement.
    """.trimIndent()

        // Call getChatGPTResponse and update the variable within the callback
        getChatGPTResponse(apiKey, prompt) { response ->
            runOnUiThread {
                if (response != null) {
                    Log.d("ChatGPT response:", response)

                    // Inflate the performance feedback view
                    val feedbackView = layoutInflater.inflate(R.layout.ai_feedback, ai_container, false)
                    feedbackView.findViewById<TextView>(R.id.ai_suggestion_text).text = response
                    ai_container.removeAllViews()  // Clear existing views
                    ai_container.addView(feedbackView)  // Add the new feedback view
                } else {
                    Log.d("ChatGPT response:", "Failed to get response")
                    // Make sure to update Toast on the main thread
                    Toast.makeText(this, "Failed to generate feedback", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun getChatGPTResponse(apiKey: String, prompt: String, callback: (String?) -> Unit) {
        val client = OkHttpClient()

        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()

        // Construct the request body
        val requestBody = JSONObject().apply {
            put("model", "gpt-3.5-turbo") // Ensure you're using the correct model
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }.toString().toRequestBody(mediaType)

        // Build the request with Authorization header
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")  // Correct endpoint
            .post(requestBody)
            .addHeader("Authorization", "Bearer $apiKey") // Ensure correct Bearer token format
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                Log.e("ChatGPT", "Request failed: ${e.message}")
                runOnUiThread {
                    callback(null)  // Return null on failure and ensure Toast is shown on the main thread
                    Toast.makeText(applicationContext, "Request failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (!response.isSuccessful) {
                        Log.e("ChatGPT", "Response not successful: ${response.code}")
                        val responseBody = response.body?.string()
                        Log.e("ChatGPT", "Error Response: $responseBody")
                        callback(null)
                        Toast.makeText(applicationContext, "Failed to retrieve response", Toast.LENGTH_SHORT).show()
                    } else {
                        try {
                            val responseBody = response.body?.string()
                            if (responseBody != null) {
                                val jsonResponse = JSONObject(responseBody)
                                val message = jsonResponse.getJSONArray("choices")
                                    .getJSONObject(0)
                                    .getJSONObject("message")
                                    .getString("content")
                                callback(message)
                            } else {
                                Log.e("ChatGPT", "Response body is null")
                                callback(null)
                            }
                        } catch (e: Exception) {
                            Log.e("ChatGPT", "Error parsing response: ${e.message}")
                            callback(null)
                        }
                    }
                }
            }
        })
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