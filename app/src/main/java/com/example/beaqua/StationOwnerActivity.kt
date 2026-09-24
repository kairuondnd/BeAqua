package com.example.beaqua

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.DocumentChange
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class StationOwnerActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private val products = mutableListOf<Product>()
    private lateinit var adapter: ProductAdapter
    private var selectedImageUri: Uri? = null
    private var selectedDefaultImageSource: String? = null
    private var defaultImageCards: Map<String, MaterialCardView> = emptyMap()
    private val IMAGE_PICK_CODE = 1001
    private val NOTIFICATION_PERMISSION_CODE = 1002
    private var accountNotificationListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var newOrdersListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var visibleOrdersListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var recurringSchedulesListener: com.google.firebase.firestore.ListenerRegistration? = null

    private lateinit var currentUsername: String
    private lateinit var tvNavName: TextView
    private lateinit var tvNavRole: TextView
    
    private lateinit var ordersContainer: LinearLayout
    private lateinit var acceptedOrdersContainer: LinearLayout
    private var isShowingOnlyPending = false

    private var editingProduct: Product? = null
    private var etName: EditText? = null
    private var etPrice: EditText? = null
    private var imagePreview: ImageView? = null
    private var imageStatus: TextView? = null
    private var btnAdd: MaterialButton? = null
    
    private var currentUserData: User? = null
    private var gcashUploadButton: MaterialButton? = null
    private var gcashPreview: ImageView? = null
    private val gcashPicker = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val type = contentResolver.getType(uri)
            if (type?.startsWith("image/") != true) {
                Toast.makeText(this, "Choose a QR code image", Toast.LENGTH_SHORT).show()
            } else {
                gcashUploadButton?.isEnabled = false
                FirebaseHelper.uploadGcashQr(currentUsername, uri).addOnSuccessListener {
                    gcashUploadButton?.isEnabled = true
                    gcashPreview?.setImageURI(uri)
                    Toast.makeText(this, "GCash QR saved. Customers can now use it at checkout.", Toast.LENGTH_LONG).show()
                }.addOnFailureListener {
                    gcashUploadButton?.isEnabled = true
                    Toast.makeText(this, "QR upload failed: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    private var selectedGraphRange = GraphRange.WEEK

    private enum class GraphRange {
        DAY,
        WEEK,
        MONTH,
        YEAR
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_station_owner)

        currentUsername = intent.getStringExtra("USERNAME") ?: ""

        if (currentUsername.isEmpty()) {
            Toast.makeText(this, "No user logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        FirebaseHelper.getUser(currentUsername)
            .addOnSuccessListener { doc ->
                val user = doc.toObject(User::class.java)
                if (user == null || !user.accountType.equals("Station Owner", ignoreCase = true)) {
                    Toast.makeText(
                        this,
                        "This is not a station-owner account",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                    return@addOnSuccessListener
                }
                currentUserData = user
                initializeStationShell(user)
                if (user.isApprovedStationOwner()) {
                    initializeApprovedStationOwner()
                } else {
                    initializeRestrictedStationOwner(user)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Could not load station account", Toast.LENGTH_LONG).show()
                finish()
            }
    }

    private fun initializeStationShell(user: User) {
        drawerLayout = findViewById(R.id.drawerLayoutStation)
        tvNavName = findViewById(R.id.tvNavStationName)
        tvNavRole = findViewById(R.id.tvNavStationRole)

        val btnMenu = findViewById<ImageButton>(R.id.btnMenuStation)
        btnMenu.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.END)
        }

        tvNavName.text = user.name
        tvNavRole.text = if (user.isApprovedStationOwner()) {
            "Station Owner • Approved • Active"
        } else {
            "Station Owner • ${restrictedStatusLabel(user)}"
        }
    }

    private fun initializeApprovedStationOwner() {
        SubscriptionOrderWorker.bindAccount(this, currentUsername, stationOwner = true)
        recurringSchedulesListener?.remove()
        recurringSchedulesListener = FirebaseHelper.subscriptionsCollection
            .whereEqualTo("stationOwnerUsername", currentUsername)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && !snapshot.metadata.hasPendingWrites()) {
                    FirebaseHelper.processDueSubscriptions(stationOwnerUsername = currentUsername)
                        .addOnFailureListener {
                            Toast.makeText(this, "Could not check recurring deliveries: ${it.message}", Toast.LENGTH_LONG).show()
                        }
                }
            }

        setupSideNav()
        checkNotificationPermission()
        startAccountNotificationListenerIfAllowed()

        adapter = ProductAdapter(products,
            onEdit = { position ->
                val product = products[position]
                selectedImageUri = null
                selectedDefaultImageSource = DefaultContainerImages.find(product.imageUri)?.source
                editingProduct = product
                loadInventoryControls()
                etName?.setText(product.name)
                etPrice?.setText(product.price.toString())
                btnAdd?.text = "Update Product"
            },
            onDelete = { position ->
                val product = products[position]
                FirebaseHelper.productsCollection.document(product.id).delete()
                products.removeAt(position)
                adapter.notifyItemRemoved(position)
                Toast.makeText(this, "Product deleted", Toast.LENGTH_SHORT).show()
            }
        )

        val viewType = intent.getStringExtra("VIEW_TYPE")
        when (viewType) {
            "ORDERS" -> loadOrdersView(false)
            "PENDING_ORDERS" -> loadOrdersView(true)
            "INVENTORY" -> loadInventoryControls()
            "ETA_SETTINGS" -> loadStationSettings()
            else -> loadDashboard()
        }
        
        loadProducts()
        listenForNewOrders()
    }

    private fun initializeRestrictedStationOwner(user: User) {
        val restrictedItems = listOf(
            R.id.navStationHome,
            R.id.navStationOrders,
            R.id.navStationInventory,
            R.id.navStationAnalytics,
            R.id.navStationSettings,
            R.id.navStationMessages,
            R.id.navStationHistory,
            R.id.navStationFeedbacks,
            R.id.btnSideOrders,
            R.id.btnSideInventory,
            R.id.btnSideAnalytics,
            R.id.btnSideMessages,
            R.id.btnSideSettings
        )
        restrictedItems.forEach { findViewById<View>(it).visibility = View.GONE }

        val openProfile = View.OnClickListener {
            startActivity(
                Intent(this, UserProfileActivity::class.java)
                    .putExtra("USERNAME", currentUsername)
            )
            drawerLayout.closeDrawer(GravityCompat.END)
        }
        findViewById<View>(R.id.navStationProfile).setOnClickListener(openProfile)
        findViewById<View>(R.id.btnSideProfile).setOnClickListener(openProfile)
        findViewById<Button>(R.id.btnLogoutStation).setOnClickListener { logoutStationOwner() }
        loadRestrictedDashboard(user)
    }

    private fun loadRestrictedDashboard(user: User) {
        val restrictedView = layoutInflater.inflate(R.layout.station_owner_restricted, null)
        val contentFrame = findViewById<FrameLayout>(R.id.stationContentFrame)
        contentFrame.removeAllViews()
        contentFrame.addView(restrictedView)

        restrictedView.findViewById<TextView>(R.id.tvRestrictedStationName).text =
            user.name.ifBlank { currentUsername }
        restrictedView.findViewById<TextView>(R.id.tvRestrictedStatus).text =
            restrictedStatusLabel(user)
        restrictedView.findViewById<TextView>(R.id.tvRestrictedEmail).text =
            user.emailAddress.ifBlank { "No business email saved" }

        val completeDocuments = user.hasCompleteKycDocuments()
        restrictedView.findViewById<TextView>(R.id.tvRestrictedMessage).text = when {
            user.kycStatus == User.KYC_REJECTED ->
                "Your KYC application was not approved. Update and resubmit all required " +
                    "documents for another administrator review."
            !completeDocuments ->
                "Your station is waiting for administrator approval. Submit your missing " +
                    "KYC documents for review. Station operations remain locked until an " +
                    "administrator approves your account."
            else ->
                "Your KYC application is waiting for administrator review. You can sign in " +
                    "and manage your profile, but station operations remain locked."
        }

        val submitKyc = restrictedView.findViewById<MaterialButton>(R.id.btnRestrictedSubmitKyc)
        submitKyc.visibility = if (
            user.kycStatus == User.KYC_REJECTED || !completeDocuments
        ) View.VISIBLE else View.GONE
        submitKyc.text = if (user.kycStatus == User.KYC_REJECTED) {
            "Resubmit KYC documents"
        } else {
            "Submit KYC documents"
        }
        submitKyc.setOnClickListener {
            startActivity(
                Intent(this, KycSubmissionActivity::class.java)
                    .putExtra("USERNAME", currentUsername)
            )
        }

        restrictedView.findViewById<MaterialButton>(R.id.btnRestrictedProfile)
            .setOnClickListener {
                startActivity(
                    Intent(this, UserProfileActivity::class.java)
                        .putExtra("USERNAME", currentUsername)
                )
            }
        restrictedView.findViewById<MaterialButton>(R.id.btnRestrictedRefresh)
            .setOnClickListener { refreshStationApproval() }
        restrictedView.findViewById<MaterialButton>(R.id.btnRestrictedLogout)
            .setOnClickListener { logoutStationOwner() }
    }

    private fun refreshStationApproval() {
        FirebaseHelper.getUser(currentUsername)
            .addOnSuccessListener { snapshot ->
                val refreshedUser = snapshot.toObject(User::class.java)
                if (refreshedUser == null) {
                    Toast.makeText(this, "Station account was not found", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }
                if (refreshedUser.isApprovedStationOwner()) {
                    startActivity(
                        Intent(this, StationOwnerActivity::class.java).apply {
                            putExtra("USERNAME", currentUsername)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                    )
                    finish()
                } else {
                    currentUserData = refreshedUser
                    tvNavRole.text = "Station Owner • ${restrictedStatusLabel(refreshedUser)}"
                    loadRestrictedDashboard(refreshedUser)
                    Toast.makeText(
                        this,
                        "KYC status: ${restrictedStatusLabel(refreshedUser)}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Could not refresh KYC status", Toast.LENGTH_LONG).show()
            }
    }

    private fun restrictedStatusLabel(user: User): String = when {
        user.kycStatus == User.KYC_REJECTED -> "REJECTED • INACTIVE"
        !user.hasCompleteKycDocuments() -> "DOCUMENTS MISSING • INACTIVE"
        else -> "PENDING ADMIN APPROVAL • INACTIVE"
    }

    private fun logoutStationOwner() {
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_CODE)
            }
        }
    }

    private fun listenForNewOrders() {
        newOrdersListener?.remove()
        newOrdersListener = FirebaseHelper.ordersCollection
            .whereEqualTo("stationOwnerUsername", currentUsername)
            .whereEqualTo("status", "Pending")
            .addSnapshotListener { snapshots, e ->
                if (snapshots == null) return@addSnapshotListener
                
                for (dc in snapshots.documentChanges) {
                    if (dc.type == DocumentChange.Type.ADDED) {
                        val order = dc.document.toObject(Order::class.java)
                        if (order != null) {
                            NotificationHelper.showNotification(
                                this,
                                "New Order Received!",
                                "${order.customerName} ordered ${order.productName}",
                                Intent(this, StationOwnerActivity::class.java).apply {
                                    putExtra("USERNAME", currentUsername)
                                    putExtra("VIEW_TYPE", "PENDING_ORDERS")
                                }
                            )
                        }
                    }
                }
            }
    }

    private fun setupSideNav() {
        findViewById<LinearLayout>(R.id.navStationHome).setOnClickListener {
            loadDashboard()
            drawerLayout.closeDrawer(GravityCompat.END)
        }
        findViewById<LinearLayout>(R.id.navStationOrders).setOnClickListener {
            loadOrdersView(onlyPending = false)
            drawerLayout.closeDrawer(GravityCompat.END)
        }
        findViewById<LinearLayout>(R.id.navStationInventory).setOnClickListener {
            loadInventoryControls()
            drawerLayout.closeDrawer(GravityCompat.END)
        }
        findViewById<LinearLayout>(R.id.navStationAnalytics).setOnClickListener {
            val intent = Intent(this, RevenueActivity::class.java)
            intent.putExtra("USERNAME", currentUsername)
            startActivity(intent)
            drawerLayout.closeDrawer(GravityCompat.END)
        }
        findViewById<LinearLayout>(R.id.navStationSettings).setOnClickListener {
            loadStationSettings()
            drawerLayout.closeDrawer(GravityCompat.END)
        }
        findViewById<LinearLayout>(R.id.navStationMessages).setOnClickListener {
            val intent = Intent(this, MessagesActivity::class.java)
            intent.putExtra("USERNAME", currentUsername)
            startActivity(intent)
            drawerLayout.closeDrawer(GravityCompat.END)
        }
        findViewById<LinearLayout>(R.id.navStationHistory).setOnClickListener {
            val intent = Intent(this, StationOwnerHistoryActivity::class.java)
            intent.putExtra("USERNAME", currentUsername)
            startActivity(intent)
            drawerLayout.closeDrawer(GravityCompat.END)
        }
        findViewById<LinearLayout>(R.id.navStationFeedbacks).setOnClickListener {
            val intent = Intent(this, FeedbacksActivity::class.java)
            intent.putExtra("USERNAME", currentUsername)
            intent.putExtra("ROLE", "Station Owner")
            startActivity(intent)
            drawerLayout.closeDrawer(GravityCompat.END)
        }
        findViewById<View>(R.id.navStationProfile).setOnClickListener {
            val intent = Intent(this, UserProfileActivity::class.java)
            intent.putExtra("USERNAME", currentUsername)
            startActivity(intent)
            drawerLayout.closeDrawer(GravityCompat.END)
        }
        findViewById<MaterialButton>(R.id.btnSideOrders).setOnClickListener {
            loadOrdersView(onlyPending = false)
            drawerLayout.closeDrawer(GravityCompat.END)
        }
        findViewById<MaterialButton>(R.id.btnSideInventory).setOnClickListener {
            loadInventoryControls()
            drawerLayout.closeDrawer(GravityCompat.END)
        }
        findViewById<MaterialButton>(R.id.btnSideAnalytics).setOnClickListener {
            val intent = Intent(this, RevenueActivity::class.java)
            intent.putExtra("USERNAME", currentUsername)
            startActivity(intent)
            drawerLayout.closeDrawer(GravityCompat.END)
        }
        findViewById<MaterialButton>(R.id.btnSideMessages).setOnClickListener {
            val intent = Intent(this, MessagesActivity::class.java)
            intent.putExtra("USERNAME", currentUsername)
            startActivity(intent)
            drawerLayout.closeDrawer(GravityCompat.END)
        }
        findViewById<MaterialButton>(R.id.btnSideSettings).setOnClickListener {
            loadStationSettings()
            drawerLayout.closeDrawer(GravityCompat.END)
        }
        findViewById<MaterialButton>(R.id.btnSideProfile).setOnClickListener {
            val intent = Intent(this, UserProfileActivity::class.java)
            intent.putExtra("USERNAME", currentUsername)
            startActivity(intent)
            drawerLayout.closeDrawer(GravityCompat.END)
        }
        findViewById<Button>(R.id.btnLogoutStation).setOnClickListener {
            logoutStationOwner()
        }
    }

    private fun loadProducts() {
        FirebaseHelper.getProductsByStation(currentUsername, false).addOnSuccessListener { result ->
            products.clear()
            for (document in result) {
                val product = document.toObject(Product::class.java)
                product.id = document.id
                products.add(product)
            }
            adapter.notifyDataSetChanged()
        }
    }

    private fun loadDashboard() {
        val dashboardView = layoutInflater.inflate(R.layout.station_owner_home, null)
        val contentFrame = findViewById<FrameLayout>(R.id.stationContentFrame)
        contentFrame.removeAllViews()
        contentFrame.addView(dashboardView)
        
        dashboardView.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in))

        dashboardView.findViewById<TextView>(R.id.tvGreeting).text = "Hello, $currentUsername!"
        
        dashboardView.findViewById<MaterialCardView>(R.id.cardRevenue).setOnClickListener {
            val intent = Intent(this, RevenueActivity::class.java)
            intent.putExtra("USERNAME", currentUsername)
            startActivity(intent)
        }
        dashboardView.findViewById<View>(R.id.cardSalesGraph).setOnClickListener {
            val intent = Intent(this, RevenueActivity::class.java)
            intent.putExtra("USERNAME", currentUsername)
            startActivity(intent)
        }
        setupGraphRangeButtons(dashboardView, emptyList())

        updateInventoryStats(dashboardView)
        updateDashboardStats(dashboardView)
    }

    private fun startAccountNotificationListenerIfAllowed() {
        if (!androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        accountNotificationListener?.remove()
        accountNotificationListener = NotificationHelper.listenForAccountNotifications(
            this,
            currentUsername
        ) {
            Intent(this, StationOwnerHistoryActivity::class.java).apply {
                putExtra("USERNAME", currentUsername)
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
        visibleOrdersListener?.remove()
        recurringSchedulesListener?.remove()
        newOrdersListener?.remove()
        accountNotificationListener?.remove()
        super.onDestroy()
    }

    private fun updateDashboardStats(view: View) {
        val tvTotalSales = view.findViewById<TextView>(R.id.tvTotalSales)
        val tvTotalOrders = view.findViewById<TextView>(R.id.tvTotalOrders)
        val tvCompletedOrders = view.findViewById<TextView>(R.id.tvCompletedOrders)
        val tvPendingOrders = view.findViewById<TextView>(R.id.tvPendingOrders)
        val tvPreparingOrders = view.findViewById<TextView>(R.id.tvPreparingOrders)
        val tvWeeklySales = view.findViewById<TextView>(R.id.tvWeeklySales)
        val tvMonthlySales = view.findViewById<TextView>(R.id.tvMonthlySales)
        val ordersPreview = view.findViewById<LinearLayout>(R.id.dashboardOrdersPreview)
        val emptyOrders = view.findViewById<View>(R.id.layoutQueueEmpty)
        val salesGraph = view.findViewById<SalesGraphView>(R.id.salesGraphView)

        visibleOrdersListener?.remove()
        visibleOrdersListener = FirebaseHelper.ordersCollection
            .whereEqualTo("stationOwnerUsername", currentUsername).addSnapshotListener { result, _ ->
            if (result == null || !view.isAttachedToWindow) return@addSnapshotListener
            var totalSales = 0.0
            val totalOrdersCount = result.size()
            var completedCount = 0
            var pendingCount = 0
            var preparingCount = 0
            var weeklySales = 0.0
            var monthlySales = 0.0
            val activeOrders = mutableListOf<Order>()
            val deliveredOrders = mutableListOf<Order>()
            val now = System.currentTimeMillis()
            val sevenDaysAgo = now - 7L * 24L * 60L * 60L * 1000L
            val startOfMonth = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.DAY_OF_MONTH, 1)
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis

            for (doc in result) {
                val order = doc.toObject(Order::class.java) ?: continue
                order.id = doc.id
                when (order.status) {
                    "Delivered" -> {
                        completedCount++
                        totalSales += order.totalPrice
                        deliveredOrders.add(order)
                        if (order.timestamp >= sevenDaysAgo) weeklySales += order.totalPrice
                        if (order.timestamp >= startOfMonth) monthlySales += order.totalPrice
                    }
                    "Pending" -> {
                        pendingCount++
                        activeOrders.add(order)
                    }
                    "Accepted" -> {
                        preparingCount++
                        activeOrders.add(order)
                    }
                }
            }

            tvTotalSales.text = String.format(Locale.getDefault(), "₱%.2f", totalSales)
            tvTotalOrders?.text = totalOrdersCount.toString()
            tvCompletedOrders.text = completedCount.toString()
            tvPendingOrders.text = pendingCount.toString()
            tvPreparingOrders?.text = preparingCount.toString()
            tvWeeklySales?.text = String.format(Locale.getDefault(), "₱%,.2f", weeklySales)
            tvMonthlySales?.text = String.format(Locale.getDefault(), "₱%,.2f", monthlySales)
            salesGraph?.setSalesPoints(buildSalesSeries(deliveredOrders, selectedGraphRange))
            setupGraphRangeButtons(view, deliveredOrders)
            renderDashboardOrderPreview(ordersPreview, emptyOrders, activeOrders)
        }
    }

    private fun setupGraphRangeButtons(view: View, deliveredOrders: List<Order>) {
        val salesGraph = view.findViewById<SalesGraphView>(R.id.salesGraphView)
        val dropdown = view.findViewById<AutoCompleteTextView>(R.id.dropdownGraphRange)
        val labels = listOf("24 hours", "Week", "Month", "Year")
        val ranges = mapOf(
            "24 hours" to GraphRange.DAY,
            "Week" to GraphRange.WEEK,
            "Month" to GraphRange.MONTH,
            "Year" to GraphRange.YEAR
        )

        dropdown?.apply {
            setAdapter(android.widget.ArrayAdapter(this@StationOwnerActivity, android.R.layout.simple_dropdown_item_1line, labels))
            setText(
                ranges.entries.firstOrNull { it.value == selectedGraphRange }?.key ?: "Week",
                false
            )
            setOnItemClickListener { _, _, position, _ ->
                val selectedLabel = labels[position]
                selectedGraphRange = ranges[selectedLabel] ?: GraphRange.WEEK
                salesGraph?.setSalesPoints(buildSalesSeries(deliveredOrders, selectedGraphRange))
            }
        }
    }

    private fun buildSalesSeries(deliveredOrders: List<Order>, range: GraphRange): List<SalesPoint> {
        val now = System.currentTimeMillis()
        val startToday = startOfToday()
        val timeFormatHour = java.text.SimpleDateFormat("HH:00", Locale.getDefault())
        val timeFormatDayWeek = java.text.SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        val timeFormatDayMonth = java.text.SimpleDateFormat("MMM d", Locale.getDefault())
        val timeFormatMonthYear = java.text.SimpleDateFormat("MMM yyyy", Locale.getDefault())

        return when (range) {
            GraphRange.DAY -> {
                val revenues = DoubleArray(24)
                val counts = IntArray(24)
                deliveredOrders.forEach { order ->
                    val hoursAgo = ((now - order.timestamp) / 3_600_000L).toInt()
                    if (hoursAgo in 0..23) {
                        val index = 23 - hoursAgo
                        revenues[index] += order.totalPrice
                        counts[index]++
                    }
                }
                List(24) { i ->
                    val slotTime = now - (23 - i) * 3_600_000L
                    SalesPoint(
                        revenue = revenues[i],
                        orderCount = counts[i],
                        label = timeFormatHour.format(java.util.Date(slotTime))
                    )
                }
            }
            GraphRange.WEEK -> {
                val revenues = DoubleArray(7)
                val counts = IntArray(7)
                deliveredOrders.forEach { order ->
                    val daysAgo = ((startToday - order.timestamp) / 86_400_000L).toInt()
                    if (daysAgo in 0..6) {
                        val index = 6 - daysAgo
                        revenues[index] += order.totalPrice
                        counts[index]++
                    }
                }
                List(7) { i ->
                    val slotTime = startToday - (6 - i) * 86_400_000L
                    val labelText = if (i == 6) "Today" else timeFormatDayWeek.format(java.util.Date(slotTime))
                    SalesPoint(
                        revenue = revenues[i],
                        orderCount = counts[i],
                        label = labelText
                    )
                }
            }
            GraphRange.MONTH -> {
                val revenues = DoubleArray(30)
                val counts = IntArray(30)
                deliveredOrders.forEach { order ->
                    val daysAgo = ((startToday - order.timestamp) / 86_400_000L).toInt()
                    if (daysAgo in 0..29) {
                        val index = 29 - daysAgo
                        revenues[index] += order.totalPrice
                        counts[index]++
                    }
                }
                List(30) { i ->
                    val slotTime = startToday - (29 - i) * 86_400_000L
                    val labelText = if (i == 29) "Today" else timeFormatDayMonth.format(java.util.Date(slotTime))
                    SalesPoint(
                        revenue = revenues[i],
                        orderCount = counts[i],
                        label = labelText
                    )
                }
            }
            GraphRange.YEAR -> {
                val revenues = DoubleArray(12)
                val counts = IntArray(12)
                val calendarNow = java.util.Calendar.getInstance()
                deliveredOrders.forEach { order ->
                    val calendarOrder = java.util.Calendar.getInstance().apply {
                        timeInMillis = order.timestamp
                    }
                    val monthsAgo = (calendarNow.get(java.util.Calendar.YEAR) - calendarOrder.get(java.util.Calendar.YEAR)) * 12 +
                        calendarNow.get(java.util.Calendar.MONTH) - calendarOrder.get(java.util.Calendar.MONTH)
                    if (monthsAgo in 0..11) {
                        val index = 11 - monthsAgo
                        revenues[index] += order.totalPrice
                        counts[index]++
                    }
                }
                List(12) { i ->
                    val cal = (calendarNow.clone() as java.util.Calendar).apply {
                        add(java.util.Calendar.MONTH, -(11 - i))
                    }
                    SalesPoint(
                        revenue = revenues[i],
                        orderCount = counts[i],
                        label = timeFormatMonthYear.format(cal.time)
                    )
                }
            }
        }
    }

    private fun startOfToday(): Long {
        return java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun updateInventoryStats(view: View) {
        val tvInventoryCount = view.findViewById<TextView>(R.id.tvInventoryCount)

        FirebaseHelper.getProductsByStation(currentUsername, false).addOnSuccessListener { result ->
            tvInventoryCount?.text = result.size().toString()
        }
    }

    private fun renderDashboardOrderPreview(container: LinearLayout?, emptyView: View?, orders: List<Order>) {
        if (container == null || emptyView == null) return

        container.removeAllViews()
        val previewOrders = groupCheckoutOrders(orders)
            .sortedWith(compareBy<OrderGroup> { if (it.first.status == "Pending") 0 else 1 }
                .thenByDescending { it.first.timestamp }).take(3)
        emptyView.visibility = if (previewOrders.isEmpty()) View.VISIBLE else View.GONE
        container.visibility = if (previewOrders.isEmpty()) View.GONE else View.VISIBLE
        fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

        previewOrders.forEachIndexed { index, group ->
            val order = group.first
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(4), dp(10), dp(4))
                setBackgroundResource(R.drawable.bg_queue_row)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                    if (index > 0) topMargin = dp(4)
                }
            }
            val labels = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) }
            }
            val title = TextView(this).apply {
                text = order.customerName
                setTextColor(ContextCompat.getColor(this@StationOwnerActivity, R.color.text_primary))
                textSize = 14f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                includeFontPadding = false
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            val details = TextView(this).apply {
                text = group.items.joinToString(" · ") { item ->
                    "${item.quantity} × ${item.productName}"
                }
                setTextColor(ContextCompat.getColor(this@StationOwnerActivity, R.color.text_secondary))
                textSize = 12f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                includeFontPadding = false
            }
            val status = TextView(this).apply {
                text = if (order.status == "Pending") "Pending" else "Preparing"
                textSize = 11f
                includeFontPadding = false
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(dp(8), dp(4), dp(8), dp(4))
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(ContextCompat.getColor(this@StationOwnerActivity,
                        if (order.status == "Pending") R.color.yellow_soft else R.color.sky_blue_light))
                }
                setTextColor(ContextCompat.getColor(this@StationOwnerActivity,
                    if (order.status == "Pending") R.color.warning else R.color.primary_variant))
            }
            labels.addView(title)
            labels.addView(details)
            if (order.isSubscriptionOrder && order.scheduledDeliveryDate > 0L) {
                details.text = "${order.recurringDeliveryLabel()} · ${details.text}"
                row.setOnClickListener {
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Recurring delivery")
                        .setMessage(order.recurringDeliveryNotice())
                        .setPositiveButton("View orders") { _, _ -> loadOrdersView(false) }
                        .setNegativeButton("Close", null).show()
                }
            }
            row.addView(labels)
            row.addView(status)
            row.contentDescription = "${order.customerName}, ${status.text}. ${group.itemSummary()}"
            container.addView(row)
        }
    }

    private fun loadOrdersView(onlyPending: Boolean) {
        FirebaseHelper.processDueSubscriptions(stationOwnerUsername = currentUsername)
        isShowingOnlyPending = onlyPending
        val ordersView = layoutInflater.inflate(R.layout.station_owner_home, null)
        val contentFrame = findViewById<FrameLayout>(R.id.stationContentFrame)
        contentFrame.removeAllViews()
        contentFrame.addView(ordersView)
        
        ordersView.findViewById<View>(R.id.layoutDashboard).visibility = View.GONE
        ordersView.findViewById<View>(R.id.layoutOrders).visibility = View.VISIBLE

        ordersView.findViewById<View>(R.id.layoutAcceptedOrders).visibility = if (isShowingOnlyPending) View.GONE else View.VISIBLE

        ordersContainer = ordersView.findViewById(R.id.ordersContainer)
        acceptedOrdersContainer = ordersView.findViewById(R.id.acceptedOrdersContainer)
        refreshOrders()
    }

    private fun loadInventoryControls() {
        val inventoryView = layoutInflater.inflate(R.layout.station_owner_home, null)
        val contentFrame = findViewById<FrameLayout>(R.id.stationContentFrame)
        contentFrame.removeAllViews()
        contentFrame.addView(inventoryView)

        inventoryView.findViewById<View>(R.id.layoutDashboard).visibility = View.GONE
        inventoryView.findViewById<View>(R.id.layoutInventoryControls).visibility = View.VISIBLE

        val rvInventory = inventoryView.findViewById<RecyclerView>(R.id.rvInventory)
        etName = inventoryView.findViewById(R.id.etProductName)
        etPrice = inventoryView.findViewById(R.id.etProductPrice)
        imagePreview = inventoryView.findViewById(R.id.ivContainerPreview)
        imageStatus = inventoryView.findViewById(R.id.tvContainerImageStatus)
        btnAdd = inventoryView.findViewById(R.id.btnAddProduct)
        defaultImageCards = mapOf(
            DefaultContainerImages.SMALL_BOTTLE to inventoryView.findViewById(R.id.cardDefaultSmallBottle),
            DefaultContainerImages.WATER_JUG to inventoryView.findViewById(R.id.cardDefaultWaterJug),
            DefaultContainerImages.JERRY_CAN to inventoryView.findViewById(R.id.cardDefaultJerryCan)
        )
        defaultImageCards.forEach { (source, card) ->
            card.setOnClickListener { selectDefaultContainerImage(source) }
        }
        updateDefaultImageSelection()
        val switchRefillEnabled = inventoryView.findViewById<
            com.google.android.material.switchmaterial.SwitchMaterial
        >(R.id.switchRefillServiceEnabled)
        val etRefillFee = inventoryView.findViewById<EditText>(R.id.etStationRefillFee)
        val btnSaveRefillSettings = inventoryView.findViewById<MaterialButton>(
            R.id.btnSaveRefillSettings
        )

        rvInventory.adapter = adapter
        rvInventory.layoutManager = LinearLayoutManager(this)

        inventoryView.findViewById<MaterialButton>(R.id.btnSelectImage).setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            startActivityForResult(intent, IMAGE_PICK_CODE)
        }

        btnAdd?.setOnClickListener {
            val name = etName?.text.toString().trim()
            val price = etPrice?.text.toString().toDoubleOrNull()
            if (name.isBlank()) {
                etName?.error = "Enter a container name"
                return@setOnClickListener
            }
            if (price == null || price <= 0.0) {
                etPrice?.error = "Enter a valid selling price"
                return@setOnClickListener
            }
            val existingReusableImage = editingProduct?.imageUri?.takeIf {
                it.startsWith("https://") ||
                    it.startsWith("http://") ||
                    DefaultContainerImages.isDefault(it)
            }
            val selectedOrExistingImage = selectedDefaultImageSource ?: existingReusableImage
            if (selectedImageUri == null && selectedOrExistingImage == null) {
                Toast.makeText(
                    this,
                    "Choose a default image or upload a custom photo",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            btnAdd?.isEnabled = false
            if (selectedImageUri != null) {
                btnAdd?.text = "Uploading Image…"
                FirebaseHelper.uploadContainerImage(currentUsername, selectedImageUri!!)
                    .addOnSuccessListener { downloadUrl ->
                        persistContainer(name, price, downloadUrl)
                    }
                    .addOnFailureListener { error ->
                        btnAdd?.isEnabled = true
                        btnAdd?.text = if (editingProduct == null) "Save Container" else "Update Container"
                        Toast.makeText(
                            this,
                            "Image upload failed: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            } else {
                persistContainer(name, price, selectedOrExistingImage!!)
            }
        }

        editingProduct?.let { product ->
            etName?.setText(product.name)
            etPrice?.setText(product.price.toString())
            product.imageUri?.let {
                imagePreview?.let { preview -> ContainerImageLoader.load(preview, it) }
                imageStatus?.text = DefaultContainerImages.find(it)?.let { defaultImage ->
                    "${defaultImage.label} selected"
                } ?: if (it.startsWith("http")) {
                    "Current custom container image"
                } else {
                    "Choose a new image so customers can see it"
                }
            }
            updateDefaultImageSelection()
            btnAdd?.text = "Update Container"
        }


        FirebaseHelper.getUser(currentUsername).addOnSuccessListener { snapshot ->
            snapshot.toObject(User::class.java)?.let { owner ->
                currentUserData = owner
                switchRefillEnabled.isChecked = owner.refillServiceEnabled
                if (owner.refillFee > 0.0) {
                    etRefillFee.setText(owner.refillFee.toString())
                }
                etRefillFee.isEnabled = owner.refillServiceEnabled
            }
        }

        switchRefillEnabled.setOnCheckedChangeListener { _, enabled ->
            etRefillFee.isEnabled = enabled
            if (!enabled) etRefillFee.error = null
        }

        btnSaveRefillSettings.setOnClickListener {
            val enabled = switchRefillEnabled.isChecked
            val fee = etRefillFee.text.toString().toDoubleOrNull() ?: 0.0
            if (enabled && fee <= 0.0) {
                etRefillFee.error = "Enter a valid refill fee"
                return@setOnClickListener
            }

            btnSaveRefillSettings.isEnabled = false
            FirebaseHelper.getUser(currentUsername)
                .continueWithTask { userTask ->
                    val owner = userTask.result?.toObject(User::class.java)
                        ?: throw IllegalStateException("Owner account was not found")
                    val updated = owner.copy(
                        refillServiceEnabled = enabled,
                        refillFee = if (enabled) fee else 0.0
                    )
                    currentUserData = updated
                    FirebaseHelper.addUser(updated)
                }
                .addOnSuccessListener {
                    btnSaveRefillSettings.isEnabled = true
                    Toast.makeText(this, "Refill settings saved", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { error ->
                    btnSaveRefillSettings.isEnabled = true
                    Toast.makeText(
                        this,
                        "Could not save refill settings: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    private fun loadStationSettings() {
        val etaView = layoutInflater.inflate(R.layout.station_owner_home, null)
        val contentFrame = findViewById<FrameLayout>(R.id.stationContentFrame)
        contentFrame.removeAllViews()
        contentFrame.addView(etaView)

        etaView.findViewById<View>(R.id.layoutDashboard).visibility = View.GONE
        etaView.findViewById<View>(R.id.layoutEtaSettings).visibility = View.VISIBLE

        
        val etDeliveryFee = etaView.findViewById<EditText>(R.id.etDeliveryFee)
        val switchRush = etaView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchRushOrder)
        val etRushFee = etaView.findViewById<EditText>(R.id.etRushOrderFee)
        val btnSaveRush = etaView.findViewById<MaterialButton>(R.id.btnSaveRushSettings)
        val paymentSettings = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }
        paymentSettings.addView(TextView(this).apply {
            text = "GCash payment QR\nUpload your station's GCash receiving QR. Customers will see this at checkout."
        })
        gcashPreview = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, (220 * resources.displayMetrics.density).toInt())
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Station GCash QR code"
            setBackgroundColor(android.graphics.Color.WHITE)
        }
        paymentSettings.addView(gcashPreview)
        gcashUploadButton = MaterialButton(this).apply {
            text = "Upload / Replace GCash QR"
            setOnClickListener { gcashPicker.launch("image/*") }
        }
        paymentSettings.addView(gcashUploadButton)
        (btnSaveRush.parent as android.view.ViewGroup).addView(paymentSettings)
        FirebaseHelper.getUser(currentUsername).addOnSuccessListener { snapshot ->
            gcashPreview?.let { ContainerImageLoader.load(it, snapshot.toObject(User::class.java)?.gcashQrUrl) }
        }

        val etOperatingOpen = etaView.findViewById<EditText>(R.id.etOperatingOpenTime)
        val etOperatingClose = etaView.findViewById<EditText>(R.id.etOperatingCloseTime)
        val operatingStatusGroup = etaView.findViewById<RadioGroup>(R.id.rgOperatingStatus)
        val ownerOperatingStatus = etaView.findViewById<TextView>(R.id.tvOwnerOperatingStatus)
        val btnSaveOperating = etaView.findViewById<MaterialButton>(R.id.btnSaveOperatingHours)

        fun selectedOperatingOverride(): String = when (operatingStatusGroup.checkedRadioButtonId) {
            R.id.rbStatusOpen -> OperatingHours.STATUS_OPEN
            R.id.rbStatusClosed -> OperatingHours.STATUS_CLOSED
            else -> OperatingHours.STATUS_AUTO
        }

        fun renderOperatingStatus(hours: OperatingHours) {
            val previewUser = (currentUserData ?: User()).copy(operatingHours = hours)
            ownerOperatingStatus.text =
                "${previewUser.stationStatusLabel()}  •  ${previewUser.operatingHoursLabel()}"
            ownerOperatingStatus.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (previewUser.isStationOpen()) R.color.success else R.color.error
                )
            )
        }

        fun bindSettings(user: User) {
            currentUserData = user
            etDeliveryFee.setText(String.format(Locale.getDefault(), "%.2f", user.deliveryFee))
            switchRush.isChecked = user.rushOrderEnabled
            etRushFee.setText(String.format(Locale.getDefault(), "%.2f", user.rushOrderFee))
            etOperatingOpen.setText(user.operatingHours.openTime.toDisplayTime())
            etOperatingClose.setText(user.operatingHours.closeTime.toDisplayTime())
            when (user.operatingHours.statusOverride.uppercase(Locale.US)) {
                OperatingHours.STATUS_OPEN -> operatingStatusGroup.check(R.id.rbStatusOpen)
                OperatingHours.STATUS_CLOSED -> operatingStatusGroup.check(R.id.rbStatusClosed)
                else -> operatingStatusGroup.check(R.id.rbStatusSchedule)
            }
            renderOperatingStatus(user.operatingHours)
        }

        fun bindTimePicker(field: EditText, fallback: String, title: String) {
            field.setOnClickListener {
                val time = normalizeOperatingTime(field.text.toString()) ?: fallback
                TimePickerDialog(this, { _, hour, minute ->
                    field.setText(String.format(Locale.US, "%02d:%02d", hour, minute).toDisplayTime())
                    field.error = null
                    val open = normalizeOperatingTime(etOperatingOpen.text.toString()) ?: "08:00"
                    val close = normalizeOperatingTime(etOperatingClose.text.toString()) ?: "18:00"
                    renderOperatingStatus(OperatingHours(open, close, selectedOperatingOverride()))
                }, time.substringBefore(':').toInt(), time.substringAfter(':').toInt(), false)
                    .apply { setTitle(title) }.show()
            }
        }
        bindTimePicker(etOperatingOpen, "08:00", "Opening time")
        bindTimePicker(etOperatingClose, "18:00", "Closing time")

        // Load current settings
        currentUserData?.let(::bindSettings) ?: FirebaseHelper.getUser(currentUsername)
            .addOnSuccessListener { snapshot ->
                snapshot.toObject(User::class.java)?.let(::bindSettings)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Could not load station settings", Toast.LENGTH_SHORT).show()
            }

        operatingStatusGroup.setOnCheckedChangeListener { _, _ ->
            val openTime = normalizeOperatingTime(etOperatingOpen.text.toString()) ?: "08:00"
            val closeTime = normalizeOperatingTime(etOperatingClose.text.toString()) ?: "18:00"
            renderOperatingStatus(
                OperatingHours(openTime, closeTime, selectedOperatingOverride())
            )
        }

        btnSaveOperating.setOnClickListener {
            val openTime = normalizeOperatingTime(etOperatingOpen.text.toString())
            val closeTime = normalizeOperatingTime(etOperatingClose.text.toString())
            if (openTime == null) {
                etOperatingOpen.error = "Select an opening time, such as 8:00AM"
                return@setOnClickListener
            }
            if (closeTime == null) {
                etOperatingClose.error = "Select a closing time, such as 6:00PM"
                return@setOnClickListener
            }

            val hours = OperatingHours(openTime, closeTime, selectedOperatingOverride())
            btnSaveOperating.isEnabled = false
            FirebaseHelper.updateStationOperatingHours(currentUsername, hours)
                .addOnSuccessListener {
                    currentUserData = (currentUserData ?: User(username = currentUsername))
                        .copy(operatingHours = hours)
                    etOperatingOpen.setText(openTime.toDisplayTime())
                    etOperatingClose.setText(closeTime.toDisplayTime())
                    renderOperatingStatus(hours)
                    btnSaveOperating.isEnabled = true
                    Toast.makeText(this, "Operating hours updated", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { error ->
                    btnSaveOperating.isEnabled = true
                    Toast.makeText(
                        this,
                        "Could not update operating hours: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }

        btnSaveRush.setOnClickListener {
            val isEnabled = switchRush.isChecked
            val rushFee = etRushFee.text.toString().toDoubleOrNull() ?: 0.0
            val deliveryFee = etDeliveryFee.text.toString().toDoubleOrNull() ?: 0.0

            if (rushFee < 0.0) {
                etRushFee.error = "Rush fee cannot be negative"
                return@setOnClickListener
            }
            if (deliveryFee < 0.0) {
                etDeliveryFee.error = "Delivery fee cannot be negative"
                return@setOnClickListener
            }
             
            currentUserData?.let { user ->
                val updatedUser = user.copy(
                    rushOrderEnabled = isEnabled, 
                    rushOrderFee = rushFee,
                    deliveryFee = deliveryFee
                )
                btnSaveRush.isEnabled = false
                FirebaseHelper.updateStationPricing(
                    currentUsername,
                    isEnabled,
                    rushFee,
                    deliveryFee
                ).addOnSuccessListener {
                    currentUserData = updatedUser
                    btnSaveRush.isEnabled = true
                    Toast.makeText(
                        this,
                        "Delivery and Priority pricing updated!",
                        Toast.LENGTH_SHORT
                    ).show()
                }.addOnFailureListener { error ->
                    btnSaveRush.isEnabled = true
                    Toast.makeText(
                        this,
                        "Failed to update pricing: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun timeInMinutes(value: String): Int {
        val parts = value.split(":")
        return parts[0].toInt() * 60 + parts[1].toInt()
    }

    private fun selectDefaultContainerImage(source: String) {
        val defaultImage = DefaultContainerImages.find(source) ?: return
        selectedDefaultImageSource = source
        selectedImageUri = null
        imagePreview?.setImageResource(defaultImage.drawableRes)
        imageStatus?.text = "${defaultImage.label} selected"
        updateDefaultImageSelection()
    }

    private fun updateDefaultImageSelection() {
        val selectedStrokeWidth = (2 * resources.displayMetrics.density).toInt().coerceAtLeast(2)
        val normalStrokeWidth = resources.displayMetrics.density.toInt().coerceAtLeast(1)
        defaultImageCards.forEach { (source, card) ->
            val isSelected = source == selectedDefaultImageSource
            card.strokeColor = ContextCompat.getColor(
                this,
                if (isSelected) R.color.primary else R.color.divider
            )
            card.strokeWidth = if (isSelected) selectedStrokeWidth else normalStrokeWidth
            card.isChecked = isSelected
        }
    }

    private fun resetInputFields() {
        etName?.text?.clear()
        etPrice?.text?.clear()
        imagePreview?.setImageResource(R.drawable.ic_placeholder)
        imageStatus?.text = "No image selected"
        selectedImageUri = null
        selectedDefaultImageSource = null
        updateDefaultImageSelection()
        editingProduct = null
        btnAdd?.text = "Save Container"
    }

    private fun refreshOrders() {
        visibleOrdersListener?.remove()
        visibleOrdersListener = FirebaseHelper.ordersCollection
            .whereEqualTo("stationOwnerUsername", currentUsername).addSnapshotListener { result, error ->
            if (!ordersContainer.isAttachedToWindow) return@addSnapshotListener
            if (result == null) {
                Toast.makeText(this, "Could not load orders: ${error?.message}", Toast.LENGTH_LONG).show()
                return@addSnapshotListener
            }
            val orders = result.documents.mapNotNull { document ->
                document.toObject(Order::class.java)?.apply { id = document.id }
            }
            ordersContainer.removeAllViews()
            groupCheckoutOrders(orders.filter { it.status == "Pending" }).forEach(::addIncomingOrder)
            if (!isShowingOnlyPending) {
                acceptedOrdersContainer.removeAllViews()
                groupCheckoutOrders(orders.filter { it.status == "Accepted" }).forEach(::addAcceptedOrder)
            }
        }
    }

    private fun addIncomingOrder(group: OrderGroup) {
        val order = group.first
        val orderView = LayoutInflater.from(this).inflate(R.layout.item_order, ordersContainer, false)
        val tvOrderInfo = orderView.findViewById<TextView>(R.id.tvOrderInfo)
        
        var infoText = "${order.customerName} ordered:\n${group.itemSummary()}"
        if (order.isSubscriptionOrder) {
            infoText = "[RECURRING DELIVERY] $infoText\n${order.recurringDeliveryNotice()}"
        }
        if (order.isRushOrder) {
            infoText = "[RUSH] $infoText\nRush Fee: ₱${String.format(Locale.getDefault(), "%.2f", group.items.sumOf { it.rushOrderFee })}"
            tvOrderInfo.setTextColor(ContextCompat.getColor(this, R.color.warning))
        }
        
        tvOrderInfo.text = "$infoText\nTotal: ₱${String.format(Locale.getDefault(), "%.2f", group.totalPrice)}"
        orderView.findViewById<TextView>(R.id.tvOrderTime).visibility = View.GONE
        orderView.findViewById<TextView>(R.id.tvPaymentMethod).text = "Payment: ${order.paymentMethod}${if(group.isPaid) " (PAID)" else ""}"

        orderView.findViewById<Button>(R.id.btnAccept).setOnClickListener { 
            if (order.paymentMethod == "GCash" && !group.isPaid) {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Verify GCash payment")
                    .setMessage("Check your GCash account for this customer's payment of ₱${String.format(Locale.getDefault(), "%.2f", group.totalPrice)} before accepting all ${group.items.size} item(s).")
                    .setPositiveButton("Payment received") { _, _ ->
                        showDeliveryEtaPicker(group, markPaid = true)
                    }
                    .setNegativeButton("Not yet", null).show()
                return@setOnClickListener
            }
            showDeliveryEtaPicker(group, markPaid = false)
        }
        orderView.findViewById<Button>(R.id.btnReject).setOnClickListener {
            FirebaseHelper.updateOrderGroupStatus(group.ids, "Rejected").addOnSuccessListener {
                refreshOrders()
            }.addOnFailureListener { error ->
                Toast.makeText(this, "Could not reject order: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
        
        orderView.findViewById<ImageButton>(R.id.btnChatWithCustomer).setOnClickListener {
            val intent = Intent(this, SingleChatActivity::class.java)
            intent.putExtra("CURRENT_USERNAME", currentUsername)
            intent.putExtra("CHAT_WITH_USERNAME", order.customerName)
            startActivity(intent)
        }

        val btnPrint = orderView.findViewById<MaterialButton>(R.id.btnPrintReceiptIncoming)
        if (group.isPaid && group.first.deliveryReceipt != null) {
            btnPrint.visibility = View.VISIBLE
            btnPrint.setOnClickListener { ReceiptHelper.printReceipt(this, group.items) }
        } else {
            btnPrint.visibility = View.GONE
        }

        ordersContainer.addView(orderView)
    }

    private fun showDeliveryEtaPicker(group: OrderGroup, markPaid: Boolean) {
        val timeZoneId = currentUserData?.operatingHours?.timeZoneId
            ?.takeIf { it.isNotBlank() }
            ?: DeliveryEta.DEFAULT_TIME_ZONE_ID
        val now = System.currentTimeMillis()
        val today = DeliveryEta.today(now, timeZoneId)
        val tomorrow = DeliveryEta.tomorrow(now, timeZoneId)
        val choices = arrayOf(
            DeliveryEta.label(today, timeZoneId, now),
            DeliveryEta.label(tomorrow, timeZoneId, now),
            "Choose another date"
        )

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Estimated delivery date")
            .setItems(choices) { _, choice ->
                when (choice) {
                    0 -> acceptOrderWithEta(group, today, timeZoneId, markPaid)
                    1 -> acceptOrderWithEta(group, tomorrow, timeZoneId, markPaid)
                    else -> showCustomDeliveryDatePicker(group, timeZoneId, markPaid)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCustomDeliveryDatePicker(
        group: OrderGroup,
        timeZoneId: String,
        markPaid: Boolean
    ) {
        val timeZone = TimeZone.getTimeZone(timeZoneId)
        val initialTimestamp = group.first.scheduledDeliveryDate
            .takeIf { it > 0L && DeliveryEta.isTodayOrFuture(it, timeZoneId = timeZoneId) }
            ?: DeliveryEta.tomorrow(timeZoneId = timeZoneId)
        val initial = Calendar.getInstance(timeZone).apply { timeInMillis = initialTimestamp }

        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selectedDate = DeliveryEta.fromDate(year, month, dayOfMonth, timeZoneId)
                acceptOrderWithEta(group, selectedDate, timeZoneId, markPaid)
            },
            initial.get(Calendar.YEAR),
            initial.get(Calendar.MONTH),
            initial.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.minDate = System.currentTimeMillis() - 60_000L
            setTitle("Choose delivery date")
            show()
        }
    }

    private fun acceptOrderWithEta(
        group: OrderGroup,
        estimatedDeliveryDate: Long,
        timeZoneId: String,
        markPaid: Boolean
    ) {
        FirebaseHelper.acceptOrders(
            orderIds = group.ids,
            estimatedDeliveryDate = estimatedDeliveryDate,
            estimatedDeliveryTimeZoneId = timeZoneId,
            markPaid = markPaid
        ).addOnSuccessListener {
            Toast.makeText(
                this,
                "Order accepted. ETA: ${DeliveryEta.label(estimatedDeliveryDate, timeZoneId)}",
                Toast.LENGTH_LONG
            ).show()
            refreshOrders()
        }.addOnFailureListener { error ->
            Toast.makeText(
                this,
                "Could not accept order: ${error.message ?: "Please try again"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun addAcceptedOrder(group: OrderGroup) {
        val order = group.first
        val orderView = LayoutInflater.from(this).inflate(R.layout.item_accepted_order, acceptedOrdersContainer, false)
        val tvInfo = orderView.findViewById<TextView>(R.id.tvAcceptedOrderInfo)
        val tvPayment = orderView.findViewById<TextView>(R.id.tvAcceptedPaymentMethod)

        var info = "${order.customerName}:\n${group.itemSummary()}\nStatus: ${order.status}"
        if (order.isSubscriptionOrder) {
            info = "[RECURRING DELIVERY] $info\n${order.recurringDeliveryNotice()}"
        }
        if (order.isRushOrder) {
            info = "[RUSH] $info"
            tvInfo.setTextColor(ContextCompat.getColor(this, R.color.warning))
        }
        if (order.estimatedDeliveryDate > 0L) {
            info += "\nEstimated delivery: ${DeliveryEta.label(order.estimatedDeliveryDate, order.estimatedDeliveryTimeZoneId)}"
        }
        tvInfo.text = "$info\nTotal: ₱${String.format(Locale.getDefault(), "%.2f", group.totalPrice)}"
        tvPayment.text = "Payment: ${order.paymentMethod}${if(group.isPaid) " (PAID)" else ""}"
            
        orderView.findViewById<ImageButton>(R.id.btnChatWithCustomerAccepted).setOnClickListener {
            val intent = Intent(this, SingleChatActivity::class.java)
            intent.putExtra("CURRENT_USERNAME", currentUsername)
            intent.putExtra("CHAT_WITH_USERNAME", order.customerName)
            startActivity(intent)
        }

        val btnPrint = orderView.findViewById<MaterialButton>(R.id.btnPrintReceiptAccepted)
        if (group.isPaid && group.first.deliveryReceipt != null) {
            btnPrint.visibility = View.VISIBLE
            btnPrint.setOnClickListener { ReceiptHelper.printReceipt(this, group.items) }
        } else {
            btnPrint.visibility = View.GONE
        }

        val btnComplete = orderView.findViewById<Button>(R.id.btnCompleteOrder)
        if (btnComplete != null) {
            btnComplete.visibility = View.VISIBLE
            btnComplete.setOnClickListener {
                DeliveryReceiptEditor.show(
                    this, group,
                    availableProducts = products.toList(),
                    save = { items -> FirebaseHelper.completeDeliveryWithReceipt(group.ids, currentUsername, items) },
                    onSaved = { receipt ->
                        refreshOrders()
                        Toast.makeText(this, "Delivery confirmed. Receipt saved.", Toast.LENGTH_SHORT).show()
                        ReceiptHelper.printReceipt(this, group.items.map {
                            it.copy(status = "Delivered", isPaid = true, deliveryReceipt = receipt)
                        })
                    }
                )
            }
        }

        acceptedOrdersContainer.addView(orderView)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMAGE_PICK_CODE && resultCode == RESULT_OK) {
            selectedImageUri = data?.data
            selectedImageUri?.let { uri ->
                selectedDefaultImageSource = null
                updateDefaultImageSelection()
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                    // The image remains available for the current session if persistence is unsupported.
                }
                imagePreview?.setImageURI(uri)
                imageStatus?.text = "Custom container image selected"
            }
        }
    }

    private fun persistContainer(name: String, price: Double, imageUrl: String) {
        btnAdd?.text = "Saving…"
        val task = if (editingProduct != null) {
            FirebaseHelper.updateProduct(
                editingProduct!!.copy(name = name, price = price, imageUri = imageUrl)
            )
        } else {
            FirebaseHelper.addProduct(
                Product(
                    name = name,
                    price = price,
                    imageUri = imageUrl,
                    ownerUsername = currentUsername
                )
            )
        }

        task.addOnSuccessListener {
            val wasEditing = editingProduct != null
            resetInputFields()
            btnAdd?.isEnabled = true
            loadProducts()
            Toast.makeText(
                this,
                if (wasEditing) "Container updated!" else "Container added!",
                Toast.LENGTH_SHORT
            ).show()
        }.addOnFailureListener { error ->
            btnAdd?.isEnabled = true
            btnAdd?.text = if (editingProduct == null) "Save Container" else "Update Container"
            Toast.makeText(this, "Could not save container: ${error.message}", Toast.LENGTH_LONG).show()
        }
    }
}
