package com.example.drivetrak

data class User(
    val uid: String? = null,
    val username: String? = null,
    val email: String? = null,
    val Vehicle: String? = null,
    var profileImageUrl: String? = null,
    var profileLocation: String? = null,
    var friends: List<String>? = null,
    var Score: Int? = null,
    val tripIds: List<String> = emptyList(),
    val position: Int? = null

)
{constructor(userId: String, username: String, score: Long, position: Int , profileImageUrl: String?) : this(
    uid = userId,
    username = username,
    Score = score.toInt(),
    position = position,
    profileImageUrl = profileImageUrl
)}
