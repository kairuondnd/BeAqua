package com.example.beaqua

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Patterns
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.tasks.Tasks
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout

class SignupActivity : AppCompatActivity() {

    private var stationOwnerMode = false
    private var selectedLat: Double? = null
    private var selectedLon: Double? = null
    private lateinit var etAddress: EditText

    private var barangayClearanceUri: Uri? = null
    private var sanitaryPermitUri: Uri? = null
    private var mayorsPermitUri: Uri? = null

    private lateinit var tvBarangayClearanceFile: TextView
    private lateinit var tvSanitaryPermitFile: TextView
    private lateinit var tvMayorsPermitFile: TextView

    private val locationPicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == Activity.RESULT_OK && data != null) {
                selectedLat = data.getDoubleExtra("LATITUDE", 0.0)
                selectedLon = data.getDoubleExtra("LONGITUDE", 0.0)
                etAddress.setText(data.getStringExtra("ADDRESS"))
            }
        }

    private val barangayClearancePicker = documentPicker { uri ->
        barangayClearanceUri = uri
        tvBarangayClearanceFile.text = selectedFileName(uri)
    }

    private val sanitaryPermitPicker = documentPicker { uri ->
        sanitaryPermitUri = uri
        tvSanitaryPermitFile.text = selectedFileName(uri)
    }

    private val mayorsPermitPicker = documentPicker { uri ->
        mayorsPermitUri = uri
        tvMayorsPermitFile.text = selectedFileName(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        val tvTitle = findViewById<TextView>(R.id.tvTitle)
        val tvSubtitle = findViewById<TextView>(R.id.tvSubtitle)
        val radioAccountType = findViewById<RadioGroup>(R.id.radioAccountType)
        val layoutKycDocuments = findViewById<View>(R.id.layoutKycDocuments)
        val tvInformationSectionTitle = findViewById<TextView>(R.id.tvInformationSectionTitle)
        val tvInformationSectionSubtitle = findViewById<TextView>(R.id.tvInformationSectionSubtitle)
        val tvSubmissionNote = findViewById<TextView>(R.id.tvSubmissionNote)

        val tilName = findViewById<TextInputLayout>(R.id.tilName)
        val tilOwnerFullName = findViewById<TextInputLayout>(R.id.tilOwnerFullName)
        val tilContact = findViewById<TextInputLayout>(R.id.tilContact)
        val tilEmail = findViewById<TextInputLayout>(R.id.tilEmail)
        val tilAddress = findViewById<TextInputLayout>(R.id.tilAddress)
        val tilUsername = findViewById<TextInputLayout>(R.id.tilUsername)
        val tilPasswordSignup = findViewById<TextInputLayout>(R.id.tilPasswordSignup)

        val btnSignup = findViewById<MaterialButton>(R.id.btnSignupSubmit)
        val btnBack = findViewById<MaterialButton>(R.id.btnBackToMainSignup)
        val signupProgress = findViewById<ProgressBar>(R.id.signupProgress)
        val privacyAcknowledgment = findViewById<CheckBox>(R.id.cbPrivacyAcknowledgment)
        val privacyError = findViewById<TextView>(R.id.tvPrivacyError)
        val openPrivacyNotice = View.OnClickListener {
            startActivity(Intent(this, PrivacyNoticeActivity::class.java))
        }
        findViewById<View>(R.id.btnReadPrivacyNotice).setOnClickListener(openPrivacyNotice)
        findViewById<View>(R.id.btnReadPrivacyNoticeBottom).setOnClickListener(openPrivacyNotice)
        privacyAcknowledgment.setOnCheckedChangeListener { _, checked ->
            if (checked) privacyError.visibility = View.GONE
        }

        val etName = findViewById<EditText>(R.id.etName)
        val etOwnerFullName = findViewById<EditText>(R.id.etOwnerFullName)
        val etContact = findViewById<EditText>(R.id.etContact)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        etAddress = findViewById(R.id.etAddress)
        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPasswordSignup)

        tvBarangayClearanceFile = findViewById(R.id.tvBarangayClearanceFile)
        tvSanitaryPermitFile = findViewById(R.id.tvSanitaryPermitFile)
        tvMayorsPermitFile = findViewById(R.id.tvMayorsPermitFile)

        val openLocationPicker = View.OnClickListener {
            locationPicker.launch(Intent(this, PickLocationActivity::class.java))
        }
        etAddress.setOnClickListener(openLocationPicker)
        tilAddress.setEndIconOnClickListener { openLocationPicker.onClick(etAddress) }

        findViewById<MaterialButton>(R.id.btnSelectBarangayClearance).setOnClickListener {
            barangayClearancePicker.launch(arrayOf("application/pdf", "image/*"))
        }
        findViewById<MaterialButton>(R.id.btnSelectSanitaryPermit).setOnClickListener {
            sanitaryPermitPicker.launch(arrayOf("application/pdf", "image/*"))
        }
        findViewById<MaterialButton>(R.id.btnSelectMayorsPermit).setOnClickListener {
            mayorsPermitPicker.launch(arrayOf("application/pdf", "image/*"))
        }

        fun updateAccountTypeUi(isStationOwner: Boolean) {
            stationOwnerMode = isStationOwner
            layoutKycDocuments.visibility = if (isStationOwner) View.VISIBLE else View.GONE
            tilOwnerFullName.visibility = if (isStationOwner) View.VISIBLE else View.GONE
            tilEmail.visibility = if (isStationOwner) View.VISIBLE else View.GONE
            tilName.hint = if (isStationOwner) "Water Station / Legal Business Name *" else "Full Name *"
            tilContact.hint = if (isStationOwner) "Business Contact Number *" else "Contact Number"
            tilAddress.hint = if (isStationOwner) "Registered Business Address *" else "Delivery Address *"
            tvInformationSectionTitle.text =
                if (isStationOwner) "STATION INFORMATION" else "PERSONAL INFORMATION"
            tvInformationSectionSubtitle.text = if (isStationOwner) {
                "Enter the legal information shown on the submitted permits."
            } else {
                "Tell us where your orders should be delivered."
            }
            tvSubtitle.text = if (isStationOwner) {
                "Register a verified water-refilling station."
            } else {
                "Create a customer account for water delivery."
            }
            btnSignup.text =
                if (isStationOwner) "Submit Station Application" else "Create Customer Account"
            tvSubmissionNote.text = if (isStationOwner) {
                "Submitting creates a pending application. Station access begins only after KYC approval."
            } else {
                "By continuing, you confirm that the information provided is accurate."
            }
            if (!isStationOwner) {
                tilOwnerFullName.error = null
                tilEmail.error = null
            }
        }

        radioAccountType.setOnCheckedChangeListener { _, checkedId ->
            updateAccountTypeUi(checkedId == R.id.radioOwner)
        }
        updateAccountTypeUi(false)

        val slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up)
        listOf(
            tvTitle,
            tvSubtitle,
            tilName,
            tilOwnerFullName,
            tilContact,
            tilEmail,
            tilAddress,
            tilUsername,
            tilPasswordSignup,
            btnSignup,
            btnBack
        ).forEach { it.startAnimation(slideUp) }

        btnBack.setOnClickListener { finish() }

        btnSignup.setOnClickListener {
            if (!privacyAcknowledgment.isChecked) {
                privacyError.visibility = View.VISIBLE
                privacyAcknowledgment.requestRectangleOnScreen(android.graphics.Rect(
                    0, 0, privacyAcknowledgment.width, privacyAcknowledgment.height), false)
                privacyError.announceForAccessibility(getString(R.string.privacy_acknowledgment_required))
                return@setOnClickListener
            }
            val name = etName.text.toString().trim()
            val ownerFullName = etOwnerFullName.text.toString().trim()
            val contact = etContact.text.toString().trim()
            val email = etEmail.text.toString().trim().lowercase()
            val address = etAddress.text.toString().trim()
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val isStationOwner = stationOwnerMode
            val accountType = if (isStationOwner) "Station Owner" else "Customer"

            listOf(
                tilName,
                tilOwnerFullName,
                tilContact,
                tilEmail,
                tilAddress,
                tilUsername,
                tilPasswordSignup
            ).forEach { it.error = null }

            var formIsValid = true
            if (name.length < 2) {
                tilName.error = if (isStationOwner) {
                    "Enter the legal station or business name"
                } else {
                    "Enter your full name"
                }
                formIsValid = false
            }
            if (isStationOwner && ownerFullName.length < 2) {
                tilOwnerFullName.error = "Enter the proprietor's full legal name"
                formIsValid = false
            }
            val contactDigits = contact.count(Char::isDigit)
            if (isStationOwner && contactDigits !in 7..15) {
                tilContact.error = "Enter a valid business contact number"
                formIsValid = false
            } else if (!isStationOwner && contact.isNotEmpty() && contactDigits !in 7..15) {
                tilContact.error = "Enter a valid contact number"
                formIsValid = false
            }
            if (isStationOwner && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                tilEmail.error = "Enter a valid business email address"
                formIsValid = false
            }
            if (address.isEmpty() || selectedLat == null || selectedLon == null) {
                tilAddress.error = "Choose the exact address from the map"
                formIsValid = false
            }
            if (
                username.length < 4 ||
                !username.matches(Regex("[A-Za-z0-9._-]+"))
            ) {
                tilUsername.error = "Use at least 4 characters with no spaces"
                formIsValid = false
            }
            if (password.length < 6) {
                tilPasswordSignup.error = "Password must contain at least 6 characters"
                formIsValid = false
            }
            if (!formIsValid) {
                Toast.makeText(this, "Please review the highlighted information", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (username.equals("admin", ignoreCase = true)) {
                Toast.makeText(this, "This username is reserved", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (
                isStationOwner &&
                (barangayClearanceUri == null || sanitaryPermitUri == null || mayorsPermitUri == null)
            ) {
                Toast.makeText(
                    this,
                    "Please attach all three required permits",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            setSubmitting(true, btnSignup, signupProgress)
            FirebaseHelper.getUser(username)
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        setSubmitting(false, btnSignup, signupProgress)
                        Toast.makeText(this, "Username already exists", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }

                    val user = User(
                        name = name,
                        ownerFullName = if (isStationOwner) ownerFullName else "",
                        emailAddress = if (isStationOwner) email else "",
                        contactNumber = contact,
                        address = address,
                        username = username,
                        password = password,
                        accountType = accountType,
                        latitude = selectedLat,
                        longitude = selectedLon,
                        kycStatus = if (isStationOwner) {
                            User.KYC_PENDING
                        } else {
                            User.KYC_NOT_REQUIRED
                        },
                        kycSubmittedAt = if (isStationOwner) System.currentTimeMillis() else 0L,
                        privacyNoticeVersion = PrivacyNotice.VERSION,
                        privacyNoticeAcknowledgedAt = System.currentTimeMillis()
                    )

                    if (isStationOwner) {
                        FirebaseHelper.isEmailRegistered(email)
                            .addOnSuccessListener { emailIsRegistered ->
                                if (emailIsRegistered) {
                                    setSubmitting(false, btnSignup, signupProgress)
                                    tilEmail.error = "This email is already registered"
                                    Toast.makeText(
                                        this,
                                        "Business email already exists",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    uploadKycAndCreateUser(user, btnSignup, signupProgress)
                                }
                            }
                            .addOnFailureListener { error ->
                                setSubmitting(false, btnSignup, signupProgress)
                                Toast.makeText(
                                    this,
                                    "Could not check email: ${error.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                    } else {
                        createUser(user, btnSignup, signupProgress)
                    }
                }
                .addOnFailureListener { error ->
                    setSubmitting(false, btnSignup, signupProgress)
                    Toast.makeText(this, "Could not check username: ${error.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun uploadKycAndCreateUser(
        user: User,
        submitButton: MaterialButton,
        progress: ProgressBar
    ) {
        Thread {
            try {
                val passwordHash = PasswordHelper.hash(user.password)
                runOnUiThread {
                    user.password = passwordHash
                    user.supabaseUserId = ""
                    uploadStationDocuments(user, submitButton, progress)
                }
            } catch (error: Exception) {
                runOnUiThread {
                    setSubmitting(false, submitButton, progress)
                    showSignupError(
                        "The station password could not be secured: ${error.message}"
                    )
                }
            }
        }.start()
    }

    private fun uploadStationDocuments(
        user: User,
        submitButton: MaterialButton,
        progress: ProgressBar
    ) {
        val uploads = listOf(
            FirebaseHelper.uploadKycDocument(
                this,
                user.username,
                "barangay_business_clearance",
                requireNotNull(barangayClearanceUri)
            ),
            FirebaseHelper.uploadKycDocument(
                this,
                user.username,
                "sanitary_permit",
                requireNotNull(sanitaryPermitUri)
            ),
            FirebaseHelper.uploadKycDocument(
                this,
                user.username,
                "mayors_business_permit",
                requireNotNull(mayorsPermitUri)
            )
        )

        Tasks.whenAllSuccess<String>(uploads)
            .addOnSuccessListener { paths ->
                user.barangayClearanceUrl = paths[0]
                user.sanitaryPermitUrl = paths[1]
                user.mayorsPermitUrl = paths[2]
                createUser(user, submitButton, progress)
            }
            .addOnFailureListener { error ->
                setSubmitting(false, submitButton, progress)
                showSignupError(
                    "The permits could not be uploaded. Check your connection and try again.\n\n" +
                        (error.message ?: "Document upload failed")
                )
            }
    }

    private fun showSignupError(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Station application not submitted")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun createUser(
        user: User,
        submitButton: MaterialButton,
        progress: ProgressBar,
        successMessage: String? = null
    ) {
        val createTask = if (user.accountType == "Station Owner") {
            FirebaseHelper.addStationOwnerWithUniqueEmail(user)
        } else {
            FirebaseHelper.createCustomer(user)
        }
        createTask
            .addOnSuccessListener {
                setSubmitting(false, submitButton, progress)
                val message = successMessage ?: if (user.accountType == "Station Owner") {
                    "Application submitted. You can sign in now with limited access while an " +
                        "administrator reviews your KYC documents."
                } else {
                    "Signup successful!"
                }
                if (user.accountType == "Station Owner") {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Application submitted")
                        .setMessage(message)
                        .setCancelable(false)
                        .setPositiveButton("Continue") { _, _ -> finish() }
                        .show()
                } else {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    finish()
                }
            }
            .addOnFailureListener { error ->
                setSubmitting(false, submitButton, progress)
                Toast.makeText(this, "Signup failed: ${error.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun documentPicker(onSelected: (Uri) -> Unit) =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                    // The URI remains readable for this signup session.
                }
                onSelected(uri)
            }
        }

    private fun selectedFileName(uri: Uri): String {
        val displayName = contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        return "Selected: ${displayName ?: "document"}"
    }

    private fun setSubmitting(
        submitting: Boolean,
        submitButton: MaterialButton,
        progress: ProgressBar
    ) {
        submitButton.isEnabled = !submitting
        findViewById<CheckBox>(R.id.cbPrivacyAcknowledgment).isEnabled = !submitting
        submitButton.text = if (submitting) {
            "Submitting..."
        } else if (stationOwnerMode) {
            "Submit Station Application"
        } else {
            "Create Customer Account"
        }
        progress.visibility = if (submitting) View.VISIBLE else View.GONE
    }
}
