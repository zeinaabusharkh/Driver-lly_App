package com.example.drivetrak

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
        firestore.collection("trips").document(tripId).get()
            .addOnSuccessListener { document ->
                if (document != null) {
                    val departure = document.getString("departure") ?: "Unknown"
                    val destination = document.getString("destination") ?: "Unknown"
                    val duration = document.getString("duration") ?: "Unknown"
                    val date = document.getString("date") ?: "Unknown"
                    val mapUrl = document.getString("mapUrl") ?: "Unknown"

                    val departureTextView: TextView = findViewById(R.id.departure_text)
                    val destinationTextView: TextView = findViewById(R.id.destination_text)
                    val durationTextView: TextView = findViewById(R.id.route_duration)
                    val dateTextView: TextView = findViewById(R.id.date_of_route)
                    val tripMap: ImageView = findViewById(R.id.recent_trip_map)

                    departureTextView.text = departure
                    destinationTextView.text = destination
                    durationTextView.text = duration
                    dateTextView.text = date
                    if (mapUrl.isNotEmpty()) {
                        Glide.with(this).load(mapUrl).into(tripMap)
                    }
                } else {
                    Toast.makeText(this, "Trip not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}