package com.example.drivetrak

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

class ViewAll : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var trips_container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_all)
        val sortButton = findViewById<TextView>(R.id.view_all)
        // Set up click listener to show sorting options
        sortButton.setOnClickListener {
            showSortOptions()
        }


        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        trips_container = findViewById(R.id.trips_container)
        loadAndSortTrips("newToOld")

    }

    private fun showSortOptions() {
        val sortButton = findViewById<TextView>(R.id.view_all)
        val popupMenu = PopupMenu(this, sortButton)
        popupMenu.menu.add("New to Old")
        popupMenu.menu.add("Old to New")
        popupMenu.menu.add("Worst to Best Score")
        popupMenu.menu.add("Best to Worst Score")

        popupMenu.setOnMenuItemClickListener { item ->
            when (item.title) {
                "New to Old" -> loadAndSortTrips("newToOld")
                "Old to New" -> loadAndSortTrips("oldToNew")
                "Worst to Best Score" -> loadAndSortTrips("worstToBest")
                "Best to Worst Score" -> loadAndSortTrips("bestToWorst")
            }
            true
        }
        popupMenu.show()
    }
    private fun loadAndSortTrips(sortOption: String) {
        val currentUser = auth.currentUser
        currentUser?.let {
            firestore.collection("users").document(it.uid).get()
                .addOnSuccessListener { document ->
                    val tripIds = document.get("tripIds") as? List<String> ?: emptyList()
                    trips_container.removeAllViews()
                    if (tripIds.isNotEmpty()) {
                        val tripDocuments = mutableListOf<DocumentSnapshot>()

                        for (tripId in tripIds) {
                            firestore.collection("tripReports").document(tripId).get()
                                .addOnSuccessListener { tripDocument ->
                                    tripDocuments.add(tripDocument)
                                    if (tripDocuments.size == tripIds.size) {
                                        val sortedTrips = when (sortOption) {
                                            "newToOld" -> tripDocuments.sortedWith(compareByDescending<DocumentSnapshot> {
                                                it.get("date") as? String
                                            }.thenByDescending {
                                                it.get("startTime") as? String
                                            })
                                            "oldToNew" -> tripDocuments.sortedWith(compareBy<DocumentSnapshot> {
                                                it.get("date") as? String
                                            }.thenBy {
                                                it.get("startTime") as? String
                                            })
                                            "worstToBest" -> tripDocuments.sortedByDescending { it.get("OverAllScoreOfTrip") as? Long }
                                            "bestToWorst" -> tripDocuments.sortedBy { it.get("OverAllScoreOfTrip") as? Long }
                                            else -> tripDocuments
                                        }

                                        for (trip in sortedTrips) {
                                            loadTriplast(trip.id)
                                        }
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(this, "Error loading trip: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                    } else {
                        findViewById<TextView>(R.id.no_trips_message_2).visibility = View.VISIBLE
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }



    private fun loadTriplast(tripId: String) {
        firestore.collection("tripReports").document(tripId).get()
            .addOnSuccessListener { document ->
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

                // Set the onClickListener inside the addOnSuccessListener, after the data is loaded
                tripView.setOnClickListener {
                    val intent = Intent(this, TripDetail::class.java)
                    intent.putExtra("tripId", tripId)  // Use the tripId from the document here
                    startActivity(intent)
                }
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