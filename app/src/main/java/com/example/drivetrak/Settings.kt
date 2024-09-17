package com.example.drivetrak

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class Settings : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val accountSettingsButton: Button = findViewById(R.id.accountSettingsButton)
        val notificationsButton: Button = findViewById(R.id.notificationsButton)
        accountSettingsButton.setOnClickListener {
            startActivity(Intent(this, AccountSettings::class.java))
        }

        notificationsButton.setOnClickListener {
            //startActivity(Intent(this, Notifications::class.java))
        }
    }
}