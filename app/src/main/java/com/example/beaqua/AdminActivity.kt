package com.example.beaqua

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class AdminActivity : AppCompatActivity() {

    private val applications = mutableListOf<User>()
    private lateinit var adapter: KycApplicationAdapter
    private lateinit var progress: ProgressBar
    private lateinit var emptyMessage: TextView
    private lateinit var summary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        progress = findViewById(R.id.adminKycProgress)
        emptyMessage = findViewById(R.id.tvKycEmpty)
        summary = findViewById(R.id.tvKycSummary)

        adapter = KycApplicationAdapter(
            applications = applications,
            onViewDocument = ::openDocument,
            onApprove = { application -> confirmDecision(application, User.KYC_APPROVED) },
            onReject = { application -> confirmDecision(application, User.KYC_REJECTED) }
        )

        findViewById<RecyclerView>(R.id.rvKycApplications).apply {
            layoutManager = LinearLayoutManager(this@AdminActivity)
            adapter = this@AdminActivity.adapter
        }

        findViewById<MaterialButton>(R.id.btnRefreshKyc).setOnClickListener {
            loadApplications()
        }
        findViewById<MaterialButton>(R.id.btnLogoutAdmin).setOnClickListener {
            startActivity(
                Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
            finish()
        }

        loadApplications()
    }

    private fun loadApplications() {
        progress.visibility = View.VISIBLE
        emptyMessage.visibility = View.GONE
        FirebaseHelper.getStationOwners()
            .addOnSuccessListener { snapshot ->
                progress.visibility = View.GONE
                applications.clear()
                applications.addAll(
                    snapshot.toObjects(User::class.java).sortedWith(
                        compareBy<User> { statusPriority(it.kycStatus) }
                            .thenByDescending { it.kycSubmittedAt }
                    )
                )
                adapter.notifyDataSetChanged()
                updateSummary()
                emptyMessage.visibility =
                    if (applications.isEmpty()) View.VISIBLE else View.GONE
            }
            .addOnFailureListener { error ->
                progress.visibility = View.GONE
                emptyMessage.text = "Could not load KYC applications"
                emptyMessage.visibility = View.VISIBLE
                Toast.makeText(this, error.message ?: "KYC loading failed", Toast.LENGTH_LONG).show()
            }
    }

    private fun confirmDecision(application: User, newStatus: String) {
        val approving = newStatus == User.KYC_APPROVED
        if (approving && !application.hasCompleteKycDocuments()) {
            val missingDocuments = buildList {
                if (application.barangayClearanceUrl.isBlank()) {
                    add("Barangay Business Clearance")
                }
                if (application.sanitaryPermitUrl.isBlank()) add("Sanitary Permit")
                if (application.mayorsPermitUrl.isBlank()) add("Mayor's Business Permit")
            }
            AlertDialog.Builder(this)
                .setTitle("Cannot approve yet")
                .setMessage(
                    "The applicant must submit:\n\n" +
                        missingDocuments.joinToString(separator = "\n") { "• $it" }
                )
                .setPositiveButton("OK", null)
                .show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(if (approving) "Approve station?" else "Reject station?")
            .setMessage(
                if (approving) {
                    "${application.name} will appear in the app and gain Station Owner access."
                } else {
                    "${application.name} will remain blocked and hidden from customers."
                }
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton(if (approving) "Approve" else "Reject") { _, _ ->
                updateStatus(application, newStatus)
            }
            .show()
    }

    private fun updateStatus(application: User, newStatus: String) {
        FirebaseHelper.updateStationKycStatus(application.username, newStatus)
            .addOnSuccessListener {
                application.kycStatus = newStatus
                application.kycReviewedAt = System.currentTimeMillis()
                applications.sortWith(
                    compareBy<User> { statusPriority(it.kycStatus) }
                        .thenByDescending { it.kycSubmittedAt }
                )
                adapter.notifyDataSetChanged()
                updateSummary()
                Toast.makeText(
                    this,
                    if (newStatus == User.KYC_APPROVED) {
                        "Station approved"
                    } else {
                        "Station rejected"
                    },
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener { error ->
                Toast.makeText(
                    this,
                    "Could not update application: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun openDocument(application: User, url: String) {
        if (url.isBlank()) {
            Toast.makeText(this, "Document was not submitted", Toast.LENGTH_SHORT).show()
            return
        }
        if (FirestoreKycDocumentStore.isFirestoreReference(url)) {
            Toast.makeText(this, "Preparing secure document...", Toast.LENGTH_SHORT).show()
            FirestoreKycDocumentStore.download(this, url)
                .addOnSuccessListener(::openDownloadedDocument)
                .addOnFailureListener { error ->
                    Toast.makeText(
                        this,
                        "Could not open ${application.name}'s document: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            return
        }
        SupabaseHelper.createKycSignedUrl(this, url)
            .addOnSuccessListener(::openSignedDocument)
            .addOnFailureListener { error ->
                Toast.makeText(
                    this,
                    "Could not open ${application.name}'s document: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun openSignedDocument(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No app can open this document", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openDownloadedDocument(document: DownloadedKycDocument) {
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(document.uri, document.mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No app can open this document", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateSummary() {
        val pending = applications.count { it.kycStatus == User.KYC_PENDING }
        val approved = applications.count { it.kycStatus == User.KYC_APPROVED }
        val rejected = applications.count { it.kycStatus == User.KYC_REJECTED }
        val unverified = applications.size - pending - approved - rejected
        summary.text = if (unverified > 0) {
            "$pending pending  •  $approved approved & active  •  $rejected rejected  •  $unverified unverified"
        } else {
            "$pending pending  •  $approved approved & active  •  $rejected rejected"
        }
    }

    private fun statusPriority(status: String): Int = when (status) {
        User.KYC_PENDING -> 0
        User.KYC_REJECTED -> 1
        User.KYC_APPROVED -> 2
        else -> 3
    }
}
