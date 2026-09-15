package com.example.beaqua

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.ListenerRegistration

class PendingOrdersActivity : AppCompatActivity() {

    private lateinit var username: String
    private lateinit var rvPendingOrders: RecyclerView
    private lateinit var layoutEmpty: View
    private var ordersListener: ListenerRegistration? = null
    private val ordersList = mutableListOf<Order>()
    private lateinit var adapter: PendingOrdersAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pending_orders)

        username = intent.getStringExtra("USERNAME") ?: ""

        val btnBack = findViewById<ImageButton>(R.id.btnBackPending)
        btnBack.setOnClickListener { finish() }

        rvPendingOrders = findViewById(R.id.rvPendingOrders)
        layoutEmpty = findViewById(R.id.headerPending)
        
        rvPendingOrders.layoutManager = LinearLayoutManager(this)
        adapter = PendingOrdersAdapter(ordersList) { order ->
            ReceiptHelper.printReceipt(this, order)
        }
        rvPendingOrders.adapter = adapter

        if (username.isNotEmpty()) {
            startListeningForOrders()
        } else {
            Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun startListeningForOrders() {
        ordersListener = FirebaseHelper.ordersCollection
            .whereEqualTo("customerName", username)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    fetchOrdersFallback()
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    ordersList.clear()
                    for (doc in snapshots.documents) {
                        val order = doc.toObject(Order::class.java)
                        if (order != null) {
                            order.id = doc.id
                            if (order.status == "Pending" || order.status == "Accepted") {
                                ordersList.add(order)
                            }
                        }
                    }
                    ordersList.sortByDescending { it.timestamp }
                    adapter.notifyDataSetChanged()
                }
            }
    }

    private fun fetchOrdersFallback() {
        FirebaseHelper.ordersCollection
            .whereEqualTo("customerName", username)
            .get()
            .addOnSuccessListener { snapshots ->
                ordersList.clear()
                for (doc in snapshots.documents) {
                    val order = doc.toObject(Order::class.java)
                    if (order != null) {
                        order.id = doc.id
                        if (order.status == "Pending" || order.status == "Accepted") {
                            ordersList.add(order)
                        }
                    }
                }
                ordersList.sortByDescending { it.timestamp }
                adapter.notifyDataSetChanged()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        ordersListener?.remove()
    }

    class PendingOrdersAdapter(
        private val orders: List<Order>,
        private val onPrintReceipt: (Order) -> Unit
    ) : RecyclerView.Adapter<PendingOrdersAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivContainerImage: ImageView = view.findViewById(R.id.ivPendingIcon)
            val tvProductName: TextView = view.findViewById(R.id.tvPendingProductName)
            val tvDetails: TextView = view.findViewById(R.id.tvPendingDetails)
            val tvPayment: TextView = view.findViewById(R.id.tvPendingPaymentMethod)
            val tvSlot: TextView = view.findViewById(R.id.tvPendingSlot)
            val btnPrintReceipt: MaterialButton = view.findViewById(R.id.btnPrintReceiptPending)
            val cvStatusBadge: MaterialCardView = view.findViewById(R.id.cvStatusBadge)
            val tvStatusBadge: TextView = view.findViewById(R.id.tvStatusBadge)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_pending_order, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val order = orders[position]
            ContainerImageLoader.load(holder.ivContainerImage, order.imageUri)
            val refillPrefix = if (order.isRefill()) "REFILL • " else ""
            holder.tvProductName.text = if (order.isSubscriptionOrder) {
                "AUTOMATED • $refillPrefix${order.productName}"
            } else {
                "$refillPrefix${order.productName}"
            }
            holder.tvDetails.text = if (order.isRefill()) {
                "Empty containers: ${order.emptyContainerCount.coerceAtLeast(order.quantity)} | Type: ${order.containerType}" +
                    if (order.refillInstructions.isBlank()) "" else "\nNotes: ${order.refillInstructions}"
            } else {
                "Quantity: ${order.quantity} | Container: ${order.containerType}"
            }
            holder.tvPayment.text = "Payment: ${order.paymentMethod}${if (order.isPaid) " (PAID)" else ""}"
            
            val statusDisplay = when (order.status) {
                "Accepted" -> "Accepted (Preparing)"
                else -> "Awaiting Confirmation"
            }
            holder.tvSlot.text =
                "Estimated delivery: ${order.deliveryTimeSlot}\nStatus: $statusDisplay"

            holder.tvStatusBadge.text = order.status.uppercase()
            holder.cvStatusBadge.visibility = View.VISIBLE

            if (order.isPaid) {
                holder.btnPrintReceipt.visibility = View.VISIBLE
                holder.btnPrintReceipt.setOnClickListener { onPrintReceipt(order) }
            } else {
                holder.btnPrintReceipt.visibility = View.GONE
            }
        }

        override fun getItemCount(): Int = orders.size
    }
}
