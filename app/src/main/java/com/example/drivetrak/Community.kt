package com.example.drivetrak

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class Community : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_community)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val inviteButton:Button = findViewById(R.id.inviteButton)
        val viewButton:Button = findViewById(R.id.view_button)
        inviteButton.setOnClickListener {
            val intent = Intent(this, Invite::class.java)
            startActivity(intent)
        }
        viewButton.setOnClickListener {
            val intent = Intent(this, FriendProfile::class.java)
            startActivity(intent)
        }


    }
    }
