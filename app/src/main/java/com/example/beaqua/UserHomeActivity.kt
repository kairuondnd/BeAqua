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
    private lateinit var navMessages: LinearLayout
    private lateinit var navHistory: LinearLayout
    private lateinit var navProfile: LinearLayout
    private lateinit var btnLogout: Button
    
    private lateinit var tvNavUserName: TextView
    private lateinit var tvNavUserRole: TextView

    private var currentUser: User? = null
    private var cartListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var latestCartSnapshot: com.google.firebase.firestore.QuerySnapshot? = null
    private var orderStatusListener: com.google.firebase.firestore.ListenerRegistration? = null
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
        findViewById<ImageButton>(R.id.btnHeaderCart).setOnClickListener { openCart() }
        
        val btnMenu = findViewById<ImageButton>(R.id.btnMenu)
        btnMenu.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.END)
        }

        navHome = findViewById(R.id.navUserHome)
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
                currentUser?.let { DeliveryReminderWorker.start(this, it.username) }
                if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                    listenForCartCount()
                }
                currentUser?.let {
                    tvNavUserName.text = it.name
                    tvNavUserRole.text = it.accountType
                }
                showStations()
                setupSideNav()
                listenForOrderStatusChanges() 
                checkNotificationPermission()
                startAccountNotificationListenerIfAllowed()
                SubscriptionOrderWorker.bindAccount(this, username, stationOwner = false)
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

    override fun onStart() {
        super.onStart()
        listenForCartCount()
    }

    override fun onStop() {
        cartListener?.remove()
        cartListener = null
        latestCartSnapshot = null
        super.onStop()
    }

    private fun listenForCartCount() {
        val user = currentUser ?: return
        cartListener?.remove()
        cartListener = FirebaseHelper.cartCollection
            .whereEqualTo("customerUsername", user.username)
            .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null || snapshot == null) {
                    latestCartSnapshot = null
                    return@addSnapshotListener
                }
                latestCartSnapshot = if (snapshot.metadata.isFromCache) null else snapshot
                val count = snapshot.documents.sumOf {
                    (it.getLong("quantity") ?: 0L).coerceAtLeast(0L)
                }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                val badge = findViewById<TextView>(R.id.tvCartBadge)
                badge.visibility = if (count > 0) View.VISIBLE else View.GONE
                badge.text = if (count > 99) getString(R.string.cart_badge_overflow) else count.toString()
                findViewById<ImageButton>(R.id.btnHeaderCart).contentDescription =
                    resources.getQuantityString(R.plurals.cart_item_count, count, count)
            }
    }

    private fun cartSnapshotForAdd(username: String): com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot> {
        val snapshot = latestCartSnapshot
        return if (snapshot != null && username == currentUser?.username) {
            com.google.android.gms.tasks.Tasks.forResult(snapshot)
        } else FirebaseHelper.getCartItems(username)
    }

    private fun openCart() {
        val user = currentUser ?: return
        startActivity(Intent(this, CartActivity::class.java).putExtra("USERNAME", user.username))
    }

    override fun onDestroy() {
        orderStatusListener?.remove()
        accountNotificationListener?.remove()
        super.onDestroy()
    }

    private fun listenForOrderStatusChanges() {
        currentUser?.let { user ->
            orderStatusListener?.remove()
            orderStatusListener = FirebaseHelper.ordersCollection
                .whereEqualTo("customerName", user.username)
                .addSnapshotListener { snapshots, e ->
                    if (e != null || snapshots == null) return@addSnapshotListener
                    
                    for (dc in snapshots.documentChanges) {
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
                                    "Accepted" -> buildString {
                                        append("Your order for ${order.productName} has been accepted and is being prepared.")
                                        if (order.estimatedDeliveryDate > 0L) {
                                            append(" Estimated delivery: ")
                                            append(
                                                DeliveryEta.label(
                                                    order.estimatedDeliveryDate,
                                                    order.estimatedDeliveryTimeZoneId
                                                )
                                            )
                                            append('.')
                                        }
                                    }
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
            DeliveryReminderWorker.clear(this)
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
        val recurringCheck = inventoryView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.checkRecurringDelivery)
        val recurringForm = inventoryView.findViewById<LinearLayout>(R.id.recurringDeliveryForm)
        val recurringHint = inventoryView.findViewById<TextView>(R.id.tvRecurringDeliveryHint)
        recurringCheck.setOnCheckedChangeListener { _, checked ->
            recurringForm.visibility = if (checked) View.VISIBLE else View.GONE
            recurringHint.visibility = if (checked) View.GONE else View.VISIBLE
            recurringCheck.setTextColor(ContextCompat.getColor(this,
                if (checked) R.color.primary else R.color.text_secondary))
            if (checked && recurringForm.childCount == 0) {
                val customer = currentUser ?: return@setOnCheckedChangeListener
                recurringForm.addView(TextView(this).apply {
                    setText(R.string.recurring_delivery_loading)
                    setPadding(24, 16, 24, 24)
                })
                RecurringDeliveryEditor.show(this, customer, station,
                    inlineContainer = recurringForm,
                    onSavingChanged = { recurringCheck.isEnabled = !it },
                    onCancelled = {
                        recurringForm.removeAllViews()
                        recurringCheck.isChecked = false
                    },
                    onSaved = {
                        recurringForm.removeAllViews()
                        recurringCheck.isChecked = false
                        Toast.makeText(this, "Recurring delivery saved", Toast.LENGTH_LONG).show()
                    })
            }
        }
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
                        val cartItem = CartItem(
                            productId = product.id,
                            productName = product.name,
                            productPrice = product.price,
                            quantity = quantity,
                            containerType = product.name,
                            stationOwnerUsername = station.username,
                            stationName = station.name,
                            customerUsername = customer.username,
                            imageUri = product.imageUri
                        )
                        
                        // Check if cart already has items
                        Toast.makeText(this, "Adding to cart…", Toast.LENGTH_SHORT).show()
                        cartSnapshotForAdd(customer.username).addOnSuccessListener { cartSnapshot ->
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
                                    it.containerType == product.name
                                }

                                if (duplicateItem != null) {
                                    FirebaseHelper.incrementCartItem(duplicateItem, quantity).addOnSuccessListener {
                                        Toast.makeText(this, "Quantity updated for ${product.name}", Toast.LENGTH_SHORT).show()
                                    }.addOnFailureListener {
                                        Toast.makeText(this, "Could not update cart. Try again.", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    FirebaseHelper.addToCart(cartItem).addOnSuccessListener {
                                        Toast.makeText(
                                            this,
                                            "${product.name} added to cart",
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
                    withDeliveryAccess(station) {
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
                    withDeliveryAccess(station) {
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
            .setTitle(if (subscribe) "Recurring refill exchange" else "Confirm refill exchange")
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
        val refillId = "REFILL_${station.username}"
        val cartItem = CartItem(
            productId = refillId,
            productName = "Water Refill",
            productPrice = station.refillFee,
            quantity = quantity,
            containerType = "Customer-owned container",
            stationOwnerUsername = station.username,
            stationName = station.name,
            customerUsername = customer.username,
            offeringType = OFFERING_REFILL,
            refillServiceId = station.username,
            refillInstructions = instructions,
            emptyContainerCount = quantity
        )

        Toast.makeText(this, "Adding refill to cart…", Toast.LENGTH_SHORT).show()
        cartSnapshotForAdd(customer.username)
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
                        it.refillInstructions == instructions
                }
                if (duplicate != null) {
                    FirebaseHelper.incrementCartItem(duplicate, quantity).addOnSuccessListener {
                        Toast.makeText(this, "Refill quantity updated", Toast.LENGTH_SHORT).show()
                    }.addOnFailureListener {
                        Toast.makeText(this, "Could not update refill. Try again.", Toast.LENGTH_SHORT).show()
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

    private fun withDeliveryAccess(station: User, onActive: () -> Unit) { onActive() }

    private fun showSubscriptionDialog(
        station: User, offeringId: String, offeringName: String, offeringPrice: Double,
        initialQuantity: Int, containerType: String, offeringType: String = OFFERING_PURCHASE,
        refillInstructions: String = ""
    ) {
        val customer = currentUser ?: return
        RecurringDeliveryEditor.show(this, customer, station, preferredProductId = offeringId,
            initialQuantity = initialQuantity, onSaved = { openSubscriptions() })
    }
}
