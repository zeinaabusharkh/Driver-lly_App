package com.example.drivetrak

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
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