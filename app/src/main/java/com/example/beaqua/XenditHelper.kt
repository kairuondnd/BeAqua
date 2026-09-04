package com.example.beaqua

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * XenditHelper using the Invoice API for better reliability and GCash support.
 * This API provides a hosted checkout page that includes GCash and other local methods.
 */
object XenditHelper {
    private const val TAG = "XenditHelper"
    private const val API_KEY = "xnd_development_3OJnMbuL1YBaZGFaz1GkQfnnMsZlSzNUmLhF6YzLrPqi4iB9WxiKXDbhgy1cahB"
    private const val BASE_URL = "https://api.xendit.co"
    private val client = OkHttpClient()
    private val gson = Gson()

    data class InvoiceRequest(
        @SerializedName("external_id") val externalId: String,
        @SerializedName("amount") val amount: Double,
        @SerializedName("description") val description: String,
        @SerializedName("currency") val currency: String = "PHP",
        @SerializedName("success_redirect_url") val successRedirectUrl: String,
        @SerializedName("failure_redirect_url") val failureRedirectUrl: String,
        @SerializedName("payment_methods") val paymentMethods: List<String> = listOf("GCASH")
    )

    data class InvoiceResponse(
        @SerializedName("id") val id: String = "",
        @SerializedName("invoice_url") val invoiceUrl: String = "",
        @SerializedName("status") val status: String = "",
        @SerializedName("external_id") val externalId: String = "",
        @SerializedName("amount") val amount: Double = 0.0
    )

    data class XenditError(
        @SerializedName("error_code") val errorCode: String?,
        @SerializedName("message") val message: String?
    )

    fun createGCashCharge(
        referenceId: String,
        amount: Double,
        successUrl: String,
        callback: (InvoiceResponse?, String?) -> Unit
    ) {
        // Round to 0 decimal places as required by some PH channels, or 2 for PHP.
        val finalAmount = Math.round(amount * 100.0) / 100.0
        
        if (finalAmount <= 0) {
            callback(null, "Invalid amount: ₱$finalAmount")
            return
        }

        val requestBody = InvoiceRequest(
            externalId = referenceId,
            amount = finalAmount,
            description = "BeAqua Payment - $referenceId",
            successRedirectUrl = successUrl,
            failureRedirectUrl = successUrl // Redirect back to app even on failure to handle it
        )

        val json = gson.toJson(requestBody)
        Log.d(TAG, "Request: $json")
        
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val auth = Base64.encodeToString("$API_KEY:".toByteArray(), Base64.NO_WRAP)

        val request = Request.Builder()
            .url("$BASE_URL/v2/invoices")
            .addHeader("Authorization", "Basic $auth")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Network Error", e)
                callback(null, "Connection error: ${e.localizedMessage}")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                Log.d(TAG, "Response Code: ${response.code}")
                Log.d(TAG, "Response Data: $responseData")

                if (response.isSuccessful && responseData != null) {
                    try {
                        val invoiceResponse = gson.fromJson(responseData, InvoiceResponse::class.java)
                        callback(invoiceResponse, null)
                    } catch (e: Exception) {
                        callback(null, "Processing error")
                    }
                } else {
                    val errorMsg = try {
                        val err = gson.fromJson(responseData, XenditError::class.java)
                        err.message ?: "Server Error ${response.code}"
                    } catch (e: Exception) {
                        "Payment initialization failed"
                    }
                    callback(null, errorMsg)
                }
            }
        })
    }

    fun getInvoice(
        invoiceId: String,
        callback: (InvoiceResponse?, String?) -> Unit
    ) {
        val auth = Base64.encodeToString("$API_KEY:".toByteArray(), Base64.NO_WRAP)
        val request = Request.Builder()
            .url("$BASE_URL/v2/invoices/$invoiceId")
            .addHeader("Authorization", "Basic $auth")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null, "Could not verify payment: ${e.localizedMessage}")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                if (response.isSuccessful && responseData != null) {
                    try {
                        callback(gson.fromJson(responseData, InvoiceResponse::class.java), null)
                    } catch (_: Exception) {
                        callback(null, "Could not read payment status")
                    }
                } else {
                    callback(null, "Payment verification failed")
                }
            }
        })
    }
}
