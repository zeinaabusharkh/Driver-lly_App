package com.example.drivetrak

import android.content.Context
import android.os.Build
import androidx.work.Worker
import androidx.work.WorkerParameters
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit




class CalculationWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {
    private val firestore = FirebaseFirestore.getInstance()
    private val database = FirebaseDatabase.getInstance("https://drive-trak-default-rtdb.firebaseio.com")
    private val dbRef = database.reference

    override fun doWork(): Result {

        processTripData("TRIP000029")

        // Return the result
        return Result.success()
    }




    fun processTripData(tripId: String) {
        val tripRef = dbRef.child("trips_all").child(tripId)
        // to save in the main extented trip data with each second entry
        val firestoreTripDoc = firestore.collection("trips").document(tripId)
        // to save in the main trip report data with the summary of the trip
        val firestoreTripReportDoc = firestore.collection("tripReports").document(tripId)
        //drowsy/yawning detection - database RAW data
        val firestoreDrowsyDoc = dbRef.child("drowsiness_totals")
        // user id's to get the user vin
        val users = firestore.collection("users")




        tripRef.addListenerForSingleValueEvent(object : ValueEventListener {
            @RequiresApi(Build.VERSION_CODES.O)
            override fun onDataChange(snapshot: DataSnapshot) {
                val tripEntries = mutableListOf<Trip>()
                val rawTripEntries = mutableListOf<Map<String, Any?>>()

                // Statistics tracking
                var totalRpm = 0
                var totalSpeed = 0
                var highestSpeed = Int.MIN_VALUE
                var lowestSpeed = Int.MAX_VALUE
                var totalFuelUsed = 0.0
                var firstTimestamp: Long? = null
                var lastTimestamp: Long? = null
                var drowsinessCount = 0
                var yawningCount = 0

                for (record in snapshot.children) {
                    val dataMap = record.value as? Map<*, *> ?: continue

                    // Parse timestamp handling both string and long formats
                    val timestamp = when (val rawTimestamp = dataMap["timeStamp"]) {
                        is String -> try {
                            LocalDateTime.parse(rawTimestamp)
                                .atZone(ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()
                        } catch (e: Exception) {
                            Log.e("TripData", "Error parsing timestamp string: $rawTimestamp", e)
                            continue
                        }
                        is Long -> rawTimestamp
                        else -> continue
                    }

                    // Update timestamp tracking
                    if (firstTimestamp == null || timestamp < firstTimestamp) {
                        firstTimestamp = timestamp
                    }
                    if (lastTimestamp == null || timestamp > lastTimestamp) {
                        lastTimestamp = timestamp
                    }

                    // Parse alerts
                    val alertsMap = dataMap["Alerts"] as? Map<*, *>
                    val formattedAlerts = parseAlerts(alertsMap)

                    // Create TripEntry
                    val entry = Trip(
                        batteryLevel = (dataMap["Battery Level"] as? Double) ?: 0.0,
                        deceleration = (dataMap["Deceleration"] as? Long)?.toInt() ?: 0,
                        fuelLevel = (dataMap["Fuel Level"] as? Double) ?: 0.0,
                        rpm = (dataMap["RPM"] as? Long)?.toInt() ?: 0,
                        speed = (dataMap["Speed"] as? Long)?.toInt() ?: 0,
                        carID = dataMap["carID"] as? String ?: "",
                        deviceID = dataMap["deviceID"] as? String ?: "",
                        timeStamp = timestamp,
                        tripID = dataMap["tripID"] as? String ?: "",
                        alerts = formattedAlerts
                    )

                    // Create raw entry for complete data storage
                    val rawEntry = mapOf(
                        "Alerts" to formattedAlerts,
                        "Battery Level" to entry.batteryLevel,
                        "Deceleration" to entry.deceleration,
                        "Fuel Level" to entry.fuelLevel,
                        "RPM" to entry.rpm,
                        "Speed" to entry.speed,
                        "carID" to entry.carID,
                        "deviceID" to entry.deviceID,
                        "timeStamp" to entry.timeStamp,
                        "tripID" to entry.tripID
                    )

                    // Update statistics
                    totalRpm += entry.rpm
                    totalSpeed += entry.speed
                    totalFuelUsed += entry.fuelLevel
                    if (entry.speed > highestSpeed) highestSpeed = entry.speed
                    if (entry.speed < lowestSpeed) lowestSpeed = entry.speed

                    tripEntries.add(entry)
                    rawTripEntries.add(rawEntry)
                }

                // Calculate timing information
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

                val startDate = firstTimestamp?.let { Date(it) }
                val endDate = lastTimestamp?.let { Date(it) }
                Log.d("TripData", "Start: $startDate, End: $endDate")
                Log.d("startandend:", "Start: $firstTimestamp, End: $lastTimestamp")

                        firestoreDrowsyDoc.addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(drowsySnapshot: DataSnapshot) {
                                // Define the date format for parsing the timestamp string
                                val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.getDefault())
                                dateFormat.timeZone = TimeZone.getTimeZone("UTC") // Set the timezone to UTC if needed

                                // Check each drowsiness timestamp
                                drowsySnapshot.child("drowsiness").children.forEach { alert ->
                                    // Get the timestamp as a String (e.g., "2024-11-09T17:14:45.678958")
                                    val alertTimestampString = alert.getValue(String::class.java)

                                    // Parse the string to a Date object
                                    val alertDate = alertTimestampString?.let {
                                        try {
                                            dateFormat.parse(it)
                                        } catch (e: Exception) {
                                            Log.e("Timestamp", "Error parsing drowsiness timestamp: $alertTimestampString")
                                            null
                                        }
                                    }

                                    // Compare the parsed alert timestamp with the trip start and end timestamps
                                    alertDate?.let { date ->
                                        val alertTimestampMillis = date // Convert Date to Unix timestamp in milliseconds
                                        Log.d("drowsiness", "drowsiness  $alertTimestampMillis")

                                       // if (alertTimestampMillis in firstTimestamp!!..lastTimestamp!!){
                                        if (alertTimestampMillis in startDate!!..endDate!!) {
                                            Log.d("drowsiness", "drowsiness detected $alertTimestampMillis")
                                            drowsinessCount++
                                            Log.d("drowsiness", "drowsiness detected $drowsinessCount")
                                        }
                                    }
                                }

                                // Check each yawning timestamp
                                drowsySnapshot.child("yawning").children.forEach { alert ->
                                    val alertTimestampString = alert.getValue(String::class.java)

                                    // Parse the string to a Date object for yawning
                                    val alertDate = alertTimestampString?.let {
                                        try {
                                            dateFormat.parse(it)
                                        } catch (e: Exception) {
                                            Log.e("Timestamp", "Error parsing yawning timestamp: $alertTimestampString")
                                            null
                                        }
                                    }

                                    // Compare the parsed yawning timestamp with the trip start and end timestamps
                                    alertDate?.let { date ->
                                        val alertTimestampMillis = date // Convert Date to Unix timestamp in milliseconds
                                        Log.d("Yawning", "Yawning  $alertTimestampMillis")

                                        if (alertTimestampMillis in startDate!!..endDate!!) {
                                            Log.d("Yawning", "Yawning detected $alertTimestampMillis")
                                            yawningCount++
                                            Log.d("Yawning", "Yawning count $yawningCount")
                                        }
                                    }
                                }
                            }

                            override fun onCancelled(error: DatabaseError) {
                                Log.e("Firebase", "Error reading data: ${error.message}")
                            }
                        })




                val date = startDate?.let { dateFormat.format(it) } ?: ""
                val startTime = startDate?.let { timeFormat.format(it) } ?: ""
                val endTime = endDate?.let { timeFormat.format(it) } ?: ""

                // Calculate duration
                val durationMillis = (lastTimestamp ?: 0) - (firstTimestamp ?: 0)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis)
                val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis) % 60
                val duration = String.format("%d:%02d", minutes, seconds)

                // Calculate averages
                val avgRpm = if (tripEntries.isNotEmpty()) totalRpm / tripEntries.size else 0
                val avgSpeed = if (tripEntries.isNotEmpty()) totalSpeed / tripEntries.size else 0

                // Calculate trip score
                val score = calculateTripScore(tripEntries.lastOrNull()?.alerts)

                // Get user ID from carID
                users.whereEqualTo("vehicle", tripEntries.firstOrNull()?.carID).get()
                    .addOnSuccessListener { querySnapshot ->
                        val currentUser = querySnapshot.documents.firstOrNull()?.getString("uid")
                        // Use currentUserId as needed in your code
                        // Create reports
                        val tripReport = hashMapOf(
                            "vin" to (tripEntries.firstOrNull()?.carID ?: ""),
                            "tripId" to tripId,
                            "startTime" to startTime,
                            "endTime" to endTime,
                            "totalTime" to duration,
                            "date" to date,
                            "totalFuelUsed" to totalFuelUsed,
                            "averageRpm" to avgRpm,
                            "averageSpeed" to avgSpeed,
                            "highestSpeed" to highestSpeed,
                            "lowestSpeed" to if (lowestSpeed == Int.MAX_VALUE) 0 else lowestSpeed,
                            "OverAllScoreOfTrip" to score,
                            "alerts" to getAlertsSummary(tripEntries.lastOrNull()?.alerts , drowsinessCount , yawningCount)
                        )

                        val tripDocument = hashMapOf(
                            "tripId" to tripId,
                            "entries" to rawTripEntries,
                            "userId" to currentUser,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "metadata" to hashMapOf(
                                "carId" to (tripEntries.firstOrNull()?.carID),
                                "deviceId" to (tripEntries.firstOrNull()?.deviceID),
                                "startTime" to firstTimestamp,
                                "endTime" to lastTimestamp,
                                "entryCount" to tripEntries.size
                            ).filterValues { it != null }
                        )

                        // Save both documents to Firestore
                        saveTripData(tripId, tripDocument, tripReport, currentUser)
                    }
                    .addOnFailureListener { e ->
                        Log.e("TripData", "Error getting user ID: ${e.message}")
                    }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("TripData", "Database error: ${error.message}")
            }
        })
    }

    private fun calculateTripScore(alerts: Alerts?): Double {
        var score = 100.0

        val weights = mapOf(
            "hardAcceleration" to 2.0,
            "speeding" to 3.0,
            "emergencyBraking" to 4.0,
            "fuelAlerts" to 1.0,
            "batteryAlerts" to 1.0,
            "drowsiness" to 5.0,
            "yawning" to 5.0
        )

        val alertCounts = mapOf(
            "hardAcceleration" to (alerts?.hardAcceleration?.count ?: 0),
            "speeding" to (alerts?.speeding?.count ?: 0),
            "emergencyBraking" to (alerts?.emergencyBraking?.count ?: 0),
            "fuelAlerts" to (alerts?.fuelLevel?.count ?: 0),
            "batteryAlerts" to (alerts?.batteryLevel?.count ?: 0),
            "drowsiness" to (alerts?.drowsiness?.count ?: 0),
            "yawning" to (alerts?.yawning?.count ?: 0)

        )

        alertCounts.forEach { (alertType, count) ->
            val weight = weights[alertType] ?: 0.0
            score -= count * weight
        }

        return score.coerceIn(0.0, 100.0)
    }


    // Helper function to parse alerts
    fun parseAlerts(alertsMap: Map<*, *>?): Alerts? {
        return alertsMap?.let { alertData ->
            Alerts(
                batteryLevel = (alertData["Battery Level"] as? Map<*, *>)?.get("count")?.toString()?.toIntOrNull()
                    ?.let { AlertCount(it) },
                emergencyBraking = (alertData["Emergency Braking"] as? Map<*, *>)?.get("count")?.toString()?.toIntOrNull()
                    ?.let { AlertCount(it) },
                fuelLevel = (alertData["Fuel Level"] as? Map<*, *>)?.get("count")?.toString()?.toIntOrNull()
                    ?.let { AlertCount(it) },
                hardAcceleration = (alertData["Hard Acceleration"] as? Map<*, *>)?.get("count")?.toString()?.toIntOrNull()
                    ?.let { AlertCount(it) },
                speeding = (alertData["Speeding"] as? Map<*, *>)?.get("count")?.toString()?.toIntOrNull()
                    ?.let { AlertCount(it) }
            )
        }
    }

    private fun getAlertsSummary(alerts: Alerts?, drowInt: Int , yawnInt:Int): Map<String, Int> {
        // Initialize the base alert counts from the existing Alerts object
        val alertsSummary = mutableMapOf<String, Int>(
            "hardAcceleration" to (alerts?.hardAcceleration?.count ?: 0),
            "speeding" to (alerts?.speeding?.count ?: 0),
            "emergencyBraking" to (alerts?.emergencyBraking?.count ?: 0),
            "fuelAlerts" to (alerts?.fuelLevel?.count ?: 0),
            "batteryAlerts" to (alerts?.batteryLevel?.count ?: 0)
        )

        // Add the drowsiness and yawning counts to the summary
        alertsSummary["drowsiness"] = alertsSummary.getOrDefault("drowsiness", 0) + drowInt
        alertsSummary["yawning"] = alertsSummary.getOrDefault("yawning", 0) + yawnInt

        return alertsSummary
    }


    private fun saveTripData(
        tripId: String,
        tripDocument: HashMap<String, Any?>,
        tripReport: HashMap<String, Any>,
        userId: String?
    ) {
        val firestoreTripDoc = firestore.collection("trips").document(tripId)
        val firestoreTripReportDoc = firestore.collection("tripReports").document(tripId)

        // Save complete trip data
        firestoreTripDoc.set(tripDocument, SetOptions.merge())
            .addOnSuccessListener {
                Log.d("TripData", "Trip saved successfully in Firestore")
                // Link trip to user
                userId?.let { uid ->
                    firestore.collection("users").document(uid)
                        .update("tripIds", FieldValue.arrayUnion(tripId))
                        .addOnSuccessListener {
                            Log.d("TripData", "Trip linked to user successfully")
                        }
                        .addOnFailureListener { e ->
                            Log.e("TripData", "Error linking trip to user: ${e.message}")
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("TripData", "Error saving trip data: ${e.message}")
            }

        // Save trip report
        firestoreTripReportDoc.set(tripReport)
            .addOnSuccessListener {
                Log.d("TripData", "Trip report saved successfully for trip ID: $tripId")
            }
            .addOnFailureListener { e ->
                Log.e("TripData", "Error saving trip report: ${e.message}")
            }
    }







}
