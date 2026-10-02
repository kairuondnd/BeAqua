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
    private var rememberForLogin = false

    companion object {
        private const val ADMIN_USERNAME = "admin"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BackNavigation.install(this)
        if (!intent.getBooleanExtra("RESTORE_SESSION", false)) clearAccountWork()
        setContentView(R.layout.activity_login)

        val ivLoginLogo = findViewById<ImageView>(R.id.ivLoginLogo)
        val tvTitle = findViewById<TextView>(R.id.tvTitle)
        val tilUsername = findViewById<TextInputLayout>(R.id.tilUsername)
        val tilPassword = findViewById<TextInputLayout>(R.id.tilPassword)
        val btnLogin = findViewById<MaterialButton>(R.id.btnLoginLoginPage)
        val btnBack = findViewById<ImageButton>(R.id.btnBackToMain)

        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        findViewById<TextView>(R.id.tvForgotPassword).setOnClickListener {
            PasswordRecovery.request(this, etUsername.text.toString().trim())
        }
        val keepSignedIn = findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbKeepSignedIn)

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

        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        btnLogin.setOnClickListener {
            rememberForLogin = keepSignedIn.isChecked
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
                            completeLogin("admin", "Admin", AdminCredentialStore.sessionCredential(this))
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

            setLoginBusy(btnLogin, true)
            FirebaseHelper.getUser(username).addOnSuccessListener { document ->
                setLoginBusy(btnLogin, false)
                if (document.exists()) {
                    val user = document.toObject(User::class.java)
                    if (user == null) {
                        Toast.makeText(this, "User account is invalid", Toast.LENGTH_SHORT).show()
                    } else if (user.accountType.equals("Station Owner", ignoreCase = true)) {
                        authenticateStationOwner(user, password, btnLogin)
                    } else if (user.password == password) {
                        scheduleWaterReminder()
                        completeLogin(user.username, user.accountType, user.password)
                    } else {
                        Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
                }
            }.addOnFailureListener { e ->
                setLoginBusy(btnLogin, false)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        if (intent.getBooleanExtra("RESTORE_SESSION", false)) restoreSession()
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
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (!passwordMatches) {
                    setLoginBusy(loginButton, false)
                    Toast.makeText(this, "Incorrect password", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }

                if (upgradedHash != null) {
                    FirebaseHelper.usersCollection.document(user.username).update(
                        "password",
                        upgradedHash
                    ).addOnCompleteListener { upgraded ->
                        if (upgraded.isSuccessful) user.password = upgradedHash
                        continueStationOwnerLogin(user)
                    }
                    return@runOnUiThread
                }
                continueStationOwnerLogin(user)
            }
        }.start()
    }

    private fun continueStationOwnerLogin(user: User) {
        completeLogin(user.username, user.accountType, user.password)
    }

    private fun completeLogin(username: String, role: String, credential: String) {
        if (isFinishing || isDestroyed) return
        try {
            RememberedSession.save(this, username, role, credential, rememberForLogin)
        } catch (_: Exception) {
            Toast.makeText(this, "Signed in, but this device could not remember the session.", Toast.LENGTH_LONG).show()
        }
        startActivity(RememberedSession.destination(this, username, role))
        finish()
    }

    private fun clearAccountWork() {
        DeliveryReminderWorker.clear(this)
        SubscriptionOrderWorker.clearAccount(this)
    }

    private fun restoreSession() {
        val account = RememberedSession.read(this)
        if (account == null) {
            clearAccountWork()
            return
        }
        val button = findViewById<MaterialButton>(R.id.btnLoginLoginPage)
        setLoginBusy(button, true)
        fun invalidSession() {
            RememberedSession.clear(this)
            clearAccountWork()
            setLoginBusy(button, false)
            Toast.makeText(this, "Please sign in again.", Toast.LENGTH_SHORT).show()
        }
        fun openAccount() {
            if (isFinishing || isDestroyed) return
            startActivity(RememberedSession.destination(this, account.username, account.role))
            finish()
        }
        if (account.role == "Admin") {
            if (account.credentialFingerprint == RememberedSession.fingerprint(AdminCredentialStore.sessionCredential(this)))
                openAccount() else invalidSession()
            return
        }
        // A server read prevents a deleted account or an old password from restoring from cached data.
        FirebaseHelper.usersCollection.document(account.username)
            .get(com.google.firebase.firestore.Source.SERVER)
            .addOnSuccessListener { document ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                val user = document.toObject(User::class.java)
                if (user == null || user.accountType != account.role ||
                    account.credentialFingerprint != RememberedSession.fingerprint(user.password)) {
                    invalidSession()
                } else openAccount()
            }.addOnFailureListener {
                if (isFinishing || isDestroyed) return@addOnFailureListener
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Unable to restore sign-in")
                    .setMessage("Connect to the internet to check your saved account, then try again.")
                    .setPositiveButton("Retry") { _, _ -> restoreSession() }
                    .setNegativeButton("Sign in manually") { _, _ ->
                        RememberedSession.clear(this)
                        clearAccountWork()
                        setLoginBusy(button, false)
                    }.setCancelable(false).show()
            }
    }

    private fun setLoginBusy(button: MaterialButton, busy: Boolean) {
        button.isEnabled = !busy
        findViewById<View>(R.id.cbKeepSignedIn).isEnabled = !busy
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
