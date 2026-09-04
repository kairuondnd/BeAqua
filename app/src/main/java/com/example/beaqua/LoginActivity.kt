package com.example.beaqua

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import java.util.concurrent.TimeUnit

class LoginActivity : AppCompatActivity() {

    companion object {
        private const val ADMIN_USERNAME = "admin"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val ivLoginLogo = findViewById<ImageView>(R.id.ivLoginLogo)
        val tvTitle = findViewById<TextView>(R.id.tvTitle)
        val tilUsername = findViewById<TextInputLayout>(R.id.tilUsername)
        val tilPassword = findViewById<TextInputLayout>(R.id.tilPassword)
        val btnLogin = findViewById<MaterialButton>(R.id.btnLoginLoginPage)
        val btnBack = findViewById<ImageButton>(R.id.btnBackToMain)

        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPassword)

        val popAnimation = AnimationUtils.loadAnimation(this, R.anim.scale_up_pop)
        val slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up)

        ivLoginLogo.startAnimation(popAnimation)
        tvTitle.startAnimation(slideUp)
        
        tilUsername.visibility = View.INVISIBLE
        tilPassword.visibility = View.INVISIBLE
        btnLogin.visibility = View.INVISIBLE

        tilUsername.postDelayed({
            tilUsername.visibility = View.VISIBLE
            tilUsername.startAnimation(slideUp)
        }, 200)

        tilPassword.postDelayed({
            tilPassword.visibility = View.VISIBLE
            tilPassword.startAnimation(slideUp)
        }, 400)

        btnLogin.postDelayed({
            btnLogin.visibility = View.VISIBLE
            btnLogin.startAnimation(slideUp)
        }, 600)

        btnBack.setOnClickListener { finish() }

        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (username.equals(ADMIN_USERNAME, ignoreCase = true)) {
                setLoginBusy(btnLogin, true)
                Thread {
                    val passwordMatches = AdminCredentialStore.verify(this, password)
                    runOnUiThread {
                        setLoginBusy(btnLogin, false)
                        if (passwordMatches) {
                            startActivity(Intent(this, AdminActivity::class.java))
                            finish()
                        } else {
                            Toast.makeText(
                                this,
                                "Incorrect admin password",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }.start()
                return@setOnClickListener
            }

            FirebaseHelper.getUser(username).addOnSuccessListener { document ->
                if (document.exists()) {
                    val user = document.toObject(User::class.java)
                    if (user == null) {
                        Toast.makeText(this, "User account is invalid", Toast.LENGTH_SHORT).show()
                    } else if (user.accountType.equals("Station Owner", ignoreCase = true)) {
                        authenticateStationOwner(user, password, btnLogin)
                    } else if (user.password == password) {
                        scheduleWaterReminder()
                        startActivity(
                            Intent(this, UserHomeActivity::class.java).apply {
                                putExtra("USERNAME", user.username)
                                putExtra("ACCOUNT_TYPE", user.accountType)
                            }
                        )
                        finish()
                    } else {
                        Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
                }
            }.addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun authenticateStationOwner(
        user: User,
        password: String,
        loginButton: MaterialButton
    ) {
        if (user.password.isBlank()) {
            Toast.makeText(
                this,
                "This older station account needs a password reset. Please contact the administrator.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        setLoginBusy(loginButton, true)
        Thread {
            val passwordMatches = PasswordHelper.verify(password, user.password)
            val upgradedHash = if (
                passwordMatches && !PasswordHelper.isHashed(user.password)
            ) PasswordHelper.hash(password) else null

            runOnUiThread {
                setLoginBusy(loginButton, false)
                if (!passwordMatches) {
                    Toast.makeText(this, "Incorrect password", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }

                if (upgradedHash != null) {
                    user.password = upgradedHash
                    FirebaseHelper.usersCollection.document(user.username).update(
                        "password",
                        upgradedHash
                    )
                }
                continueStationOwnerLogin(user)
            }
        }.start()
    }

    private fun continueStationOwnerLogin(user: User) {
        startActivity(
            Intent(this, StationOwnerActivity::class.java).apply {
                putExtra("USERNAME", user.username)
                putExtra("ACCOUNT_TYPE", user.accountType)
            }
        )
        finish()
    }

    private fun setLoginBusy(button: MaterialButton, busy: Boolean) {
        button.isEnabled = !busy
        button.text = if (busy) "Signing in..." else "Sign in"
    }

    private fun scheduleWaterReminder() {
        val workRequest = PeriodicWorkRequestBuilder<WaterReminderWorker>(3, TimeUnit.DAYS)
            .build()
        
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "WaterReminder",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
