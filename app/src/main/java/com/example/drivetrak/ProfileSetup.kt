package com.example.drivetrak

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.*

class ProfileSetup : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    private lateinit var profileImageButton: ImageButton
    private var selectedImageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Standard edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_profile_setup)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        val usernameEditText: EditText = findViewById(R.id.usernameInput)
        val saveUsernameButton: Button = findViewById(R.id.nextButton)
        profileImageButton = findViewById(R.id.profileImage)

        profileImageButton.setOnClickListener {
            selectImage()
        }

        saveUsernameButton.setOnClickListener {
            val username = usernameEditText.text.toString()
            if (username.isEmpty()) {
                Toast.makeText(this, "Please enter a username", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (selectedImageUri != null) {
                uploadImageAndSaveUser(username)
            } else {
                saveUsername(username, null)
            }
        }
    }

    private fun selectImage() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, 1)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == Activity.RESULT_OK && data != null) {
            selectedImageUri = data.data
            profileImageButton.setImageURI(selectedImageUri)
        }
    }

    // Uploads the image to Firebase Storage and saves the user data
    private fun uploadImageAndSaveUser(username: String) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        val storageRef = storage.reference.child("profileImages/$uid.jpg")

        selectedImageUri?.let { uri ->
            storageRef.putFile(uri)
                .addOnSuccessListener {
                    // Get the download URL for the uploaded image
                    storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                        saveUsername(username, downloadUri.toString())
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Image upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("FirebaseStorage", "Upload failed", e)
                }
        } ?: run {
            Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show()
        }
    }

    // Saves the username and optional profile image URL to Firestore
    private fun saveUsername(username: String, profileImageUrl: String?) {
        val currentUser = auth.currentUser
        currentUser?.let {
            val uid = it.uid
            val user = User(uid = uid, username = username, profileImageUrl = profileImageUrl, email = it.email)

            // Store the user info in Firestore
            firestore.collection("users").document(uid)
                .set(user)
                .addOnSuccessListener {
                    Toast.makeText(this, "User info added successfully!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, ConnectObd::class.java))
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("Firestore", "Error saving user info", e)
                }
        } ?: run {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
        }
    }
}
