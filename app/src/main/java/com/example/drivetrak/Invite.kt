// Invite.kt
package com.example.drivetrak

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class Invite : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_invite)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        val emailInput: EditText = findViewById(R.id.FriendEmailInput)
        val inviteButton: Button = findViewById(R.id.inviteButton)

        inviteButton.setOnClickListener {
            val email = emailInput.text.toString()
            checkUserExists(email)
        }
    }

    private fun checkUserExists(email: String) {
        firestore.collection("users").whereEqualTo("email", email).get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Toast.makeText(this, "User does not exist. Invite them to join!", Toast.LENGTH_LONG).show()
                } else {
                    val userId = documents.documents[0].id
                    saveFriend(userId)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveFriend(friendId: String) {
        val currentUser = auth.currentUser
        currentUser?.let {
            val userRef = firestore.collection("users").document(it.uid)
            userRef.update("friends", FieldValue.arrayUnion(friendId))
                .addOnSuccessListener {
                    Toast.makeText(this, "Friend added successfully!", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK) // Set result to OK
                    finish() // Close the activity
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}