package com.example.drivetrak

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class AccountSettings : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    private lateinit var usernameInput: EditText
    private lateinit var profileImageButton: ImageButton
    private lateinit var saveChangesButton: Button
    private lateinit var deleteAccountButton: Button

    private val PICK_IMAGE_REQUEST = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_account_settings)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        usernameInput = findViewById(R.id.usernameInput)
        profileImageButton = findViewById(R.id.profileImage)
        saveChangesButton = findViewById(R.id.saveChangesButton)
        deleteAccountButton = findViewById(R.id.deleteAccountButton)

        profileImageButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, PICK_IMAGE_REQUEST)
        }

        saveChangesButton.setOnClickListener {
            saveChanges()
        }

        deleteAccountButton.setOnClickListener {
            deleteAccount()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null && data.data != null) {
            val imageUri: Uri = data.data!!
            uploadProfilePhoto(imageUri)
            // Display the selected image using Glide
            imageUri?.let { uri ->
                Glide.with(this)
                    .load(uri)
                    .transform(CircleCrop())
                    .into(profileImageButton)
            }
        }
    }

    private fun uploadProfilePhoto(imageUri: Uri) {
        val user = auth.currentUser ?: return
        val storageRef = storage.reference.child("profile_photos/${user.uid}.jpg")
        storageRef.putFile(imageUri)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    firestore.collection("users").document(user.uid)
                        .update("profileImageUrl", uri.toString())
                        .addOnSuccessListener {
                            Toast.makeText(this, "Profile photo updated", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to upload profile photo", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveChanges() {
        val user = auth.currentUser ?: return
        val username = usernameInput.text.toString()

        if (username.isNotEmpty()) {
            firestore.collection("users").document(user.uid)
                .update("username", username)
                .addOnSuccessListener {
                    Toast.makeText(this, "Username updated", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to update username", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun deleteAccount() {
        val user = auth.currentUser ?: return
        firestore.collection("users").document(user.uid).delete()
            .addOnSuccessListener {
                user.delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Account deleted", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this, MainActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                        finish()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to delete account", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to delete user data", Toast.LENGTH_SHORT).show()
            }
    }
}