package com.example.drivetrak

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.tasks.Tasks
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
                "Worst to Best Score" -> loadAndSortTrips("bestToWorst")
                "Best to Worst Score" -> loadAndSortTrips("worstToBest")
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
                        val tripFetchTasks = tripIds.map { tripId ->
                            firestore.collection("tripReports").document(tripId).get()
                        }

                        Tasks.whenAllSuccess<DocumentSnapshot>(tripFetchTasks)
                            .addOnSuccessListener { results ->
                                val tripDocuments = results.filterIsInstance<DocumentSnapshot>()
                                val sortedTrips = when (sortOption) {
                                    "newToOld" -> tripDocuments.sortedWith(
                                        compareByDescending<DocumentSnapshot> { it.getString("date") }
                                            .thenByDescending { it.getString("startTime") }
                                    )
                                    "oldToNew" -> tripDocuments.sortedWith(
                                        compareBy<DocumentSnapshot> { it.getString("date") }
                                            .thenBy { it.getString("startTime") }
                                    )
                                    "worstToBest" -> tripDocuments.sortedByDescending {
                                        (it.get("OverAllScoreOfTrip") as? Number)?.toDouble() ?: 0.0
                                    }
                                    "bestToWorst" -> tripDocuments.sortedBy {
                                        (it.get("OverAllScoreOfTrip") as? Number)?.toDouble() ?: 0.0
                                    }
                                    else -> tripDocuments
                                }

                                Log.d("SortedTrips", "Sort Option: $sortOption, Sorted Trip IDs: ${sortedTrips.map { it.id }}")

                                for (trip in sortedTrips) {
                                    loadTripLast(trip)
                                }
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Error fetching trips: ${e.message}", Toast.LENGTH_SHORT).show()
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

    private fun loadTripLast(trip: DocumentSnapshot) {
        val duration = trip.getString("totalTime") ?: "Unknown"
        val date = trip.getString("date") ?: "Unknown"
        val scoreTrip = (trip.get("OverAllScoreOfTrip") as? Number)?.toLong() ?: 0L
        val tripId = trip.id

        val tripView = layoutInflater.inflate(R.layout.trip_item, trips_container, false)
        val tripNumber = tripView.findViewById<TextView>(R.id.tripID)
        val durationTextView: TextView = tripView.findViewById(R.id.route_duration)
        val dateTextView: TextView = tripView.findViewById(R.id.date_of_route)
        val scoretext: TextView = tripView.findViewById(R.id.score_text)

        durationTextView.text = duration
        dateTextView.text = date
        scoretext.text = "$scoreTrip%"
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
