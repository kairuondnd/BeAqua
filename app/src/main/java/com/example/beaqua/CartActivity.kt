package com.example.beaqua

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.*

class CartActivity : AppCompatActivity() {

    private lateinit var rvCartItems: RecyclerView
    private lateinit var tvCartTotal: TextView
    private lateinit var btnCheckout: MaterialButton
    private lateinit var emptyCartState: LinearLayout
    private lateinit var cartAdapter: CartAdapter
    private val cartItems = mutableListOf<CartItem>()
    private var currentUser: User? = null
    
    private lateinit var layoutRushOrder: LinearLayout
    private lateinit var switchRushOrder: SwitchMaterial
    private lateinit var tvRushFeeNote: TextView
    private lateinit var tvDeliveryFeeCart: TextView
    private var currentStationRushFee: Double = 0.0
    private var currentStationDeliveryFee: Double = 0.0
    private var isRushEnabledAtStation: Boolean = false

    private var isFinalizing = false
    private var isCheckingItems = false
    private var completedCheckoutItems = emptyList<CartItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getString("completedCheckoutItems")?.let { json ->
            val items = org.json.JSONArray(json)
            completedCheckoutItems = (0 until items.length()).map { index ->
                val item = items.getJSONObject(index)
                CartItem(productId = item.getString("productId"), productName = item.getString("productName"),
                    quantity = item.getInt("quantity"), stationOwnerUsername = item.getString("stationOwnerUsername"),
                    stationName = item.getString("stationName"), offeringType = item.getString("offeringType"),
                    deliveryTimeSlot = item.getString("deliveryTimeSlot"), refillInstructions = item.getString("refillInstructions"))
            }
        }
        setContentView(R.layout.activity_cart)

        val toolbar = findViewById<Toolbar>(R.id.toolbarCart)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        rvCartItems = findViewById(R.id.rvCartItems)
        tvCartTotal = findViewById(R.id.tvCartTotal)
        btnCheckout = findViewById(R.id.btnCheckout)
        emptyCartState = findViewById(R.id.emptyCartState)
        
        layoutRushOrder = findViewById(R.id.layoutRushOrder)
        switchRushOrder = findViewById(R.id.switchRushOrderCheckout)
        tvRushFeeNote = findViewById(R.id.tvRushFeeNote)
        tvDeliveryFeeCart = findViewById(R.id.tvDeliveryFeeCart)
        
        rvCartItems.layoutManager = LinearLayoutManager(this)

        val intentUser = intent.getStringExtra("USERNAME")
        val username = intentUser ?: ""

        if (username.isNotEmpty()) {
            FirebaseHelper.getUser(username).addOnSuccessListener { document ->
                currentUser = document.toObject(User::class.java)
                if (completedCheckoutItems.isNotEmpty()) {
                    updateUI()
                    showRecurringRecommendation()
                } else loadCartItems()
            }.addOnFailureListener {
                Toast.makeText(this, "Session error", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            finish()
        }

        btnCheckout.setOnClickListener { showPaymentMethodDialog() }
        findViewById<MaterialButton>(R.id.btnStartShopping).setOnClickListener { finish() }
        switchRushOrder.setOnCheckedChangeListener { _, _ -> updateTotal() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val items = org.json.JSONArray()
        completedCheckoutItems.forEach { item ->
            items.put(org.json.JSONObject().apply {
                put("productId", item.productId); put("productName", item.productName)
                put("quantity", item.quantity); put("stationOwnerUsername", item.stationOwnerUsername)
                put("stationName", item.stationName); put("offeringType", item.offeringType)
                put("deliveryTimeSlot", item.deliveryTimeSlot); put("refillInstructions", item.refillInstructions)
            })
        }
        outState.putString("completedCheckoutItems", items.toString())
    }

    private fun finishCheckout() {
        completedCheckoutItems = emptyList()
        startActivity(Intent(this, UserHomeActivity::class.java).apply {
            putExtra("USERNAME", currentUser?.username)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        })
        finish()
    }

    private fun showRecurringRecommendation() {
        if (isFinishing || isDestroyed) return
        val repeatableItems = completedCheckoutItems.filter { it.offeringType == OFFERING_PURCHASE }
        if (repeatableItems.isEmpty()) {
            finishCheckout()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Order placed! Make it recurring?")
            .setMessage("Get your water delivered regularly. Choose how many days between deliveries and review the quantity and first delivery date.\n\nFuture deliveries use Cash on Delivery at current station prices and delivery fees. Your order today is already placed.")
            .setNegativeButton("No thanks") { _, _ -> finishCheckout() }
            .setPositiveButton("Continue") { _, _ ->
                if (repeatableItems.size == 1) {
                    customizeCheckedOutItem(repeatableItems.first())
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("Choose an item to repeat")
                        .setItems(repeatableItems.map { "${it.quantity} × ${it.productName}" }.toTypedArray()) { _, which ->
                            customizeCheckedOutItem(repeatableItems[which])
                        }
                        .setNegativeButton("No thanks") { _, _ -> finishCheckout() }
                        .setOnCancelListener { finishCheckout() }
                        .show()
                }
            }
            .setOnCancelListener { finishCheckout() }
            .show()
    }

    private fun customizeCheckedOutItem(item: CartItem) {
        val user = currentUser ?: return
        FirebaseHelper.getUser(item.stationOwnerUsername).addOnSuccessListener { snapshot ->
            if (isFinishing || isDestroyed) return@addOnSuccessListener
            val station = snapshot.toObject(User::class.java)
            if (station == null) {
                showRecurrenceLoadError(item)
                return@addOnSuccessListener
            }
            RecurringDeliveryEditor.show(this, user, station, checkoutItem = item,
                onCancelled = { finishCheckout() }, onSaved = {
                    DeliveryReminderWorker.start(this, user.username)
                    Toast.makeText(this, "Automated delivery saved. You can manage it from Automated Deliveries.", Toast.LENGTH_LONG).show()
                    finishCheckout()
                })
        }.addOnFailureListener { if (!isFinishing && !isDestroyed) showRecurrenceLoadError(item) }
    }

    private fun showRecurrenceLoadError(item: CartItem) {
        AlertDialog.Builder(this)
            .setTitle("Could not load delivery options")
            .setMessage("Your order was placed successfully. Retry to set up future deliveries, or skip for now.")
            .setPositiveButton("Retry") { _, _ -> customizeCheckedOutItem(item) }
            .setNegativeButton("No thanks") { _, _ -> finishCheckout() }
            .setOnCancelListener { finishCheckout() }
            .show()
    }

    private fun loadCartItems() {
        val user = currentUser ?: return
        FirebaseHelper.getCartItems(user.username).addOnSuccessListener { result ->
            cartItems.clear()
            for (doc in result) {
                val item = doc.toObject(CartItem::class.java)
                item.id = doc.id 
                cartItems.add(item)
            }
            
            if (cartItems.isNotEmpty()) {
                val stationUsername = cartItems[0].stationOwnerUsername
                FirebaseHelper.getUser(stationUsername).addOnSuccessListener { doc ->
                    val stationOwner = doc.toObject(User::class.java)
                    if (stationOwner?.isApprovedStationOwner() == true) {
                        currentStationDeliveryFee = stationOwner.deliveryFee
                        tvDeliveryFeeCart.text = String.format("₱%.2f", currentStationDeliveryFee)
                        
                        if (stationOwner.rushOrderEnabled) {
                            isRushEnabledAtStation = true
                            currentStationRushFee = stationOwner.rushOrderFee
                            layoutRushOrder.visibility = View.VISIBLE
                            tvRushFeeNote.text = "Additional fee: ₱${String.format("%.2f", currentStationRushFee)}"
                        } else {
                            isRushEnabledAtStation = false
                            currentStationRushFee = 0.0
                            switchRushOrder.isChecked = false
                            layoutRushOrder.visibility = View.GONE
                        }
                    } else {
                        Toast.makeText(
                            this,
                            "This water station is no longer available",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    updateUI()
                    
                }
            } else {
                updateUI()
            }
        }
    }

    private fun updateUI() {
        if (cartItems.isEmpty()) {
            emptyCartState.visibility = View.VISIBLE
            rvCartItems.visibility = View.GONE
            findViewById<View>(R.id.cardCheckout).visibility = View.GONE
        } else {
            emptyCartState.visibility = View.GONE
            rvCartItems.visibility = View.VISIBLE
            findViewById<View>(R.id.cardCheckout).visibility = View.VISIBLE
            
            cartAdapter = CartAdapter(cartItems) { item ->
                if (!isFinalizing && !isCheckingItems) FirebaseHelper.removeCartItem(item.id).addOnSuccessListener {
                    cartAdapter.removeItem(item)
                    updateTotal()
                    if (cartItems.isEmpty()) {
                        layoutRushOrder.visibility = View.GONE
                        updateUI()
                    }
                }
            }
            rvCartItems.adapter = cartAdapter
            updateTotal()
        }
    }

    private fun calculateTotal(): Double {
        var total = 0.0
        for (item in cartItems) {
            total += (item.productPrice * item.quantity)
        }
        total += currentStationDeliveryFee
        if (switchRushOrder.isChecked && isRushEnabledAtStation) {
            total += currentStationRushFee
        }
        return total
    }

    private fun updateTotal() {
        val total = calculateTotal()
        tvCartTotal.text = "₱${String.format("%.2f", total)}"
    }

    private fun showPaymentMethodDialog() {
        if (cartItems.isEmpty() || isFinalizing || isCheckingItems) return
        isCheckingItems = true
        btnCheckout.isEnabled = false
        
        checkItemsStillOffered { isAvailable ->
            isCheckingItems = false
            btnCheckout.isEnabled = true
            if (!isAvailable) return@checkItemsStillOffered

            val paymentMethods = resources.getStringArray(R.array.payment_methods)
            AlertDialog.Builder(this)
                .setTitle("Select Payment Method")
                .setItems(paymentMethods) { _, which ->
                    val selectedPayment = paymentMethods[which]
                    if (selectedPayment == "GCash") {
                        initiateGCashPayment()
                    } else {
                        finalizeOrders(selectedPayment, isPaid = false)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun checkItemsStillOffered(onResult: (Boolean) -> Unit) {
        val checkedItems = cartItems.map { it.copy() }
        val itemChecks = checkedItems.map { item ->
            if (item.offeringType == OFFERING_REFILL) {
                FirebaseHelper.usersCollection.document(item.stationOwnerUsername).get()
            } else {
                FirebaseHelper.productsCollection.document(item.productId).get()
            }
        }

        com.google.android.gms.tasks.Tasks.whenAllSuccess<com.google.firebase.firestore.DocumentSnapshot>(itemChecks)
            .addOnSuccessListener { snapshots ->
                // A removal started before validation can complete while these reads are running.
                if (cartItems.map { it.id to it.quantity } != checkedItems.map { it.id to it.quantity }) {
                    Toast.makeText(this, "Your cart changed. Review it and try again.", Toast.LENGTH_SHORT).show()
                    onResult(false)
                    return@addOnSuccessListener
                }
                val unavailableItems = mutableListOf<String>()
                for (i in snapshots.indices) {
                    val item = cartItems[i]
                    val unavailable = if (item.offeringType == OFFERING_REFILL) {
                        val station = snapshots[i].toObject(User::class.java)
                        val isUnavailable = station == null ||
                            !station.refillServiceEnabled ||
                            station.refillFee <= 0.0
                        if (!isUnavailable) {
                            item.productPrice = station.refillFee
                            item.totalPrice = station.refillFee * item.quantity
                        }
                        isUnavailable
                    } else {
                        val product = snapshots[i].toObject(Product::class.java)
                        if (product != null) {
                            item.productPrice = product.price
                            item.totalPrice = product.price * item.quantity
                        }
                        product == null
                    }
                    if (unavailable) {
                        unavailableItems.add("${cartItems[i].productName}")
                    }
                }

                if (unavailableItems.isNotEmpty()) {
                    AlertDialog.Builder(this)
                        .setTitle("Order Error")
                        .setMessage("Some items or refill services are no longer offered. Review your cart.")
                        .setPositiveButton("OK") { _, _ -> loadCartItems() }
                        .show()
                    onResult(false)
                } else {
                    cartAdapter.notifyDataSetChanged()
                    updateTotal()
                    onResult(true)
                }
            }.addOnFailureListener { onResult(false) }
    }

    private fun initiateGCashPayment() {
        val stationUsername = cartItems.firstOrNull()?.stationOwnerUsername ?: return
        if (cartItems.any { it.stationOwnerUsername != stationUsername }) {
            Toast.makeText(this, "Check out one water station at a time", Toast.LENGTH_LONG).show()
            return
        }
        btnCheckout.isEnabled = false
        FirebaseHelper.getUser(stationUsername).addOnSuccessListener { snapshot ->
                btnCheckout.isEnabled = true
                val station = snapshot.toObject(User::class.java)
                if (station?.isApprovedStationOwner() != true || !station.isStationOpen()) {
                    Toast.makeText(this, "This station is unavailable", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }
                GcashQrDialog.show(this, station, calculateTotal()) {
                    finalizeOrders("GCash", isPaid = false)
                }
        }.addOnFailureListener {
                btnCheckout.isEnabled = true
                Toast.makeText(this, "Could not load GCash QR code", Toast.LENGTH_LONG).show()
            }
    }

    private fun finalizeOrders(selectedPayment: String, isPaid: Boolean) {
        if (isFinalizing) return
        val user = currentUser ?: return
        
        if (cartItems.isEmpty()) {
            return
        }

        isFinalizing = true
        btnCheckout.isEnabled = false
        
        Toast.makeText(this, "Completing order...", Toast.LENGTH_LONG).show()

        val isRush = switchRushOrder.isChecked && isRushEnabledAtStation
        val checkedOutItems = cartItems.map { it.copy() }
        
        FirebaseHelper.placeOrders(
            user,
            checkedOutItems,
            selectedPayment,
            isPaid,
            isRush,
            currentStationRushFee,
            currentStationDeliveryFee
        ).addOnSuccessListener { error ->
            isFinalizing = false
            btnCheckout.isEnabled = true
            
            if (error == null) {
                Toast.makeText(this, "Order placed successfully!", Toast.LENGTH_LONG).show()
                completedCheckoutItems = checkedOutItems
                cartItems.clear()
                updateUI()
                
                showRecurringRecommendation()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Order Failed")
                    .setMessage(error)
                    .setPositiveButton("OK") { _, _ -> loadCartItems() }
                    .show()
            }
        }.addOnFailureListener { e ->
            btnCheckout.isEnabled = true
            isFinalizing = false
            Toast.makeText(this, "Checkout Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
