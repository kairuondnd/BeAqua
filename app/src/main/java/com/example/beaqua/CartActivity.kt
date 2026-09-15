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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                loadCartItems()
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
                        val currentEstimate = stationOwner.etaSettings.deliveryEstimateAt()
                        cartItems.forEach { it.deliveryTimeSlot = currentEstimate }
                        currentStationDeliveryFee = stationOwner.deliveryFee
                        tvDeliveryFeeCart.text = String.format("₱%.2f", currentStationDeliveryFee)
                        
                        if (stationOwner.rushOrderEnabled) {
                            isRushEnabledAtStation = true
                            currentStationRushFee = stationOwner.rushOrderFee
                            layoutRushOrder.visibility = View.VISIBLE
                            tvRushFeeNote.text = "Additional fee: ₱${String.format("%.2f", currentStationRushFee)}"
                        } else {
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
                FirebaseHelper.removeCartItem(item.id).addOnSuccessListener {
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
        if (cartItems.isEmpty()) return
        
        checkItemsStillOffered { isAvailable ->
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
        val itemChecks = cartItems.map { item ->
            if (item.offeringType == OFFERING_REFILL) {
                FirebaseHelper.usersCollection.document(item.stationOwnerUsername).get()
            } else {
                FirebaseHelper.productsCollection.document(item.productId).get()
            }
        }

        com.google.android.gms.tasks.Tasks.whenAllSuccess<com.google.firebase.firestore.DocumentSnapshot>(itemChecks)
            .addOnSuccessListener { snapshots ->
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
                        snapshots[i].toObject(Product::class.java) == null
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
        
        FirebaseHelper.placeOrders(
            user,
            cartItems,
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
                cartItems.clear()
                updateUI()
                
                val intent = Intent(this, UserHomeActivity::class.java)
                intent.putExtra("USERNAME", user.username)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
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
