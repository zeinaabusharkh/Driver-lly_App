package com.example.drivetrak

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class Community : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var friendsContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_community)

        val inviteButton: Button = findViewById(R.id.inviteButton)
        inviteButton.setOnClickListener {
            val intent = Intent(this, Invite::class.java)
            startActivityForResult(intent, REQUEST_CODE_INVITE)
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        friendsContainer = findViewById(R.id.friendsContainer)

        loadFriends()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_INVITE && resultCode == RESULT_OK) {
            loadFriends() // Refresh the friends list
        }
    }

    private fun loadFriends() {
        val currentUser = auth.currentUser
        currentUser?.let {
            firestore.collection("users").document(it.uid).get()
                .addOnSuccessListener { document ->
                    val friends = document.get("friends") as? List<String> ?: emptyList()
                    friendsContainer.removeAllViews() // Clear existing views
                    for (friendId in friends) {
                        loadFriendProfile(friendId)
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun loadFriendProfile(friendId: String) {
        firestore.collection("users").document(friendId).get()
            .addOnSuccessListener { document ->
                val username = document.getString("username") ?: "Unknown"
                val location = document.getString("profileLocation") ?: "Unknown"
                val profileImageUrl = document.getString("profileImageUrl")

                val friendView = LayoutInflater.from(this).inflate(R.layout.friend_profile, friendsContainer, false)
                val profileImageView: ImageView = friendView.findViewById(R.id.profile_image)
                val profileNameTextView: TextView = friendView.findViewById(R.id.profile_name)
                val profileLocationTextView: TextView = friendView.findViewById(R.id.profile_location)
                val viewButton: Button = friendView.findViewById(R.id.view_button)

                profileNameTextView.text = username
                profileLocationTextView.text = location
                if (!profileImageUrl.isNullOrEmpty()) {
                    Glide.with(this)
                        .load(profileImageUrl)
                        .transform(CircleCrop())
                        .into(profileImageView)
                }

                viewButton.setOnClickListener {
                    if (friendId != auth.currentUser?.uid) {
                        val intent = Intent(this, FriendDashboard::class.java)
                        intent.putExtra("FRIEND_ID", friendId)
                        startActivity(intent)
                    }
                }

                friendsContainer.addView(friendView)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    companion object {
        private const val REQUEST_CODE_INVITE = 1
    }
}