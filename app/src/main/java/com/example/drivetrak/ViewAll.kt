package com.example.drivetrak

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ViewAll : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var trips_container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_all)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        trips_container = findViewById(R.id.trips_container)

        loadTrip()
    }

    private fun loadTrip() {
        val currentUser = auth.currentUser
        currentUser?.let {
            firestore.collection("users").document(it.uid).get()
                .addOnSuccessListener { document ->
                    val tripIds = document.get("tripIds") as? List<String> ?: emptyList()
                    trips_container.removeAllViews() // Clear existing views
                    for (tripId in tripIds) {
                        loadTriplast(tripId)
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun loadTriplast(tripId: String) {
        firestore.collection("trips").document(tripId).get()
            .addOnSuccessListener { document ->
                val departure = document.getString("departure") ?: "Unknown"
                val destination = document.getString("destination") ?: "Unknown"
                val duration = document.getString("duration") ?: "Unknown"
                val date = document.getString("date") ?: "Unknown"
                val mapUrl = document.getString("mapUrl") ?: "Unknown"

                val tripView = layoutInflater.inflate(R.layout.trip_item, trips_container, false)
                val departureTextView: TextView = tripView.findViewById(R.id.departure_text)
                val destinationTextView: TextView = tripView.findViewById(R.id.destination_text)
                val durationTextView: TextView = tripView.findViewById(R.id.route_duration)
                val dateTextView: TextView = tripView.findViewById(R.id.date_of_route)

                departureTextView.text = departure
                destinationTextView.text = destination
                durationTextView.text = duration
                dateTextView.text = date
                trips_container.addView(tripView)
            }
        trips_container.setOnClickListener {
            val intent = Intent(this, TripDetail::class.java)
            intent.putExtra("tripId", tripId)
            startActivity(intent)
        }
    }
}