package com.example.beaqua

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.tasks.Tasks
import com.google.android.material.button.MaterialButton

class KycSubmissionActivity : AppCompatActivity() {

    private var username: String = ""
    private var barangayClearanceUri: Uri? = null
    private var sanitaryPermitUri: Uri? = null
    private var mayorsPermitUri: Uri? = null

    private lateinit var barangayFile: TextView
    private lateinit var sanitaryFile: TextView
    private lateinit var mayorsFile: TextView

    private val barangayPicker = documentPicker { uri ->
        barangayClearanceUri = uri
        barangayFile.text = selectedFileName(uri)
    }
    private val sanitaryPicker = documentPicker { uri ->
        sanitaryPermitUri = uri
        sanitaryFile.text = selectedFileName(uri)
    }
    private val mayorsPicker = documentPicker { uri ->
        mayorsPermitUri = uri
        mayorsFile.text = selectedFileName(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kyc_submission)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = returnToLogin()
        })

        username = intent.getStringExtra("USERNAME").orEmpty()
        if (username.isBlank()) {
            finish()
            return
        }

        barangayFile = findViewById(R.id.tvKycSubmitBarangayFile)
        sanitaryFile = findViewById(R.id.tvKycSubmitSanitaryFile)
        mayorsFile = findViewById(R.id.tvKycSubmitMayorsFile)
        val submitButton = findViewById<MaterialButton>(R.id.btnSubmitKycDocuments)
        val progress = findViewById<ProgressBar>(R.id.kycSubmitProgress)

        findViewById<TextView>(R.id.tvKycSubmitAccount).text = "Station account: $username"
        findViewById<MaterialButton>(R.id.btnKycSubmitBarangay).setOnClickListener {
            barangayPicker.launch(arrayOf("application/pdf", "image/*"))
        }
        findViewById<MaterialButton>(R.id.btnKycSubmitSanitary).setOnClickListener {
            sanitaryPicker.launch(arrayOf("application/pdf", "image/*"))
        }
        findViewById<MaterialButton>(R.id.btnKycSubmitMayors).setOnClickListener {
            mayorsPicker.launch(arrayOf("application/pdf", "image/*"))
        }
        findViewById<MaterialButton>(R.id.btnCancelKycSubmission).setOnClickListener {
            returnToLogin()
        }

        submitButton.setOnClickListener {
            val barangay = barangayClearanceUri
            val sanitary = sanitaryPermitUri
            val mayors = mayorsPermitUri
            if (barangay == null || sanitary == null || mayors == null) {
                Toast.makeText(this, "Please attach all three required permits", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            setSubmitting(true, submitButton, progress)
            Tasks.whenAllSuccess<String>(
                listOf(
                    FirebaseHelper.uploadKycDocument(
                        this,
                        username,
                        "barangay_business_clearance",
                        barangay
                    ),
                    FirebaseHelper.uploadKycDocument(
                        this,
                        username,
                        "sanitary_permit",
                        sanitary
                    ),
                    FirebaseHelper.uploadKycDocument(
                        this,
                        username,
                        "mayors_business_permit",
                        mayors
                    )
                )
            ).addOnSuccessListener { urls ->
                FirebaseHelper.submitStationKycDocuments(
                    username,
                    urls[0],
                    urls[1],
                    urls[2]
                ).addOnSuccessListener {
                    Toast.makeText(
                        this,
                        "Documents submitted. Wait for administrator approval.",
                        Toast.LENGTH_LONG
                    ).show()
                    returnToLogin()
                }.addOnFailureListener { error ->
                    setSubmitting(false, submitButton, progress)
                    Toast.makeText(this, "Submission failed: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }.addOnFailureListener { error ->
                setSubmitting(false, submitButton, progress)
                Toast.makeText(this, "Upload failed: ${error.message}", Toast.LENGTH_LONG).show()
            }
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
                    // The URI remains readable for this submission session.
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
        submitButton.text = if (submitting) "Uploading..." else "Submit for Review"
        progress.visibility = if (submitting) View.VISIBLE else View.GONE
    }

    private fun returnToLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }
}
