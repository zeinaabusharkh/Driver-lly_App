package com.example.drivetrak

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class Dashboard : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

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
            val viewAll: TextView = findViewById(R.id.view_all)
            val communityButton: Button = findViewById(R.id.community_button)
            val usernameTextView: TextView = findViewById(R.id.usernameTextView)
            val profileImageView: ImageView = findViewById(R.id.profileImageView)
            val profile_location: TextView = findViewById(R.id.profile_location)
            val logout_button: Button = findViewById(R.id.logout_button)
            val score_text: TextView = findViewById(R.id.score_text)

            communityButton.setOnClickListener {
                startActivity(Intent(this, Community::class.java))
            }
            viewAll.setOnClickListener {
                startActivity(Intent(this, ViewAll::class.java))
            }
            logout_button.setOnClickListener {
                auth.signOut()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }

            // Retrieve user data from Firestore
            firestore.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { document ->
                    if (document != null) {
                        val username = document.getString("username")?: "Unknown"
                        val profileImageUrl = document.getString("profileImageUrl")
                        val profileLocation = document.getString("profileLocation")?: "Unknown"
                        val score = document.getLong("score")

                        if (score != null){
                            score_text.text = score.toString()+"%"
                        }
                        else
                        {
                            score_text.text = "0%"
                        }

                        usernameTextView.text = username
                        profile_location.text = profileLocation
                        if (!profileImageUrl.isNullOrEmpty()) {
                            Glide.with(this)
                                .load(profileImageUrl)
                                .transform(CircleCrop())
                                .into(profileImageView)
                        }
                    } else {
                        // Handle the case where the document does not exist
                        usernameTextView.text = "No username found"
                    }
                }
                .addOnFailureListener { e ->
                    // Handle the error
                    usernameTextView.text = "Error: ${e.message}"
                }
        }
    }
}