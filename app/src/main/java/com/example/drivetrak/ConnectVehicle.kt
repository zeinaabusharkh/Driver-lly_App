package com.example.drivetrak

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ConnectVehicle : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_connect_vehicle)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val vehicleName: TextView = findViewById(R.id.vehicleNameInput)
        val nextButtonVehicle: Button = findViewById(R.id.nextButtonVehicle)
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        nextButtonVehicle.setOnClickListener {
            val Vehicle = vehicleName.text.toString()
            val currentUser = auth.currentUser
            currentUser?.let {
                val uid = it.uid
                firestore.collection("users").document(uid)
                    .update("vehicle", Vehicle)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Vehicle info updated successfully!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, ConnectObd::class.java))
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        Log.e("Firestore", "Error updating vehicle info", e)
                    }
            } ?: run {
                Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            }
        }
    }
}