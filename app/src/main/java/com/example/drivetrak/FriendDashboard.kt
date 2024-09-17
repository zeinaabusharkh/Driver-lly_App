package com.example.drivetrak

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class FriendDashboard : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_friend_dashboard)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        val friendId = intent.getStringExtra("FRIEND_ID") ?: return

        val profileImageView: ImageView = findViewById(R.id.profile_image)
        val profileNameTextView: TextView = findViewById(R.id.profile_name)
        val profileLocationTextView: TextView = findViewById(R.id.profile_location)
        val unfollowButton: Button = findViewById(R.id.unfollow_button)
        val score_text: TextView = findViewById(R.id.score_text)

        unfollowButton.setOnClickListener {
            removeFriend(friendId)
        }

        firestore.collection("users").document(friendId).get()
            .addOnSuccessListener { document ->
                val username = document.getString("username") ?: "Unknown"
                val location = document.getString("profileLocation") ?: "Unknown"
                val profileImageUrl = document.getString("profileImageUrl")
                val score = document.getLong("score")

                profileNameTextView.text = username
                profileLocationTextView.text = location
                if (score != null){
                    score_text.text = "$score%"
                    setScoreBackgroundColor(score_text, score)
                }
                else
                {
                    score_text.text = "0%"
                    setScoreBackgroundColor(score_text, 0)
                    findViewById<TextView>(R.id.no_trips_message).visibility = View.VISIBLE

                }

                if (!profileImageUrl.isNullOrEmpty()) {
                    Glide.with(this)
                        .load(profileImageUrl)
                        .transform(CircleCrop())
                        .into(profileImageView)
                }
            }
            .addOnFailureListener { e ->
                // Handle error
            }
    }

    private fun removeFriend(friendId: String) {
        val currentUser = auth.currentUser
        currentUser?.let {
            val userRef = firestore.collection("users").document(it.uid)
            userRef.update("friends", FieldValue.arrayRemove(friendId))
                .addOnSuccessListener {
                    // Navigate back to the community screen
                    val intent = Intent(this, Community::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    finish()
                }
                .addOnFailureListener { e ->
                    // Handle error
                }
        }
    }
    private fun setScoreBackgroundColor(textView: TextView, score: Long) {
        val color = when {
            score >= 80 -> android.graphics.Color.GREEN
            score >= 50 -> android.graphics.Color.YELLOW
            else -> android.graphics.Color.RED
        }

        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.OVAL
        drawable.setColor(color)
        textView.background = drawable
    }
}

