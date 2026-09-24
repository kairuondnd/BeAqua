package com.example.beaqua

import android.os.Bundle
import android.graphics.Typeface
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.text.util.Linkify
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat

class PrivacyNoticeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(this@PrivacyNoticeActivity, R.color.white))
        }
        val toolbar = Toolbar(this).apply { setTitle(R.string.privacy_notice_title) }
        root.addView(toolbar)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(32))
        }
        fun paragraph(value: String, heading: Boolean = false) {
            content.addView(TextView(this).apply {
                text = value
                textSize = if (heading) 18f else 15f
                setTextColor(ContextCompat.getColor(this@PrivacyNoticeActivity, R.color.text_primary))
                if (heading) setTypeface(typeface, Typeface.BOLD)
                setPadding(0, if (heading) dp(20) else dp(8), 0, dp(4))
                setLineSpacing(dp(3).toFloat(), 1f)
                setTextIsSelectable(true)
                autoLinkMask = Linkify.EMAIL_ADDRESSES
            })
        }
        paragraph("Version ${PrivacyNotice.VERSION}")
        val operator = getString(R.string.privacy_operator)
        val email = getString(R.string.privacy_contact_email)
        val retention = getString(R.string.privacy_retention_policy)
        val basis = getString(R.string.privacy_processing_basis)
        if (listOf(operator, email, retention, basis).any { it.isBlank() }) paragraph(getString(R.string.privacy_notice_draft))
        fun section(heading: Int, body: Int) { paragraph(getString(heading), true); paragraph(getString(body)) }
        section(R.string.privacy_data_heading, R.string.privacy_data_body)
        section(R.string.privacy_purpose_heading, R.string.privacy_purpose_body)
        section(R.string.privacy_sharing_heading, R.string.privacy_sharing_body)
        section(R.string.privacy_choices_heading, R.string.privacy_choices_body)
        paragraph(getString(R.string.privacy_retention_heading), true)
        paragraph(retention.ifBlank { getString(R.string.privacy_retention_pending) })
        paragraph(getString(R.string.privacy_basis_heading), true)
        paragraph(basis.ifBlank { getString(R.string.privacy_basis_pending) })
        paragraph(getString(R.string.privacy_contact_heading), true)
        paragraph(if (operator.isBlank() || email.isBlank()) getString(R.string.privacy_contact_pending) else "$operator\n$email")
        root.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
    }
}
