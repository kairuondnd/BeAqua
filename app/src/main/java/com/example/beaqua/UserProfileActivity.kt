package com.example.beaqua

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class UserProfileActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etContact: EditText
    private lateinit var etAddress: EditText
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var tvDisplayName: TextView
    private lateinit var btnSavePersonal: Button
    private lateinit var btnSaveAccount: Button
    private lateinit var btnSetLocation: Button
    private lateinit var btnLogout: Button
    private lateinit var btnBack: ImageButton

    private lateinit var currentUser: User
    private lateinit var currentUsername: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_profile)

        // Bind views safely
        etName = findViewById(R.id.etName)
        etContact = findViewById(R.id.etContact)
        etAddress = findViewById(R.id.etAddress)
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        tvDisplayName = findViewById(R.id.tvDisplayUserName)
        btnSavePersonal = findViewById(R.id.btnSavePersonal)
        btnSaveAccount = findViewById(R.id.btnSaveAccount)
        btnSetLocation = findViewById(R.id.btnSetLocation)
        btnLogout = findViewById(R.id.btnLogout)
        btnBack = findViewById(R.id.btnBackProfile)

        btnBack.setOnClickListener { finish() }

        // Get username passed from intent
        currentUsername = intent.getStringExtra("USERNAME") ?: ""
        if (currentUsername.isEmpty()) {
            Toast.makeText(this, "No user logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Load user from Firebase
        FirebaseHelper.getUser(currentUsername).addOnSuccessListener { document ->
            val user = document.toObject(User::class.java)
            if (user == null) {
                Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
                finish()
                return@addOnSuccessListener
            }
            currentUser = user

            // Populate fields
            etName.setText(currentUser.name)
            etContact.setText(currentUser.contactNumber)
            etAddress.setText(currentUser.address)
            etUsername.setText(currentUser.username)
            etPassword.setText(currentUser.password)
            tvDisplayName.text = currentUser.name.ifEmpty { currentUser.username }
        }

        btnSetLocation.setOnClickListener {
            if (::currentUser.isInitialized) {
                val intent = Intent(this, PickLocationActivity::class.java)
                intent.putExtra("USERNAME", currentUsername)
                currentUser.latitude?.let { intent.putExtra("LATITUDE", it) }
                currentUser.longitude?.let { intent.putExtra("LONGITUDE", it) }
                startActivityForResult(intent, 1001)
            } else {
                Toast.makeText(this, "Loading user data...", Toast.LENGTH_SHORT).show()
            }
        }

        // Save personal info
        btnSavePersonal.setOnClickListener {
            if (!::currentUser.isInitialized) return@setOnClickListener
            
            val newName = etName.text.toString()
            val newContact = etContact.text.toString()
            val newAddress = etAddress.text.toString()

            if (newName.isEmpty()) {
                etName.error = "Name cannot be empty"
                return@setOnClickListener
            }

            currentUser.name = newName
            currentUser.contactNumber = newContact
            currentUser.address = newAddress

            FirebaseHelper.addUser(currentUser).addOnSuccessListener {
                tvDisplayName.text = newName
                Toast.makeText(this, "Personal info updated successfully!", Toast.LENGTH_SHORT).show()
            }
        }

        // Save account info
        btnSaveAccount.setOnClickListener {
            if (!::currentUser.isInitialized) return@setOnClickListener
            
            val newPassword = etPassword.text.toString()
            if (newPassword.length < 6) {
                etPassword.error = "Password must be at least 6 characters"
                return@setOnClickListener
            }

            currentUser.password = newPassword
            FirebaseHelper.addUser(currentUser).addOnSuccessListener {
                Toast.makeText(this, "Password updated successfully!", Toast.LENGTH_SHORT).show()
            }
        }

        btnLogout.setOnClickListener {
            DeliveryReminderWorker.clear(this)
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == Activity.RESULT_OK && data != null) {
            val lat = data.getDoubleExtra("LATITUDE", 0.0)
            val lon = data.getDoubleExtra("LONGITUDE", 0.0)
            val address = data.getStringExtra("ADDRESS")
            
            currentUser.latitude = lat
            currentUser.longitude = lon
            if (address != null) {
                currentUser.address = address
                etAddress.setText(address)
            }
        }
    }
}
