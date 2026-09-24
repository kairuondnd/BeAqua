package com.example.beaqua

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.Source
import java.io.File
import java.util.concurrent.Executors

/** Opens the exact saved delivery receipt linked by a notification. */
class ReceiptActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var retry: MaterialButton
    private lateinit var print: MaterialButton
    private lateinit var pages: RecyclerView
    private val executor = Executors.newSingleThreadExecutor()
    private var renderer: PdfRenderer? = null
    private var previewFile: File? = null
    private var receiptOrders = emptyList<Order>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(240, 245, 249))
        }
        val toolbar = Toolbar(this).apply { title = "Delivery receipt" }
        layout.addView(toolbar, LinearLayout.LayoutParams(-1, -2))
        status = TextView(this).apply { setPadding(24, 16, 24, 16); textSize = 15f }
        layout.addView(status)
        retry = MaterialButton(this).apply {
            text = "Retry"; visibility = View.GONE
            setOnClickListener { loadReceipt() }
        }
        layout.addView(retry)
        print = MaterialButton(this).apply {
            text = "Print / Save PDF"; isEnabled = false
            setOnClickListener { ReceiptHelper.printReceipt(this@ReceiptActivity, receiptOrders) }
        }
        layout.addView(print)
        pages = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@ReceiptActivity) }
        layout.addView(pages, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(layout)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
        loadReceipt()
    }

    private fun loadReceipt() {
        val username = intent.getStringExtra("USERNAME").orEmpty()
        val orderId = intent.getStringExtra("ORDER_ID").orEmpty()
        val session = getSharedPreferences("order_maintenance", Context.MODE_PRIVATE)
        if (username.isBlank() || orderId.isBlank() || session.getString("username", null) != username ||
            session.getBoolean("stationOwner", false)) {
            showError("Sign in to the customer account that placed this order, then open the notification again.")
            return
        }
        status.text = "Loading your receipt…"
        retry.visibility = View.GONE
        FirebaseHelper.ordersCollection.document(orderId).get(Source.SERVER)
            .continueWithTask { task ->
                if (!task.isSuccessful) throw task.exception ?: IllegalStateException("Could not load receipt")
                val order = task.result.toObject(Order::class.java)
                check(order != null && order.customerName == username && order.status == "Delivered") {
                    "This receipt is unavailable for this account."
                }
                val reference = order.deliveryReceipt?.reference
                check(!reference.isNullOrBlank()) { "The station has not generated this receipt yet." }
                FirebaseHelper.ordersCollection.whereEqualTo("deliveryReceipt.reference", reference).get(Source.SERVER)
            }.addOnSuccessListener { snapshot ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                val orders = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Order::class.java)?.apply { id = doc.id }
                }
                if (orders.isEmpty() || orders.none { it.id == orderId } ||
                    orders.any { it.customerName != username || it.status != "Delivered" } ||
                    groupCheckoutOrders(orders).size != 1) {
                    showError("Could not load the complete receipt. Please try again.")
                } else displayReceipt(orders)
            }.addOnFailureListener {
                if (!isFinishing && !isDestroyed) showError("Could not load your receipt. Check your connection and try again.")
            }
    }

    private fun showError(message: String) {
        status.text = message
        retry.visibility = View.VISIBLE
    }

    internal fun displayReceipt(orders: List<Order>) {
        receiptOrders = orders
        status.text = "Preparing your receipt…"
        retry.visibility = View.GONE
        executor.execute {
            var file: File? = null
            try {
                file = File.createTempFile("delivery-receipt-", ".pdf", cacheDir)
                val attributes = PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .setResolution(PrintAttributes.Resolution("preview", "Preview", 150, 150))
                    .setMinMargins(PrintAttributes.Margins(500, 500, 500, 500))
                    .setColorMode(PrintAttributes.COLOR_MODE_COLOR).build()
                file.outputStream().use { ReceiptDocument(this, attributes, orders).write(it) }
                val completed = file
                runOnUiThread {
                    if (isFinishing || isDestroyed) { completed.delete(); return@runOnUiThread }
                    try {
                        pages.adapter = null
                        renderer?.close()
                        previewFile?.delete()
                        previewFile = completed
                        val pdf = PdfRenderer(ParcelFileDescriptor.open(completed, ParcelFileDescriptor.MODE_READ_ONLY))
                        renderer = pdf
                        pages.adapter = ReceiptPages(pdf)
                        val receipt = orders.first().deliveryReceipt
                        status.text = if (receipt?.itemsChanged == true) {
                            "Order ${receipt.reference} delivered. The station updated the items or quantities in this receipt."
                        } else "Order ${receipt?.reference ?: orders.first().id} delivered."
                        print.isEnabled = true
                    } catch (_: Exception) { showError("Could not display your receipt. Please try again.") }
                }
            } catch (_: Exception) {
                file?.delete()
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) showError("Could not prepare your receipt. Please try again.")
                }
            }
        }
    }

    override fun onDestroy() {
        pages.adapter = null
        renderer?.close()
        previewFile?.delete()
        executor.shutdown()
        super.onDestroy()
    }

    private class ReceiptPages(private val pdf: PdfRenderer) : RecyclerView.Adapter<ReceiptPages.Holder>() {
        class Holder(val image: ImageView) : RecyclerView.ViewHolder(image)
        override fun getItemCount() = pdf.pageCount
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(ImageView(parent.context).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.WHITE)
            layoutParams = RecyclerView.LayoutParams(-1, -2).apply { setMargins(12, 8, 12, 8) }
        })
        override fun onBindViewHolder(holder: Holder, position: Int) {
            pdf.openPage(position).use { page ->
                val width = holder.image.resources.displayMetrics.widthPixels.coerceIn(600, 1440)
                val bitmap = Bitmap.createBitmap(width, (width.toLong() * page.height / page.width).toInt(), Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                holder.image.setImageBitmap(bitmap)
                holder.image.contentDescription = "Receipt page ${position + 1} of ${pdf.pageCount}"
            }
        }
        override fun onViewRecycled(holder: Holder) { holder.image.setImageDrawable(null) }
    }

    companion object {
        fun intent(context: Context, username: String, orderId: String): Intent =
            Intent(context, ReceiptActivity::class.java).apply {
                putExtra("USERNAME", username)
                putExtra("ORDER_ID", orderId)
                // PendingIntent identity includes this URI, so different orders never share a destination.
                data = Uri.Builder().scheme("beaqua").authority("receipt")
                    .appendPath(username).appendPath(orderId).build()
            }
    }
}
