package com.example.beaqua

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class PremiumActivity : AppCompatActivity() {

    companion object {
        const val DURATION_MILLIS = 30L * 24L * 60L * 60L * 1000L
        const val EXTRA_USERNAME = "USERNAME"
        const val EXTRA_STATION_USERNAME = "STATION_USERNAME"
        private const val PREFS = "beaqua_premium_payment"
        private const val KEY_INVOICE_ID = "invoice_id"
        private const val KEY_REFERENCE = "reference"
        private const val KEY_USERNAME = "username"
        private const val KEY_STATION_USERNAME = "station_username"
        private const val KEY_STATION_NAME = "station_name"
        private const val KEY_PRICE = "price"
    }

    private lateinit var username: String
    private lateinit var stationOwnerUsername: String
    private lateinit var purchaseButton: MaterialButton
    private lateinit var statusText: TextView
    private lateinit var expiryText: TextView
    private lateinit var progress: View
    private var currentStation: User? = null
    private var currentMembership: StationPremiumMembership? = null
    private var isVerifying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_premium)

        username = intent.getStringExtra(EXTRA_USERNAME)
            ?: intent.data?.getQueryParameter("username")
            ?: ""
        stationOwnerUsername = intent.getStringExtra(EXTRA_STATION_USERNAME)
            ?: intent.data?.getQueryParameter("station")
            ?: ""
        if (username.isBlank() || stationOwnerUsername.isBlank()) {
            Toast.makeText(this, "Choose a water station first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        purchaseButton = findViewById(R.id.btnPurchasePremium)
        statusText = findViewById(R.id.tvPremiumStatus)
        expiryText = findViewById(R.id.tvPremiumExpiry)
        progress = findViewById(R.id.premiumProgress)

        findViewById<ImageButton>(R.id.btnBackPremium).setOnClickListener { finish() }
        purchaseButton.setOnClickListener { startPurchase() }
        loadMembership()
        handlePaymentReturn(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePaymentReturn(intent)
    }

    private fun loadMembership() {
        setLoading(true)
        FirebaseHelper.getUser(stationOwnerUsername)
            .addOnSuccessListener { snapshot ->
                val station = snapshot.toObject(User::class.java)
                if (station?.isApprovedStationOwner() != true) {
                    setLoading(false)
                    Toast.makeText(this, "This water station is not active", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }
                currentStation = station
                FirebaseHelper.getStationPremiumMembership(username, stationOwnerUsername)
                    .addOnSuccessListener { membershipSnapshot ->
                        currentMembership = membershipSnapshot
                            .toObject(StationPremiumMembership::class.java)
                        renderMembership()
                        setLoading(false)
                    }
                    .addOnFailureListener {
                        setLoading(false)
                        Toast.makeText(this, "Could not load Premium status", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                setLoading(false)
                Toast.makeText(this, "Could not load water station", Toast.LENGTH_SHORT).show()
            }
    }

    private fun renderMembership() {
        val station = currentStation ?: return
        val stationName = station.name.ifBlank { station.username }
        val priceLabel = formatPrice(station.premiumPrice)
        if (currentMembership?.isActiveFor(username, stationOwnerUsername) == true) {
            val formatted = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
                .format(Date(currentMembership!!.expiresAt))
            statusText.text = "Premium is active for $stationName"
            expiryText.text = "Access for this station is valid until $formatted"
            purchaseButton.text = "Extend $stationName Premium for $priceLabel"
        } else {
            statusText.text = "Unlock automatic weekly deliveries from $stationName"
            expiryText.text = "$priceLabel for 30 days at this station"
            purchaseButton.text = "Get $stationName Premium — $priceLabel"
        }
    }

    private fun startPurchase() {
        val station = currentStation ?: return
        val price = station.premiumPrice
        if (price <= 0.0) {
            Toast.makeText(
                this,
                "This station has not set a valid Premium price",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val reference = "BQA-PREMIUM-${UUID.randomUUID()}"
        val successUrl = "beaqua://premium-callback?username=${Uri.encode(username)}" +
            "&station=${Uri.encode(stationOwnerUsername)}"
        setLoading(true)
        Toast.makeText(this, "Preparing secure GCash checkout…", Toast.LENGTH_SHORT).show()

        XenditHelper.createGCashCharge(reference, price, successUrl) { response, error ->
            runOnUiThread {
                setLoading(false)
                if (error != null || response == null || response.invoiceUrl.isBlank()) {
                    Toast.makeText(
                        this,
                        error ?: "Could not open payment",
                        Toast.LENGTH_LONG
                    ).show()
                    return@runOnUiThread
                }

                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString(KEY_INVOICE_ID, response.id)
                    .putString(KEY_REFERENCE, reference)
                    .putString(KEY_USERNAME, username)
                    .putString(KEY_STATION_USERNAME, stationOwnerUsername)
                    .putString(KEY_STATION_NAME, station.name.ifBlank { station.username })
                    .putLong(KEY_PRICE, java.lang.Double.doubleToRawLongBits(price))
                    .apply()
                CustomTabsIntent.Builder().build()
                    .launchUrl(this, Uri.parse(response.invoiceUrl))
            }
        }
    }

    private fun handlePaymentReturn(intent: Intent) {
        val data = intent.data ?: return
        if (data.scheme != "beaqua" || data.host != "premium-callback" || isVerifying) return

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val invoiceId = prefs.getString(KEY_INVOICE_ID, null)
        val reference = prefs.getString(KEY_REFERENCE, null)
        val paymentUsername = prefs.getString(KEY_USERNAME, null)
        val paymentStation = prefs.getString(KEY_STATION_USERNAME, null)
        val paymentStationName = prefs.getString(KEY_STATION_NAME, stationOwnerUsername)
            ?: stationOwnerUsername
        val paidPrice = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_PRICE, 0L))
        if (
            invoiceId.isNullOrBlank() ||
            reference.isNullOrBlank() ||
            paymentUsername != username ||
            paymentStation != stationOwnerUsername ||
            paidPrice <= 0.0
        ) {
            showPaymentNotCompleted()
            return
        }

        isVerifying = true
        setLoading(true)
        XenditHelper.getInvoice(invoiceId) { invoice, error ->
            runOnUiThread {
                if (error != null || invoice == null) {
                    isVerifying = false
                    setLoading(false)
                    Toast.makeText(this, error ?: "Could not verify payment", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }

                if (invoice.status != "PAID" && invoice.status != "SETTLED") {
                    isVerifying = false
                    setLoading(false)
                    showPaymentNotCompleted()
                    return@runOnUiThread
                }
                if (invoice.externalId != reference || kotlin.math.abs(invoice.amount - paidPrice) > 0.009) {
                    isVerifying = false
                    setLoading(false)
                    Toast.makeText(
                        this,
                        "The verified payment does not match this station purchase.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@runOnUiThread
                }

                FirebaseHelper.activateStationPremium(
                    customerUsername = username,
                    stationOwnerUsername = stationOwnerUsername,
                    pricePaid = paidPrice,
                    paymentReference = reference,
                    durationMillis = DURATION_MILLIS
                ).addOnSuccessListener { expiresAt ->
                    prefs.edit().clear().apply()
                    isVerifying = false
                    setLoading(false)
                    currentMembership = StationPremiumMembership(
                        customerUsername = username,
                        stationOwnerUsername = stationOwnerUsername,
                        stationName = paymentStationName,
                        pricePaid = paidPrice,
                        purchasedAt = System.currentTimeMillis(),
                        expiresAt = expiresAt,
                        paymentReference = reference
                    )
                    renderMembership()
                    AlertDialog.Builder(this)
                        .setTitle("Welcome to BeAqua Premium")
                        .setMessage(
                            "Weekly automatic delivery from $paymentStationName is now " +
                                "unlocked for 30 days."
                        )
                        .setPositiveButton("Set a weekly delivery") { _, _ -> finish() }
                        .show()
                }.addOnFailureListener {
                    isVerifying = false
                    setLoading(false)
                    Toast.makeText(
                        this,
                        "Payment was verified, but activation failed. Please try again.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun showPaymentNotCompleted() {
        AlertDialog.Builder(this)
            .setTitle("Payment not completed")
            .setMessage("BeAqua Premium will activate only after GCash confirms the payment.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        purchaseButton.isEnabled = !loading
    }

    private fun formatPrice(price: Double): String {
        return "\u20B1" + String.format(Locale.getDefault(), "%,.2f", price)
    }
}
