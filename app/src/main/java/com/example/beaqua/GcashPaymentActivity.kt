package com.example.beaqua

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

/** Collects proof only. The station must verify the transfer in its own account. */
class GcashPaymentActivity : AppCompatActivity() {
    private var receipt: Uri? = null
    private var uploadedUrl: String? = null
    private var busy = false
    private lateinit var preview: ImageView
    private lateinit var message: TextView
    private lateinit var attach: Button
    private lateinit var submit: Button
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            receipt = uri
            uploadedUrl = null
            preview.setImageURI(uri)
            message.text = "Receipt attached. Submit for the station to verify your payment."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BackNavigation.install(this) { busy }
        receipt = savedInstanceState?.getString("receipt")?.let(Uri::parse)
        uploadedUrl = savedInstanceState?.getString("uploadedUrl")
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        fun label(value: String) = TextView(this).apply { text = value; textSize = 16f; layout.addView(this) }
        layout.addView(Button(this).apply { text = "Back to cart"; setOnClickListener { if (!busy) finish() } })
        label("Pay with GCash\n${intent.getStringExtra("stationName").orEmpty()}\nAmount: ₱${String.format("%,.2f", intent.getDoubleExtra("amount", 0.0))}\n\nScan or save this QR and pay in GCash. Check the recipient and amount. After paying, attach a screenshot of your GCash payment receipt below.")
        layout.addView(ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, (280 * resources.displayMetrics.density).toInt())
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Station GCash QR code"
            ContainerImageLoader.load(this, intent.getStringExtra("qr").orEmpty())
        })
        attach = Button(this).apply { text = "Attach GCash receipt screenshot"; setOnClickListener { picker.launch(arrayOf("image/*")) } }
        layout.addView(attach)
        preview = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, (240 * resources.displayMetrics.density).toInt())
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Selected payment receipt"
            receipt?.let { setImageURI(it) }
        }
        layout.addView(preview)
        message = label("A receipt screenshot is required. Your order stays pending until the station verifies payment. Do not pay again if submission fails; contact the station through chat.")
        submit = Button(this).apply { text = "I've paid — submit order"; setOnClickListener { submitReceipt() } }
        layout.addView(submit)
        setContentView(ScrollView(this).apply { addView(layout) })
    }

    private fun submitReceipt() {
        if (busy) return
        val uri = receipt ?: run { message.text = "Please attach your GCash receipt screenshot before submitting."; return }
        uploadedUrl?.let { complete(it); return }
        busy = true
        attach.isEnabled = false
        submit.isEnabled = false
        message.text = "Uploading receipt…"
        Thread {
            val result = runCatching {
                val bytes = contentResolver.openInputStream(uri)?.use { stream ->
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var count = stream.read(buffer)
                    while (count != -1) {
                        require(out.size() + count <= 10 * 1024 * 1024) { "Choose an image smaller than 10 MB." }
                        out.write(buffer, 0, count)
                        count = stream.read(buffer)
                    }
                    out.toByteArray()
                } ?: error("Cannot read this image. Please attach it again.")
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Please select a valid receipt image." }
                bytes to bounds.outMimeType
            }
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                result.fold(onSuccess = { (bytes, mime) ->
                    val ref = FirebaseStorage.getInstance().reference.child("gcash_receipts/${UUID.randomUUID()}")
                    val metadata = com.google.firebase.storage.StorageMetadata.Builder().setContentType(mime).build()
                    ref.putBytes(bytes, metadata).continueWithTask { task ->
                        if (!task.isSuccessful) throw task.exception ?: IllegalStateException("Upload failed")
                        ref.downloadUrl
                    }.addOnSuccessListener { uploadedUrl = it.toString(); complete(it.toString()) }
                        .addOnFailureListener { failed(it.message) }
                }, onFailure = { failed(it.message) })
            }
        }.start()
    }

    private fun failed(reason: String?) {
        busy = false
        attach.isEnabled = true
        submit.isEnabled = true
        message.text = "Receipt upload failed: ${reason.orEmpty()}\nYour order has not been submitted. Try again. Do not pay again."
    }

    private fun complete(url: String) {
        if (isDestroyed || isFinishing) return
        setResult(Activity.RESULT_OK, Intent().putExtra("receiptUrl", url)
            .putExtra("amount", intent.getDoubleExtra("amount", 0.0))
            .putExtra("stationUsername", intent.getStringExtra("stationUsername")))
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("receipt", receipt?.toString())
        outState.putString("uploadedUrl", uploadedUrl)
        super.onSaveInstanceState(outState)
    }
}
