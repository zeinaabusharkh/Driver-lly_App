package com.example.drivetrak

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class Leadership : AppCompatActivity() {
    private lateinit var firstPlaceName: TextView
    private lateinit var firstPlaceScore: TextView
    private lateinit var firstPlaceImage: ShapeableImageView
    private lateinit var secondPlaceName: TextView
    private lateinit var secondPlaceScore: TextView
    private lateinit var secondPlaceImage: ShapeableImageView
    private lateinit var thirdPlaceName: TextView
    private lateinit var thirdPlaceScore: TextView
    private lateinit var thirdPlaceImage: ShapeableImageView
    private lateinit var rankingsList: RecyclerView
    private lateinit var rankingAdapter: RankingAdapter


    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private lateinit var positionLabelView: TextView
    private lateinit var imageView: ShapeableImageView
    private lateinit var nameTextView: TextView
    private lateinit var positionTextView: TextView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leadership) // Set the content view first
        enableEdgeToEdge()
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Initialize views
        firstPlaceName = findViewById(R.id.first_place_name)
        firstPlaceScore = findViewById(R.id.first_place_score)
        firstPlaceImage = findViewById(R.id.first_place_image)
        secondPlaceName = findViewById(R.id.second_place_name)
        secondPlaceScore = findViewById(R.id.second_place_score)
        secondPlaceImage = findViewById(R.id.second_place_image)
        thirdPlaceName = findViewById(R.id.third_place_name)
        thirdPlaceScore = findViewById(R.id.third_place_score)
        thirdPlaceImage = findViewById(R.id.third_place_image)
        rankingsList = findViewById(R.id.rankings_list)


        // initalize your ranking
         positionLabelView = findViewById<TextView>(R.id.your_position_label)
         imageView = findViewById<ShapeableImageView>(R.id.your_image)
         nameTextView = findViewById<TextView>(R.id.your_name)
         positionTextView = findViewById<TextView>(R.id.your_position)

        // Initialize RecyclerView
        rankingAdapter = RankingAdapter()
        rankingsList.adapter = rankingAdapter
        rankingsList.layoutManager = LinearLayoutManager(this)

        loadscore()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun loadscore() {
            val current_user = auth.currentUser
            var position = 0
        firestore.collection("users") .orderBy("score", Query.Direction.DESCENDING)
                    .get()
                    .addOnSuccessListener { documents ->
                        val sortedUsers = documents.map { doc ->
                            val userId = doc.id
                            val score = doc.getLong("score") ?: 0
                            val username = doc.getString("username") ?: "Unknown"
                            val profileImageUrl = doc.getString("profileImageUrl")
                            position++;
                            User(userId, username, score, position,profileImageUrl ) // Assuming you have a User data class
                        }

                        // Now, sortedUsers is a list of User objects sorted by score
                        sortedUsers.forEach { user ->
                            Log.d("usersorted: ", "User: ${user.username} Score: ${user.Score}  Position: ${user.position}" )
                        }

                        if (current_user != null) {
                            val user = sortedUsers.find { it.uid == current_user.uid }
                            if (user != null) {
                                positionLabelView.text = "Your Position: ${user.position}"
                                nameTextView.text = user.username
                                positionTextView.text = "${user.Score} pts"
                                if (!user.profileImageUrl.isNullOrEmpty()) {
                                    Glide.with(this)
                                        .load(user.profileImageUrl)
                                        .transform(CircleCrop())
                                        .into(imageView)
                                }
                            }
                        }
                        sortedUsers.getOrNull(0)?.let { user ->
                            firstPlaceName.text = user.username
                            firstPlaceScore.text = "${user.Score} pts"

                            if (!user.profileImageUrl.isNullOrEmpty()) {
                                Glide.with(this)
                                    .load(user.profileImageUrl)
                                    .transform(CircleCrop())
                                    .into(firstPlaceImage)
                            }

                        }

                        sortedUsers.getOrNull(1)?.let { user ->
                            secondPlaceName.text = user.username
                            secondPlaceScore.text = "${user.Score} pts"

                            if (!user.profileImageUrl.isNullOrEmpty()) {
                                Glide.with(this)
                                    .load(user.profileImageUrl)
                                    .transform(CircleCrop())
                                    .into(secondPlaceImage)
                            }
                        }

                        sortedUsers.getOrNull(2)?.let { user ->
                            thirdPlaceName.text = user.username
                            thirdPlaceScore.text = "${user.Score} pts"

                            if (!user.profileImageUrl.isNullOrEmpty()) {
                                Glide.with(this)
                                    .load(user.profileImageUrl)
                                    .transform(CircleCrop())
                                    .into(thirdPlaceImage)
                            }

                        }
                        rankingAdapter.submitList(sortedUsers.drop(3))

                    }
                    .addOnFailureListener { exception ->
                        Log.d("Error getting documents:" , "${exception.message}")
                    }
    }




    }
