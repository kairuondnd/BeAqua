package com.example.beaqua

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class KycApplicationAdapter(
    private val applications: List<User>,
    private val onViewDocument: (User, String) -> Unit,
    private val onApprove: (User) -> Unit,
    private val onReject: (User) -> Unit
) : RecyclerView.Adapter<KycApplicationAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val stationName: TextView = view.findViewById(R.id.tvKycStationName)
        val applicantDetails: TextView = view.findViewById(R.id.tvKycApplicantDetails)
        val status: TextView = view.findViewById(R.id.tvKycStatus)
        val barangayButton: MaterialButton = view.findViewById(R.id.btnViewBarangayClearance)
        val sanitaryButton: MaterialButton = view.findViewById(R.id.btnViewSanitaryPermit)
        val mayorsButton: MaterialButton = view.findViewById(R.id.btnViewMayorsPermit)
        val approveButton: MaterialButton = view.findViewById(R.id.btnApproveKyc)
        val rejectButton: MaterialButton = view.findViewById(R.id.btnRejectKyc)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_kyc_application, parent, false)
        )
    }

    override fun getItemCount(): Int = applications.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val application = applications[position]
        holder.stationName.text = application.name.ifBlank { "Unnamed water station" }
        holder.applicantDetails.text = buildString {
            append("Username: ${application.username}")
            if (application.ownerFullName.isNotBlank()) {
                append("\nProprietor: ${application.ownerFullName}")
            }
            if (application.contactNumber.isNotBlank()) append("\nContact: ${application.contactNumber}")
            if (application.emailAddress.isNotBlank()) append("\nEmail: ${application.emailAddress}")
            if (application.address.isNotBlank()) append("\nAddress: ${application.address}")
        }
        holder.status.text = application.stationAccountStatusLabel()

        bindDocumentButton(
            holder.barangayButton,
            application,
            application.barangayClearanceUrl
        )
        bindDocumentButton(holder.sanitaryButton, application, application.sanitaryPermitUrl)
        bindDocumentButton(holder.mayorsButton, application, application.mayorsPermitUrl)

        holder.approveButton.isEnabled = application.kycStatus != User.KYC_APPROVED
        holder.rejectButton.isEnabled = application.kycStatus != User.KYC_REJECTED
        holder.approveButton.text = if (application.kycStatus == User.KYC_APPROVED) {
            "Approved & Active"
        } else {
            "Approve"
        }
        holder.approveButton.setOnClickListener { onApprove(application) }
        holder.rejectButton.setOnClickListener { onReject(application) }
    }

    private fun bindDocumentButton(button: MaterialButton, application: User, url: String) {
        button.isEnabled = url.isNotBlank()
        button.setOnClickListener { onViewDocument(application, url) }
    }
}
