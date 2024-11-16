// Main Worker Class
package com.example.drivetrak

import okhttp3.*
import org.json.JSONObject
import java.io.IOException

import android.content.Context
import android.os.Build
import androidx.work.Worker
import androidx.work.WorkerParameters
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class CalculationWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {
    private val firestore = FirebaseFirestore.getInstance()
    private val database = FirebaseDatabase.getInstance("https://drive-trak-default-rtdb.firebaseio.com")
    private val dbRef = database.reference
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun doWork(): Result {
       //processTripData("TRIP000029")
        processAllTrips()
        processAllUsers()
        return Result.success()
    }


    // calculate each users overall score and save it to user
    private fun processAllUsers() {
        val usersRef = firestore.collection("users")

        usersRef.get().addOnSuccessListener { users ->
            for (user in users) {
                val userTrips = user.get("tripIds") as? List<String> ?: emptyList()
                var totalScore = 0.0
                var tripCount = 0

                for (tripId in userTrips) {
                    val tripRef = firestore.collection("tripReports").document(tripId)

                    tripRef.get().addOnSuccessListener { trip ->
                        val score = trip.getDouble("OverAllScoreOfTrip") ?: 0.0
                        totalScore += score
                        tripCount++

                        if (tripCount == userTrips.size) {
                            val avgScore = totalScore / tripCount
                            // i would like to keep a history of the user score everything saved in a array
                            user.reference.update("score", avgScore)
                        }
                    }
                }
            }
        }
    }


    // need a function that goes through all the trips and processes them
    private fun processAllTrips() {
        // if a trip exists in the database, skip it
        val tripsRef = dbRef.child("trips_all")

        tripsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (trip in snapshot.children) {
                    if (firestore.collection("trips").document(trip.key!!).get().isSuccessful && firestore.collection("tripReports").document(trip.key!!).get().isSuccessful) {
                        continue
                    }else{
                        val tripId = trip.key ?: continue
                        processTripData(tripId)

                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("TripData", "Database error: ${error.message}")
            }
        })
    }

    private suspend fun getDrowsinessData(startDate: Date, endDate: Date): Pair<Int, Int> =
        suspendCoroutine { continuation ->
            val firestoreDrowsyDoc = dbRef.child("drowsiness_totals")

            firestoreDrowsyDoc.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(drowsySnapshot: DataSnapshot) {
                    val (drowsyCount, yawnCount) = countEventsInRange(
                        drowsySnapshot,
                        startDate,
                        endDate
                    )
                    Log.d("DrowsinessData", "Drowsy: $drowsyCount, Yawn: $yawnCount")
                    continuation.resume(Pair(drowsyCount, yawnCount))
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("DrowsinessData", "Error: ${error.message}")
                    continuation.resumeWithException(error.toException())
                }
            })
        }

    private fun processTripData(tripId: String) {
        val tripRef = dbRef.child("trips_all").child(tripId)
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
                var feedbackMessage = ""

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

                    // Create Trip Entry
                    val entry = Trip(
                        batteryLevel = (dataMap["Battery Level"] as? Double) ?: 0.0,
                        deceleration = (dataMap["Deceleration"] as? Number)?.toInt() ?: 0,
                        fuelLevel = (dataMap["Fuel Level"] as? Double) ?: 0.0,
                        rpm = (dataMap["RPM"] as? Number)?.toInt() ?: 0,
                        speed = (dataMap["Speed"] as? Number)?.toInt() ?: 0,
                        carID = dataMap["carID"] as? String ?: "",
                        deviceID = dataMap["deviceID"] as? String ?: "",
                        timeStamp = timestamp,
                        tripID = dataMap["tripID"] as? String ?: "",
                        alerts = formattedAlerts
                    )

                    // Create raw entry
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

                if (startDate != null && endDate != null) {
                    scope.launch {
                        try {
                            // Get drowsiness data
                            val (drowsyCount, yawnCount) = getDrowsinessData(startDate, endDate)

                            // Calculate time-based metrics
                            val date = dateFormat.format(startDate)
                            val startTime = timeFormat.format(startDate)
                            val endTime = timeFormat.format(endDate)
                            val durationMillis = endDate.time - startDate.time
                            val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis)
                            val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis) % 60
                            val duration = String.format("%d:%02d", minutes, seconds)

                            // Calculate averages
                            val avgRpm =
                                if (tripEntries.isNotEmpty()) totalRpm / tripEntries.size else 0
                            val avgSpeed =
                                if (tripEntries.isNotEmpty()) totalSpeed / tripEntries.size else 0

                            // Calculate score with actual drowsiness data
                            val score = calculateTripScore(
                                tripEntries.lastOrNull()?.alerts,
                                drowsyCount,
                                yawnCount
                            )

                            // Get user data
                            val userQuery =
                                users.whereEqualTo("vehicle", tripEntries.firstOrNull()?.carID)
                                    .get().await()
                            val currentUser = userQuery.documents.firstOrNull()?.getString("uid")

                            //geenrate feedback message
                            val apiKey = "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"
                            // Construct the prompt for ChatGPT
                            val alerts_last = tripEntries.lastOrNull()?.alerts

                            val prompt = """
                                    Generate a less than 200  word feedback for a driver based on the following trip data:
                                    Total Fuel Used: $totalFuelUsed ml
                                    Average Speed: $avgSpeed km/h
                                    Highest Speed: $highestSpeed km/h
                                    Average RPM: $avgRpm
                                    Overall Trip Score: $score out of 100
                                    Drowsiness Alerts count: $drowsyCount
                                    Yawning Alerts count: $yawnCount
                                    Total Time: $duration
                                    Start Time: $startTime
                                    End Time: $endTime
                                    Hard Acceleration Alert count: ${alerts_last?.hardAcceleration?.count ?: 0}
                                    Speeding Alert counts: ${alerts_last?.speeding?.count ?: 0}
                                    Emergency Braking Alert count: ${alerts_last?.emergencyBraking?.count ?: 0}
                                    Fuel Alerts count: ${alerts_last?.fuelLevel?.count ?: 0}
                                    Battery Alerts count: ${alerts_last?.batteryLevel?.count ?: 0}
                                    Drowsiness Alert count: $drowsyCount
                                    Yawning Alert count: $yawnCount
                                
                                    Provide useful insights and suggestions for improvement.
                                """.trimIndent()

                            // Define a variable to hold the feedback message
                            var feedbackMessage = ""

// Call getChatGPTResponse and update the variable within the callback
                            getChatGPTResponse(apiKey, prompt) { response ->
                                if (response != null) {
                                        Log.d("ChatGPT response:", response)

                                    feedbackMessage = response.toString()

                                    // Create trip report with the feedback message
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
                                        "FeedbackMessage" to feedbackMessage,
                                        "alerts" to getAlertsSummary(
                                            tripEntries.lastOrNull()?.alerts,
                                            drowsyCount,
                                            yawnCount
                                        )
                                    )
                                    // Create trip document
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


                                    // Save data
                                    saveTripData(tripId, tripDocument, tripReport, currentUser)
                                } else {
                                    Log.e("ChatGPT", "Failed to generate feedback")

                                    // Create trip report
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
                                        "FeedbackMessage" to "Not Found :(",
                                        "alerts" to getAlertsSummary(
                                            tripEntries.lastOrNull()?.alerts,
                                            drowsyCount,
                                            yawnCount
                                        )
                                    )

                                    // Create trip document
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

                                    // Save data
                                    saveTripData(tripId, tripDocument, tripReport, currentUser)


                                }
                            }



                        } catch (e: Exception) {
                            Log.e("TripData", "Error processing trip data: ${e.message}")
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("TripData", "Database error: ${error.message}")
            }
        })
    }

    private fun countEventsInRange(
        drowsySnapshot: DataSnapshot,
        startDate: Date,
        endDate: Date
    ): Pair<Int, Int> {
        fun isTimestampInRange(timestampStr: String): Boolean {
            val timestamp = try {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.getDefault())
                    .parse(timestampStr)
            } catch (e: Exception) {
                null
            }

            return timestamp?.let {
                it.after(startDate) && it.before(endDate) ||
                        it == startDate ||
                        it == endDate
            } ?: false
        }

        var drowsinessCount = 0
        var yawningCount = 0

        // Count drowsiness events
        drowsySnapshot.child("drowsiness").children.forEach {
            if (isTimestampInRange(it.value.toString())) {
                drowsinessCount++
            }
        }

        // Count yawning events
        drowsySnapshot.child("yawning").children.forEach {
            if (isTimestampInRange(it.value.toString())) {
                yawningCount++
            }
        }

        return Pair(drowsinessCount, yawningCount)
    }

    private fun calculateTripScore(alerts: Alerts?, drowInt: Int, yawnInt: Int): Double {
        var score = 100.0

        val weights = mapOf(
           // 10
            "hardAcceleration" to 1.0,
            "speeding" to 1.0,
            "emergencyBraking" to 1.0,
            //5
            "fuelAlerts" to 0.5,
            "batteryAlerts" to 0.5,
           // 15
            "drowsiness" to 1.5,
            "yawning" to 1.5
        )

        Log.d("Alerts", "Alerts: $alerts")
        Log.d("Alerts", "Drowsiness: $drowInt")
        Log.d("Alerts", "Yawning: $yawnInt")

        val alertCounts = mapOf(
            "hardAcceleration" to (alerts?.hardAcceleration?.count ?: 0),
            "speeding" to (alerts?.speeding?.count ?: 0),
            "emergencyBraking" to (alerts?.emergencyBraking?.count ?: 0),
            "fuelAlerts" to (alerts?.fuelLevel?.count ?: 0),
            "batteryAlerts" to (alerts?.batteryLevel?.count ?: 0),
            "drowsiness" to drowInt,
            "yawning" to yawnInt
        )

        alertCounts.forEach { (alertType, count) ->
            Log.d("Alerts", "Alert type and count: $alertType $count")
            val weight = weights[alertType] ?: 0.0
            score -= count * weight
        }

        return score.coerceIn(0.0, 100.0)
    }

    private fun parseAlerts(alertsMap: Map<*, *>?): Alerts? {
        return alertsMap?.let { alertData ->
            Alerts(
                batteryLevel = (alertData["Battery Level"] as? Map<*, *>)?.get("count")?.toString()
                    ?.toIntOrNull()
                    ?.let { AlertCount(it) },
                emergencyBraking = (alertData["Emergency Braking"] as? Map<*, *>)?.get("count")
                    ?.toString()?.toIntOrNull()
                    ?.let { AlertCount(it) },
                fuelLevel = (alertData["Fuel Level"] as? Map<*, *>)?.get("count")?.toString()
                    ?.toIntOrNull()
                    ?.let { AlertCount(it) },
                hardAcceleration = (alertData["Hard Acceleration"] as? Map<*, *>)?.get("count")
                    ?.toString()?.toIntOrNull()
                    ?.let { AlertCount(it) },
                speeding = (alertData["Speeding"] as? Map<*, *>)?.get("count")?.toString()
                    ?.toIntOrNull()
                    ?.let { AlertCount(it) }
            )
        }
    }

    private fun getAlertsSummary(alerts: Alerts?, drowInt: Int, yawnInt: Int): Map<String, Int> {
        // Initialize the base alert counts from the existing Alerts object
        val alertsSummary = mutableMapOf<String, Int>(
            "hardAcceleration" to (alerts?.hardAcceleration?.count ?: 0),
            "speeding" to (alerts?.speeding?.count ?: 0),
            "emergencyBraking" to (alerts?.emergencyBraking?.count ?: 0),
            "fuelAlerts" to (alerts?.fuelLevel?.count ?: 0),
            "batteryAlerts" to (alerts?.batteryLevel?.count ?: 0),
            "drowsiness" to (alerts?.drowsiness?.count ?: 0),
            "yawning" to (alerts?.yawning?.count ?: 0)
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
                callback(null)  // Return null on failure
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e("ChatGPT", "Response not successful: ${response.code}")
                    val responseBody = response.body?.string()
                    Log.e("ChatGPT", "Error Response: $responseBody")
                    callback(null)
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
        })
    }





}
