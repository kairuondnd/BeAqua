package com.example.beaqua

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.DocumentChange
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class UserHomeActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var contentFrame: FrameLayout
    
    // Side Nav Items
    private lateinit var navHome: LinearLayout
    private lateinit var navCart: LinearLayout
    private lateinit var navSubscriptions: LinearLayout
    private lateinit var navMessages: LinearLayout
    private lateinit var navHistory: LinearLayout
    private lateinit var navProfile: LinearLayout
    private lateinit var btnLogout: Button
    
    private lateinit var tvNavUserName: TextView
    private lateinit var tvNavUserRole: TextView

    private var currentUser: User? = null
    private val inventoryProducts = mutableListOf<Product>()
    private val NOTIFICATION_PERMISSION_CODE = 1002
    private var accountNotificationListener: com.google.firebase.firestore.ListenerRegistration? = null
    private val stationLocatorLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val stationUsername = result.data
                ?.getStringExtra(StationLocatorActivity.RESULT_STATION_USERNAME)
                .orEmpty()
            if (stationUsername.isBlank()) return@registerForActivityResult

            FirebaseHelper.getUser(stationUsername)
                .addOnSuccessListener { snapshot ->
                    val station = snapshot.toObject(User::class.java)
                    if (station?.isApprovedStationOwner() == true) {
                        showStationInventory(station)
                    } else {
                        Toast.makeText(this, "Water station is unavailable", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Could not open water station", Toast.LENGTH_SHORT).show()
                }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_home)

        drawerLayout = findViewById(R.id.drawerLayout)
        contentFrame = findViewById(R.id.userContentFrame)
        
        val btnMenu = findViewById<ImageButton>(R.id.btnMenu)
        btnMenu.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.END)
        }

        navHome = findViewById(R.id.navUserHome)
        navCart = findViewById(R.id.navUserCart)
        navSubscriptions = findViewById(R.id.navUserSubscriptions)
        navMessages = findViewById(R.id.navUserMessages)
        navHistory = findViewById(R.id.navUserHistory)
        navProfile = findViewById(R.id.navUserProfile)
        btnLogout = findViewById(R.id.btnLogout)
        
        tvNavUserName = findViewById(R.id.tvNavUserName)
        tvNavUserRole = findViewById(R.id.tvNavUserRole)

        val username = intent.getStringExtra("USERNAME") ?: ""
        if (username.isNotEmpty()) {
            FirebaseHelper.getUser(username).addOnSuccessListener { document ->
                currentUser = document.toObject(User::class.java)
                currentUser?.let {
                    tvNavUserName.text = it.name
                    tvNavUserRole.text = it.accountType
                }
                showStations()
                setupSideNav()
                listenForOrderStatusChanges() 
                checkNotificationPermission()
                startAccountNotificationListenerIfAllowed()
                FirebaseHelper.processDueSubscriptions()
                FirebaseHelper.cancelExpiredPendingOrders()
            }.addOnFailureListener {
                Toast.makeText(this, "Failed to load user data", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            finish()
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_CODE)
            }
        }
    }

    private fun startAccountNotificationListenerIfAllowed() {
        val user = currentUser ?: return
        if (!androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        accountNotificationListener?.remove()
        accountNotificationListener = NotificationHelper.listenForAccountNotifications(
            this,
            user.username
        ) {
            Intent(this, UserHistoryActivity::class.java).apply {
                putExtra("USERNAME", user.username)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (
            requestCode == NOTIFICATION_PERMISSION_CODE &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            startAccountNotificationListenerIfAllowed()
        }
    }

    override fun onDestroy() {
        accountNotificationListener?.remove()
        super.onDestroy()
    }

    private fun listenForOrderStatusChanges() {
        currentUser?.let { user ->
            FirebaseHelper.ordersCollection
                .whereEqualTo("customerName", user.username)
                .addSnapshotListener { snapshots, e ->
                    if (e != null) return@addSnapshotListener
                    
                    for (dc in snapshots!!.documentChanges) {
                        if (dc.type == DocumentChange.Type.MODIFIED) {
                            val order = dc.document.toObject(Order::class.java)
                            if (order != null) {
                                val status = order.status
                                if (status == "Cancelled" && order.autoCancelledAt > 0L) continue
                                
                                val title = when (status) {
                                    "Accepted" -> "Order Accepted!"
                                    "Delivered" -> "Order Delivered!"
                                    "Rejected" -> "Order Rejected"
                                    "Cancelled" -> "Order Cancelled"
                                    else -> "Order Update"
                                }
                                
                                val message = when (status) {
                                    "Accepted" -> "Your order for ${order.productName} has been accepted and is being prepared."
                                    "Delivered" -> "Your order for ${order.productName} has been delivered. Enjoy!"
                                    "Rejected" -> "Sorry, your order for ${order.productName} was rejected by the station."
                                    "Cancelled" -> order.cancellationReason.ifBlank {
                                        "Your order for ${order.productName} was cancelled."
                                    }
                                    else -> "Your order status is now: $status"
                                }

                                NotificationHelper.showNotification(
                                    this,
                                    title,
                                    message,
                                    Intent(this, UserHistoryActivity::class.java).apply {
                                        putExtra("USERNAME", user.username)
                                    }
                                )
                            }
                        }
                    }
                }
        }
    }

    private fun setupSideNav() {
        navHome.setOnClickListener {
            showStations()
            drawerLayout.closeDrawer(GravityCompat.END)
        }

        navCart.setOnClickListener {
            currentUser?.let { user ->
                val intent = Intent(this, CartActivity::class.java)
                intent.putExtra("USERNAME", user.username)
                startActivity(intent)
            }
            drawerLayout.closeDrawer(GravityCompat.END)
        }

        navSubscriptions.setOnClickListener {
            openSubscriptions()
            drawerLayout.closeDrawer(GravityCompat.END)
        }

        navMessages.setOnClickListener {
            currentUser?.let { user ->
                val intent = Intent(this, MessagesActivity::class.java)
                intent.putExtra("USERNAME", user.username)
                startActivity(intent)
            }
            drawerLayout.closeDrawer(GravityCompat.END)
        }

        navHistory.setOnClickListener {
            currentUser?.let { user ->
                val intent = Intent(this, UserHistoryActivity::class.java)
                intent.putExtra("USERNAME", user.username)
                startActivity(intent)
            }
            drawerLayout.closeDrawer(GravityCompat.END)
        }

        navProfile.setOnClickListener {
            currentUser?.let { user ->
                val intent = Intent(this, UserProfileActivity::class.java)
                intent.putExtra("USERNAME", user.username)
                startActivity(intent)
            }
            drawerLayout.closeDrawer(GravityCompat.END)
        }

        btnLogout.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showStations() {
        contentFrame.removeAllViews()
        val homeView = LayoutInflater.from(this).inflate(R.layout.user_home_content, contentFrame, false)
        contentFrame.addView(homeView)
        
        homeView.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in))

        val tvWelcome = homeView.findViewById<TextView>(R.id.tvWelcomeUser)
        tvWelcome.text = "Hello, ${currentUser?.name ?: "User"}!"

        val cardNearMe = homeView.findViewById<MaterialCardView>(R.id.cardNearMe)
        val cardPending = homeView.findViewById<MaterialCardView>(R.id.cardPendingOrdersUser)
        val cardSubscriptions =
            homeView.findViewById<MaterialCardView>(R.id.cardWeeklySubscriptions)
        val cardHistory = homeView.findViewById<MaterialCardView>(R.id.cardUserHistory)
        val tvRefresh = homeView.findViewById<TextView>(R.id.tvViewAllStations)
        val rvStations = homeView.findViewById<RecyclerView>(R.id.rvStations)

        cardNearMe.setOnClickListener {
            currentUser?.let { user ->
                val intent = Intent(this, StationLocatorActivity::class.java)
                intent.putExtra("USERNAME", user.username)
                stationLocatorLauncher.launch(intent)
            }
        }

        cardPending.setOnClickListener {
            currentUser?.let { user ->
                val intent = Intent(this, PendingOrdersActivity::class.java)
                intent.putExtra("USERNAME", user.username)
                startActivity(intent)
            }
        }

        cardSubscriptions.setOnClickListener { openSubscriptions() }

        cardHistory.setOnClickListener {
            currentUser?.let { user ->
                val intent = Intent(this, UserHistoryActivity::class.java)
                intent.putExtra("USERNAME", user.username)
                startActivity(intent)
            }
        }

        tvRefresh.setOnClickListener { showStations() }

        rvStations.layoutManager = LinearLayoutManager(this)
        rvStations.layoutAnimation = AnimationUtils.loadLayoutAnimation(this, R.anim.layout_animation_fall_down)

        FirebaseHelper.getApprovedStationOwners().addOnSuccessListener { result ->
            val stations = result.toObjects(User::class.java)
            val adapter = StationAdapter(stations) { station ->
                showStationInventory(station)
            }
            rvStations.adapter = adapter
            rvStations.scheduleLayoutAnimation()
        }
    }

    private fun calculateEta(settings: EtaSettings): String {
        return settings.deliveryEstimateAt()
    }

    private fun showStationInventory(station: User) {
        contentFrame.removeAllViews()
        val inventoryView = LayoutInflater.from(this).inflate(R.layout.station_inventory_content, contentFrame, false)
        contentFrame.addView(inventoryView)

        val btnBack = inventoryView.findViewById<MaterialButton>(R.id.btnBackToStations)
        val btnChat = inventoryView.findViewById<MaterialButton>(R.id.btnChatWithOwner)
        val btnBuy = inventoryView.findViewById<MaterialButton>(R.id.btnBuyContainers)
        val btnRefill = inventoryView.findViewById<MaterialButton>(R.id.btnRefillContainer)
        val tvStationName = inventoryView.findViewById<TextView>(R.id.tvStationName)
        val tvSubtitle = inventoryView.findViewById<TextView>(R.id.tvInventorySubtitle)
        val tvOpenStatus = inventoryView.findViewById<TextView>(R.id.tvStationOpenStatus)
        val tvOperatingHours = inventoryView.findViewById<TextView>(R.id.tvStationOperatingHours)
        val rvProducts = inventoryView.findViewById<RecyclerView>(R.id.rvStationProducts)

        tvStationName.text = station.name
        val stationIsOpen = station.isStationOpen()
        tvOpenStatus.text = station.stationStatusLabel()
        tvOperatingHours.text = station.operatingHoursLabel()
        tvOpenStatus.setTextColor(
            ContextCompat.getColor(this, if (stationIsOpen) R.color.success else R.color.error)
        )
        tvOpenStatus.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, if (stationIsOpen) R.color.mint_soft else R.color.peach_soft)
        )
        if (!stationIsOpen) {
            tvSubtitle.text = "This station is not accepting orders right now"
        }
        btnBuy.isEnabled = false
        btnBack.setOnClickListener { showStations() }

        btnChat.setOnClickListener {
            currentUser?.let { user ->
                val intent = Intent(this, SingleChatActivity::class.java)
                intent.putExtra("CURRENT_USERNAME", user.username)
                intent.putExtra("CHAT_WITH_USERNAME", station.username)
                startActivity(intent)
            }
        }

        rvProducts.layoutManager = LinearLayoutManager(this)
        rvProducts.layoutAnimation = AnimationUtils.loadLayoutAnimation(this, R.anim.layout_animation_fall_down)

        FirebaseHelper.getProductsByStation(station.username).addOnSuccessListener { result ->
            inventoryProducts.clear()
            inventoryProducts.addAll(result.toObjects(Product::class.java))
            
            val inventoryAdapter = ProductAdapter(
                inventoryProducts,
                isUserView = true,
                onOrder = { product, quantity ->
                    if (!ensureStationOpen(station)) return@ProductAdapter
                    if (quantity <= 0) {
                        Toast.makeText(this, "Enter a valid quantity", Toast.LENGTH_SHORT).show()
                        return@ProductAdapter
                    }

                    currentUser?.let { customer ->
                        val eta = calculateEta(station.etaSettings)
                        val cartItem = CartItem(
                            productId = product.id,
                            productName = product.name,
                            productPrice = product.price,
                            quantity = quantity,
                            containerType = product.name,
                            deliveryTimeSlot = eta,
                            stationOwnerUsername = station.username,
                            stationName = station.name,
                            customerUsername = customer.username,
                            imageUri = product.imageUri
                        )
                        
                        // Check if cart already has items
                        FirebaseHelper.getCartItems(customer.username).addOnSuccessListener { cartSnapshot ->
                            val existingItems = cartSnapshot.toObjects(CartItem::class.java)
                            
                            if (existingItems.isNotEmpty() && existingItems[0].stationOwnerUsername != station.username) {
                                // Block adding if from a different station and offer to clear
                                val currentStationInCart = existingItems[0].stationName
                                AlertDialog.Builder(this)
                                    .setTitle("Different Water Station")
                                    .setMessage("Your cart contains items from '$currentStationInCart'. You can only order from one station at a time. Do you want to clear your cart and start a new order with '${station.name}'?")
                                    .setPositiveButton("Clear & Add") { _, _ ->
                                        FirebaseHelper.clearCart(customer.username).addOnSuccessListener {
                                            FirebaseHelper.addToCart(cartItem).addOnSuccessListener {
                                                Toast.makeText(this, "${product.name} added to new cart", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                    .setNeutralButton("View Cart") { _, _ ->
                                        val intent = Intent(this, CartActivity::class.java)
                                        intent.putExtra("USERNAME", customer.username)
                                        startActivity(intent)
                                    }
                                    .setNegativeButton("Cancel", null)
                                    .show()
                            } else {
                                // Same station or empty cart: Check if product already exists to update quantity
                                val duplicateItem = existingItems.find { 
                                    it.offeringType != OFFERING_REFILL &&
                                    it.productId == product.id &&
                                    it.containerType == product.name &&
                                    it.deliveryTimeSlot == eta
                                }

                                if (duplicateItem != null) {
                                    duplicateItem.quantity += quantity
                                    FirebaseHelper.updateCartItem(duplicateItem).addOnSuccessListener {
                                        Toast.makeText(this, "Quantity updated for ${product.name}", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    FirebaseHelper.addToCart(cartItem).addOnSuccessListener {
                                        Toast.makeText(
                                            this,
                                            "${product.name} added • Estimated delivery: $eta",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }.addOnFailureListener {
                                        Toast.makeText(this, "Failed to add to cart", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }.addOnFailureListener {
                            Toast.makeText(this, "Failed to check cart status", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onSubscribe = { product, quantity ->
                    if (quantity <= 0) {
                        Toast.makeText(this, "Enter a valid quantity", Toast.LENGTH_SHORT).show()
                        return@ProductAdapter
                    }
                    requirePremium(station) {
                        showSubscriptionDialog(
                            station = station,
                            offeringId = product.id,
                            offeringName = product.name,
                            offeringPrice = product.price,
                            initialQuantity = quantity,
                            containerType = product.name
                        )
                    }
                }
            )
            rvProducts.adapter = inventoryAdapter
            rvProducts.scheduleLayoutAnimation()
            btnBuy.setOnClickListener {
                rvProducts.adapter = inventoryAdapter
                tvSubtitle.text = "Select water products to order"
                btnBuy.isEnabled = false
                btnRefill.isEnabled = true
                rvProducts.scheduleLayoutAnimation()
            }
        }

        if (station.refillServiceEnabled && station.refillFee > 0.0) {
            val refillOption = Product(
                id = "REFILL_${station.username}",
                name = "Water Refill",
                price = station.refillFee,
                ownerUsername = station.username
            )
            val refillOptions = mutableListOf(refillOption)
            val customerRefillAdapter = ProductAdapter(
                refillOptions,
                isUserView = true,
                onOrder = { _, quantity ->
                    if (!ensureStationOpen(station)) return@ProductAdapter
                    if (quantity <= 0) {
                        Toast.makeText(this, "Enter a valid number of empty containers", Toast.LENGTH_SHORT).show()
                        return@ProductAdapter
                    }
                    showRefillInstructionsDialog(station, quantity, subscribe = false)
                },
                onSubscribe = { _, quantity ->
                    if (quantity <= 0) {
                        Toast.makeText(this, "Enter a valid number of empty containers", Toast.LENGTH_SHORT).show()
                        return@ProductAdapter
                    }
                    requirePremium(station) {
                        showRefillInstructionsDialog(station, quantity, subscribe = true)
                    }
                },
                isRefillView = true
            )

            btnRefill.setOnClickListener {
                rvProducts.adapter = customerRefillAdapter
                tvSubtitle.text = "Doorstep exchange for your empty containers"
                btnBuy.isEnabled = true
                btnRefill.isEnabled = false
                rvProducts.scheduleLayoutAnimation()
            }
        } else {
            btnRefill.visibility = View.GONE
        }
    }

    private fun showRefillInstructionsDialog(
        station: User,
        quantity: Int,
        subscribe: Boolean
    ) {
        val instructions = EditText(this).apply {
            hint = "Optional notes (container size, gate, pickup details)"
            minLines = 2
            setPadding(32, 20, 32, 20)
        }
        AlertDialog.Builder(this)
            .setTitle(if (subscribe) "Weekly refill exchange" else "Confirm refill exchange")
            .setMessage(
                "Prepare $quantity empty container(s). " +
                    "The rider will collect them when delivering the refilled containers."
            )
            .setView(instructions)
            .setPositiveButton(if (subscribe) "Choose schedule" else "Add to cart") { _, _ ->
                val note = instructions.text.toString().trim()
                if (subscribe) {
                    showSubscriptionDialog(
                        station = station,
                        offeringId = "REFILL_${station.username}",
                        offeringName = "Water Refill",
                        offeringPrice = station.refillFee,
                        initialQuantity = quantity,
                        containerType = "Customer-owned container",
                        offeringType = OFFERING_REFILL,
                        refillInstructions = note
                    )
                } else {
                    addRefillToCart(station, quantity, note)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addRefillToCart(
        station: User,
        quantity: Int,
        instructions: String
    ) {
        if (!ensureStationOpen(station)) return
        val customer = currentUser ?: return
        val eta = calculateEta(station.etaSettings)
        val refillId = "REFILL_${station.username}"
        val cartItem = CartItem(
            productId = refillId,
            productName = "Water Refill",
            productPrice = station.refillFee,
            quantity = quantity,
            containerType = "Customer-owned container",
            deliveryTimeSlot = eta,
            stationOwnerUsername = station.username,
            stationName = station.name,
            customerUsername = customer.username,
            offeringType = OFFERING_REFILL,
            refillServiceId = station.username,
            refillInstructions = instructions,
            emptyContainerCount = quantity
        )

        FirebaseHelper.getCartItems(customer.username)
            .addOnSuccessListener { cartSnapshot ->
                val existingItems = cartSnapshot.toObjects(CartItem::class.java)
                if (
                    existingItems.isNotEmpty() &&
                    existingItems.first().stationOwnerUsername != station.username
                ) {
                    val currentStation = existingItems.first().stationName
                    AlertDialog.Builder(this)
                        .setTitle("Different Water Station")
                        .setMessage(
                            "Your cart contains items from '$currentStation'. Clear it and start " +
                                "a new order with '${station.name}'?"
                        )
                        .setPositiveButton("Clear & Add") { _, _ ->
                            FirebaseHelper.clearCart(customer.username).addOnSuccessListener {
                                FirebaseHelper.addToCart(cartItem).addOnSuccessListener {
                                    Toast.makeText(this, "Refill added to new cart", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                    return@addOnSuccessListener
                }

                val duplicate = existingItems.find {
                    it.offeringType == OFFERING_REFILL &&
                        it.stationOwnerUsername == station.username &&
                        it.refillInstructions == instructions &&
                        it.deliveryTimeSlot == eta
                }
                if (duplicate != null) {
                    duplicate.quantity += quantity
                    duplicate.emptyContainerCount = duplicate.quantity
                    duplicate.totalPrice = duplicate.productPrice * duplicate.quantity
                    FirebaseHelper.updateCartItem(duplicate).addOnSuccessListener {
                        Toast.makeText(this, "Refill quantity updated", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    FirebaseHelper.addToCart(cartItem)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Refill added to cart", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { error ->
                            Toast.makeText(this, "Could not add refill: ${error.message}", Toast.LENGTH_LONG).show()
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Could not check the cart", Toast.LENGTH_SHORT).show()
            }
    }

    private fun ensureStationOpen(station: User): Boolean {
        if (station.isStationOpen()) return true
        Toast.makeText(
            this,
            "${station.name.ifBlank { "This station" }} is currently closed. " +
                station.operatingHoursLabel(),
            Toast.LENGTH_LONG
        ).show()
        return false
    }

    private fun openSubscriptions() {
        val user = currentUser ?: return
        val intent = Intent(this, SubscriptionActivity::class.java)
        intent.putExtra("USERNAME", user.username)
        startActivity(intent)
    }

    private fun requirePremium(station: User, onActive: () -> Unit) {
        val user = currentUser ?: return
        FirebaseHelper.getStationPremiumMembership(user.username, station.username)
            .addOnSuccessListener { snapshot ->
                val membership = snapshot.toObject(StationPremiumMembership::class.java)
                if (membership?.isActiveFor(user.username, station.username) == true) {
                    onActive()
                } else {
                    val intent = Intent(this, PremiumActivity::class.java)
                    intent.putExtra(PremiumActivity.EXTRA_USERNAME, user.username)
                    intent.putExtra(PremiumActivity.EXTRA_STATION_USERNAME, station.username)
                    startActivity(intent)
                }
            }
            .addOnFailureListener {
                Toast.makeText(
                    this,
                    "Connect to the internet to verify Premium for this station",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun showSubscriptionDialog(
        station: User,
        offeringId: String,
        offeringName: String,
        offeringPrice: Double,
        initialQuantity: Int,
        containerType: String,
        offeringType: String = OFFERING_PURCHASE,
        refillInstructions: String = ""
    ) {
        val customer = currentUser ?: return
        if (customer.address.isBlank()) {
            AlertDialog.Builder(this)
                .setTitle("Delivery address required")
                .setMessage("Add your delivery address in Profile before starting a weekly delivery.")
                .setPositiveButton("Open profile") { _, _ ->
                    val intent = Intent(this, UserProfileActivity::class.java)
                    intent.putExtra("USERNAME", customer.username)
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        val dialogView = layoutInflater.inflate(R.layout.dialog_weekly_subscription, null)
        val productName = dialogView.findViewById<TextView>(R.id.tvSubscriptionProduct)
        val quantityInput = dialogView.findViewById<EditText>(R.id.etSubscriptionQuantity)
        val daySpinner = dialogView.findViewById<Spinner>(R.id.spnSubscriptionDay)
        val timeSpinner = dialogView.findViewById<Spinner>(R.id.spnSubscriptionTime)
        val summary = dialogView.findViewById<TextView>(R.id.tvSubscriptionSummary)

        val days = listOf(
            "Sunday", "Monday", "Tuesday", "Wednesday",
            "Thursday", "Friday", "Saturday"
        )
        val slots = station.etaSettings.customerDeliveryWindows()

        productName.text = "$offeringName from ${station.name}"
        quantityInput.setText(initialQuantity.coerceAtLeast(1).toString())
        daySpinner.adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            days
        )
        timeSpinner.adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            slots
        )
        summary.text = String.format(
            Locale.getDefault(),
            "Current product price: ₱%.2f each\nStation delivery fee: ₱%.2f\nPayment: Cash on Delivery",
            offeringPrice,
            station.deliveryFee
        )

        val dialog = AlertDialog.Builder(this)
            .setTitle("Set weekly delivery")
            .setView(dialogView)
            .setPositiveButton("Start subscription", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val quantity = quantityInput.text.toString().toIntOrNull() ?: 0
                if (quantity <= 0) {
                    quantityInput.error = "Enter a valid quantity"
                    return@setOnClickListener
                }
                val day = days[daySpinner.selectedItemPosition]
                val timeSlot = slots.getOrElse(timeSpinner.selectedItemPosition) {
                    calculateEta(station.etaSettings)
                }
                val subscription = WeeklySubscription(
                    customerUsername = customer.username,
                    stationOwnerUsername = station.username,
                    stationName = station.name,
                    productId = offeringId,
                    productName = offeringName,
                    quantity = quantity,
                    containerType = containerType,
                    deliveryDay = day,
                    deliveryTimeSlot = timeSlot,
                    nextDeliveryAt = calculateNextDelivery(day),
                    active = true,
                    lastStatus = "Scheduled",
                    offeringType = offeringType,
                    refillServiceId = if (offeringType == OFFERING_REFILL) offeringId else "",
                    refillInstructions = refillInstructions,
                    emptyContainerCount = if (offeringType == OFFERING_REFILL) quantity else 0
                )

                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                FirebaseHelper.getSubscriptionsForCustomer(customer.username)
                    .addOnSuccessListener { existingResult ->
                        val duplicate = existingResult
                            .toObjects(WeeklySubscription::class.java)
                            .any {
                                it.active &&
                                    it.stationOwnerUsername == station.username &&
                                    it.offeringType == offeringType &&
                                    (offeringType == OFFERING_REFILL || it.productId == offeringId)
                            }
                        if (duplicate) {
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                            Toast.makeText(
                                this,
                                "You already have an active weekly delivery for this service",
                                Toast.LENGTH_LONG
                            ).show()
                            return@addOnSuccessListener
                        }

                        FirebaseHelper.addSubscription(subscription)
                            .addOnSuccessListener {
                        val formattedDate = SimpleDateFormat(
                            "EEEE, MMM d",
                            Locale.getDefault()
                        ).format(Date(subscription.nextDeliveryAt))
                        Toast.makeText(
                            this,
                            "Weekly delivery starts $formattedDate",
                            Toast.LENGTH_LONG
                        ).show()
                        dialog.dismiss()
                        openSubscriptions()
                            }
                            .addOnFailureListener { error ->
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                                Toast.makeText(
                                    this,
                                    "Could not start subscription: ${error.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                    }
                    .addOnFailureListener {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                        Toast.makeText(
                            this,
                            "Could not check existing subscriptions",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
        }
        dialog.show()
    }

    private fun calculateNextDelivery(dayName: String): Long {
        val days = listOf(
            "Sunday", "Monday", "Tuesday", "Wednesday",
            "Thursday", "Friday", "Saturday"
        )
        val targetDay = days.indexOf(dayName) + Calendar.SUNDAY
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 6)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        var daysAhead = (targetDay - calendar.get(Calendar.DAY_OF_WEEK) + 7) % 7
        if (daysAhead == 0 && calendar.timeInMillis <= System.currentTimeMillis()) {
            daysAhead = 7
        }
        calendar.add(Calendar.DAY_OF_YEAR, daysAhead)
        return calendar.timeInMillis
    }
}
