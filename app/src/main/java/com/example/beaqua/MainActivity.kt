package com.example.beaqua

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.button.MaterialButton
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tagline = findViewById<TextView>(R.id.tagline)
        val btnGetStarted = findViewById<MaterialButton>(R.id.btnGetStarted)
        val btnSignupMain = findViewById<MaterialButton>(R.id.btnSignupMain)

        // Schedule Water Reminder (Once per day)
        scheduleWaterReminder()

        // Load animations
        val slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up)

        // Apply animations
        tagline.startAnimation(slideUp)
        
        val floating = AnimationUtils.loadAnimation(this, R.anim.float_up_down)
        findViewById<View>(R.id.ivHeaderMain).startAnimation(floating)
        
        btnGetStarted.startAnimation(slideUp)
        btnSignupMain.startAnimation(slideUp)

        btnGetStarted.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }

        btnSignupMain.setOnClickListener {
            val intent = Intent(this, SignupActivity::class.java)
            startActivity(intent)
        }
    }

    private fun scheduleWaterReminder() {
        val reminderRequest = PeriodicWorkRequestBuilder<WaterReminderWorker>(24, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "WaterReminderWork",
            ExistingPeriodicWorkPolicy.KEEP, // Keep existing if already scheduled
            reminderRequest
        )
    }
}
