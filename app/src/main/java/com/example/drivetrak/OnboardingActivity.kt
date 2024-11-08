package com.example.drivetrak

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var tabIndicator: TabLayout
    private lateinit var onboardingAdapter: OnboardingAdapter


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        viewPager = findViewById(R.id.viewPager)
        tabIndicator = findViewById(R.id.tabIndicator)

        onboardingAdapter = OnboardingAdapter(this)
        viewPager.adapter = onboardingAdapter
        val skipButton: Button = findViewById(R.id.skip_button)

        skipButton.setOnClickListener {
            val intent = Intent(this, Dashboard::class.java)
            startActivity(intent)
        }

        // Link TabLayout (dot indicator) with ViewPager2
        TabLayoutMediator(tabIndicator, viewPager) { tab, position ->
            // Optional: customize tab behavior (e.g., set text or icons)
        }.attach()
    }
}
