package com.example.beaqua

import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.storage.FirebaseStorage
import java.security.MessageDigest
import java.util.UUID

object FirebaseHelper {
    private val db = FirebaseFirestore.getInstance()
    
    val usersCollection = db.collection("users")
    val productsCollection = db.collection("products")
    val ordersCollection = db.collection("orders")
    val messagesCollection = db.collection("messages")
    val feedbacksCollection = db.collection("feedbacks")
    val cartCollection = db.collection("cart")
    val subscriptionsCollection = db.collection("subscriptions")
    val notificationsCollection = db.collection("notifications")

    // --- User Methods ---
    fun addUser(user: User): Task<Void> {
        return usersCollection.document(user.username).set(user)
    }

    fun createCustomer(user: User): Task<Void> = db.runTransaction<Void> { transaction ->
        val ref = usersCollection.document(user.username)
        check(!transaction.get(ref).exists()) { "Username already exists" }
        transaction.set(ref, user)
        null
    }

    fun addStationOwnerWithUniqueEmail(user: User): Task<Void> {
        val normalizedEmail = user.emailAddress.trim().lowercase()
        require(normalizedEmail.isNotBlank()) { "Business email is required" }
        user.emailAddress = normalizedEmail

        val userReference = usersCollection.document(user.username)
        val emailReference = db.collection("registered_station_emails")
            .document(normalizedEmail.sha256())

        return db.runTransaction<Void> { transaction ->
            if (transaction.get(userReference).exists()) {
                throw IllegalStateException("Username already exists")
            }
            if (transaction.get(emailReference).exists()) {
                throw IllegalStateException("Business email already exists")
            }

            transaction.set(
                emailReference,
                mapOf("email" to normalizedEmail, "username" to user.username)
            )
            transaction.set(userReference, user)
            null
        }
    }

    fun getUser(username: String): Task<DocumentSnapshot> {
        return traceRequest("user.read", usersCollection.document(username).get())
    }

    fun isEmailRegistered(email: String): Task<Boolean> {
        return usersCollection
            .whereEqualTo("emailAddress", email.trim().lowercase())
            .limit(1)
            .get()
            .continueWith { task ->
                if (!task.isSuccessful) {
                    throw task.exception ?: IllegalStateException("Could not check email")
                }
                !task.result.isEmpty
            }
    }

    private fun String.sha256(): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { "%02x".format(it) }
    }

    fun getAllUsers(): Task<QuerySnapshot> {
        return usersCollection.get()
    }
    
    fun deleteUser(username: String): Task<Void> {
        return usersCollection.document(username).delete()
    }
    
    fun getStationOwners(): Task<QuerySnapshot> {
        return usersCollection.whereEqualTo("accountType", "Station Owner").get()
    }

    fun getApprovedStationOwners(): Task<QuerySnapshot> {
        return usersCollection
            .whereEqualTo("accountType", "Station Owner")
            .whereEqualTo("kycStatus", User.KYC_APPROVED)
            .get()
    }

    fun uploadKycDocument(
        context: android.content.Context,
        username: String,
        documentType: String,
        documentUri: Uri
    ): Task<String> {
        return FirestoreKycDocumentStore.upload(context, username, documentType, documentUri)
    }

    fun updateStationKycStatus(username: String, status: String): Task<Void> {
        require(status == User.KYC_APPROVED || status == User.KYC_REJECTED)
        return usersCollection.document(username).update(
            mapOf(
                "kycStatus" to status,
                "kycReviewedAt" to System.currentTimeMillis()
            )
        )
    }

    fun submitStationKycDocuments(
        username: String,
        barangayClearanceUrl: String,
        sanitaryPermitUrl: String,
        mayorsPermitUrl: String
    ): Task<Void> {
        return usersCollection.document(username).update(
            mapOf(
                "barangayClearanceUrl" to barangayClearanceUrl,
                "sanitaryPermitUrl" to sanitaryPermitUrl,
                "mayorsPermitUrl" to mayorsPermitUrl,
                "kycStatus" to User.KYC_PENDING,
                "kycSubmittedAt" to System.currentTimeMillis(),
                "kycReviewedAt" to 0L
            )
        )
    }

    fun updateUserLocation(username: String, lat: Double, lon: Double, address: String? = null): Task<Void> {
        val updates = mutableMapOf<String, Any>(
            "latitude" to lat,
            "longitude" to lon
        )
        address?.let { updates["address"] = it }
        return usersCollection.document(username).update(updates)
    }

    fun updateStationOperatingHours(
        username: String,
        operatingHours: OperatingHours
    ): Task<Void> {
        return usersCollection.document(username)
            .update("operatingHours", operatingHours)
    }

    fun updateStationEtaSettings(username: String, etaSettings: EtaSettings): Task<Void> {
        return usersCollection.document(username).update("etaSettings", etaSettings)
    }

    fun updateStationPricing(
        username: String,
        rushOrderEnabled: Boolean,
        rushOrderFee: Double,
        deliveryFee: Double
    ): Task<Void> {
        require(rushOrderFee >= 0.0) { "Rush fee cannot be negative" }
        require(deliveryFee >= 0.0) { "Delivery fee cannot be negative" }
        return usersCollection.document(username).update(
            mapOf(
                "rushOrderEnabled" to rushOrderEnabled,
                "rushOrderFee" to rushOrderFee,
                "deliveryFee" to deliveryFee
            )
        )
    }

    // --- Product Methods ---
    fun addProduct(product: Product): Task<Void> {
        val docRef = productsCollection.document()
        product.id = docRef.id
        return docRef.set(product)
    }

    fun updateProduct(product: Product): Task<Void> {
        return productsCollection.document(product.id).set(product)
    }

    fun uploadContainerImage(ownerUsername: String, imageUri: Uri): Task<String> {
        val imageRef = FirebaseStorage.getInstance().reference
            .child("container_images/$ownerUsername/${UUID.randomUUID()}.jpg")
        return imageRef.putFile(imageUri)
            .continueWithTask { uploadTask ->
                if (!uploadTask.isSuccessful) {
                    throw uploadTask.exception ?: IllegalStateException("Image upload failed")
                }
                imageRef.downloadUrl
            }
            .continueWith { downloadTask ->
                if (!downloadTask.isSuccessful) {
                    throw downloadTask.exception ?: IllegalStateException("Image URL was unavailable")
                }
                downloadTask.result.toString()
            }
    }

    fun uploadGcashQr(ownerUsername: String, imageUri: Uri): Task<Void> {
        val reference = FirebaseStorage.getInstance().reference
            .child("gcash_qr/$ownerUsername/${UUID.randomUUID()}")
        return reference.putFile(imageUri).continueWithTask { upload ->
            if (!upload.isSuccessful) throw upload.exception ?: IllegalStateException("QR upload failed")
            reference.downloadUrl
        }.continueWithTask { download ->
            if (!download.isSuccessful) throw download.exception ?: IllegalStateException("QR URL unavailable")
            usersCollection.document(ownerUsername).update("gcashQrUrl", download.result.toString())
        }
    }

    fun getProductsByStation(ownerUsername: String, isAdmin: Boolean = false): Task<QuerySnapshot> {
        return if (isAdmin) {
            productsCollection.get()
        } else {
            productsCollection.whereEqualTo("ownerUsername", ownerUsername).get()
        }
    }

    // --- Order Methods ---
    fun addOrder(order: Order): Task<Void> {
        val orderId = order.id.ifBlank {
            OrderIdGenerator.generate(order.stationName.ifBlank { order.stationOwnerUsername })
        }
        val docRef = ordersCollection.document(orderId)
        val now = System.currentTimeMillis()
        order.id = orderId
        if (order.timestamp <= 0L) order.timestamp = now
        if (order.status == "Pending" && order.pendingExpiresAt <= 0L) {
            order.pendingExpiresAt = order.timestamp + PENDING_ORDER_TIMEOUT_MILLIS
        }
        return docRef.set(order)
    }

    /** Atomically verifies that listed containers still exist and creates orders. */
    fun placeOrders(user: User, cartItems: List<CartItem>, selectedPayment: String, isPaid: Boolean, isRush: Boolean, rushOrderFee: Double, deliveryFee: Double): Task<String?> {
        return db.runTransaction { transaction ->
            if (cartItems.isEmpty()) throw IllegalStateException("The cart is empty")
            require(cartItems.map { it.id }.distinct().size == cartItems.size) { "Duplicate cart items. Refresh your cart." }
            // Reading cart entries makes concurrent checkouts conflict and retry before creating orders.
            for (item in cartItems) {
                require(item.id.isNotBlank() && item.quantity > 0) { "Invalid cart item. Refresh your cart." }
                val saved = transaction.get(cartCollection.document(item.id)).toObject(CartItem::class.java)
                    ?: error("This cart was already checked out or changed. Refresh your cart.")
                check(saved.customerUsername == user.username && saved.productId == item.productId &&
                    saved.stationOwnerUsername == item.stationOwnerUsername && saved.quantity == item.quantity &&
                    saved.offeringType == item.offeringType && saved.refillInstructions == item.refillInstructions) {
                    "Your cart changed. Refresh it before checking out."
                }
            }
            require(cartItems.map { it.stationOwnerUsername }.distinct().size == 1) {
                "Check out one water station at a time"
            }
            val currentProducts = mutableMapOf<String, Product>()
            val purchaseIds = cartItems
                .filter { it.offeringType != OFFERING_REFILL }
                .map { it.productId }
                .distinct()
            val refillOwnerUsernames = cartItems
                .filter { it.offeringType == OFFERING_REFILL }
                .map { it.stationOwnerUsername }
                .distinct()
            val stationUsernames = cartItems
                .map { it.stationOwnerUsername }
                .filter { it.isNotBlank() }
                .distinct()
            val orderStations = mutableMapOf<String, User>()

            // Firestore transactions require all reads before writes.
            for (productId in purchaseIds) {
                val productRef = productsCollection.document(productId)
                val snapshot = transaction.get(productRef)
                if (!snapshot.exists()) {
                    throw Exception("A selected container is no longer offered by the station")
                }
                currentProducts[productId] = snapshot.toObject(Product::class.java)
                    ?: error("A selected container is unavailable")
            }
            for (ownerUsername in stationUsernames) {
                val stationSnapshot = transaction.get(usersCollection.document(ownerUsername))
                val station = stationSnapshot.toObject(User::class.java)
                    ?: throw Exception("The selected water station is unavailable")
                if (!station.isApprovedStationOwner()) {
                    throw Exception("The selected water station is not approved")
                }
                if (!station.isStationOpen()) {
                    throw Exception(
                        "${station.name.ifBlank { "The selected station" }} is currently closed"
                    )
                }
                orderStations[ownerUsername] = station
            }
            for (item in cartItems) {
                val station = orderStations.getValue(item.stationOwnerUsername)
                val product = currentProducts[item.productId]
                if (item.offeringType != OFFERING_REFILL) {
                    check(product != null && product.ownerUsername == station.username) { "Product does not belong to this station" }
                    check(product.price == item.productPrice) { "A product price changed. Review your cart and try again." }
                }
                check(station.deliveryFee == deliveryFee &&
                    (!isRush || (station.rushOrderEnabled && station.rushOrderFee == rushOrderFee))) {
                    "Station delivery fees changed. Review your cart and try again."
                }
                if (item.offeringType == OFFERING_REFILL) {
                    check(station.refillFee == item.productPrice) { "The refill price changed. Review your cart and try again." }
                }
            }
            for (ownerUsername in refillOwnerUsernames) {
                val station = orderStations[ownerUsername]
                if (
                    station == null ||
                    !station.refillServiceEnabled ||
                    station.refillFee <= 0.0
                ) {
                    throw Exception("A selected refill service is no longer available")
                }
            }

            val totalOrders = cartItems.size
            val rushFeePerItem = if (isRush) rushOrderFee / totalOrders else 0.0
            val deliveryFeePerItem = deliveryFee / totalOrders
            val checkoutTimestamp = System.currentTimeMillis()

            // Create Order documents and Delete Cart items
            for (item in cartItems) {
                val currentStation = orderStations[item.stationOwnerUsername]
                    ?: throw Exception("The selected water station is unavailable")
                val stationName = currentStation.name.ifBlank {
                    item.stationName.ifBlank { currentStation.username }
                }
                val orderId = OrderIdGenerator.generate(stationName)
                val orderDoc = ordersCollection.document(orderId)
                val currentUnitPrice = if (item.offeringType == OFFERING_REFILL) {
                    currentStation.refillFee
                } else {
                    currentProducts.getValue(item.productId).price
                }
                val order = Order(
                    id = orderId,
                    productId = item.productId,
                    productName = item.productName,
                    imageUri = item.imageUri,
                    customerName = user.username,
                    customerAddress = user.address,
                    stationOwnerUsername = item.stationOwnerUsername,
                    stationName = stationName,
                    quantity = item.quantity,
                    totalPrice = (currentUnitPrice * item.quantity) + rushFeePerItem + deliveryFeePerItem,
                    containerType = item.containerType,
                    paymentMethod = selectedPayment,
                    status = "Pending",
                    pendingExpiresAt = checkoutTimestamp + PENDING_ORDER_TIMEOUT_MILLIS,
                    customerLat = user.latitude,
                    customerLon = user.longitude,
                    timestamp = checkoutTimestamp,
                    isRated = false,
                    isRushOrder = isRush,
                    rushOrderFee = rushFeePerItem,
                    deliveryFee = deliveryFeePerItem,
                    isPaid = isPaid,
                    offeringType = item.offeringType,
                    refillServiceId = item.refillServiceId,
                    refillInstructions = item.refillInstructions,
                    emptyContainerCount = if (item.offeringType == OFFERING_REFILL) {
                        item.emptyContainerCount.coerceAtLeast(item.quantity)
                    } else {
                        0
                    }
                )
                transaction.set(orderDoc, order)
                transaction.delete(cartCollection.document(item.id))
            }

            null // Return null indicating success
        }.continueWith { task ->
            if (task.isSuccessful) {
                null
            } else {
                task.exception?.message ?: "Order processing failed"
            }
        }
    }

    fun getOrdersForStation(ownerUsername: String, isAdmin: Boolean = false): Task<QuerySnapshot> {
        return if (isAdmin) {
            ordersCollection.get()
        } else {
            ordersCollection.whereEqualTo("stationOwnerUsername", ownerUsername).get()
        }
    }

    fun getOrdersForCustomer(customerUsername: String): Task<QuerySnapshot> {
        return ordersCollection.whereEqualTo("customerName", customerUsername).get()
    }
    
    fun updateOrderStatus(orderId: String, status: String, isPaid: Boolean? = null): Task<Void> {
        val updates = mutableMapOf<String, Any>("status" to status)
        if (isPaid != null) {
            updates["isPaid"] = isPaid
        }
        return ordersCollection.document(orderId).update(updates)
    }

    fun acceptOrder(
        orderId: String,
        estimatedDeliveryDate: Long,
        estimatedDeliveryTimeZoneId: String,
        markPaid: Boolean = false
    ): Task<Void> {
        require(orderId.isNotBlank()) { "Order ID is required" }
        require(
            DeliveryEta.isTodayOrFuture(
                estimatedDeliveryDate,
                timeZoneId = estimatedDeliveryTimeZoneId
            )
        ) { "Delivery ETA must be today or a future date" }

        val orderReference = ordersCollection.document(orderId)
        return db.runTransaction<Void> { transaction ->
            val order = transaction.get(orderReference).toObject(Order::class.java)
                ?: throw IllegalStateException("Order no longer exists")
            check(order.status == "Pending") { "Only pending orders can be accepted" }

            val updates = mutableMapOf<String, Any>(
                "status" to "Accepted",
                "estimatedDeliveryDate" to DeliveryEta.normalize(
                    estimatedDeliveryDate,
                    estimatedDeliveryTimeZoneId
                ),
                "estimatedDeliveryTimeZoneId" to estimatedDeliveryTimeZoneId
            )
            if (markPaid) updates["isPaid"] = true
            transaction.update(orderReference, updates)
            null
        }
    }

    fun cancelExpiredPendingOrders(now: Long = System.currentTimeMillis(),
                                   customerUsername: String? = null, stationOwnerUsername: String? = null): Task<Void> {
        var query: Query = ordersCollection.whereEqualTo("status", "Pending")
        if (customerUsername != null) query = query.whereEqualTo("customerName", customerUsername)
        if (stationOwnerUsername != null) query = query.whereEqualTo("stationOwnerUsername", stationOwnerUsername)
        return query
            .get()
            .continueWithTask { queryTask ->
                if (!queryTask.isSuccessful) {
                    throw queryTask.exception
                        ?: IllegalStateException("Could not check pending orders")
                }
                val cancellationTasks = queryTask.result.documents.mapNotNull { document ->
                    val order = document.toObject(Order::class.java)
                    if (order?.isExpiredPending(now) == true) {
                        cancelExpiredPendingOrder(document.id, now)
                    } else {
                        null
                    }
                }
                if (cancellationTasks.isEmpty()) {
                    Tasks.forResult(null)
                } else {
                    Tasks.whenAll(cancellationTasks)
                }
            }
    }

    private fun cancelExpiredPendingOrder(orderId: String, now: Long): Task<Void> {
        return db.runTransaction<Void> { transaction ->
            val orderRef = ordersCollection.document(orderId)
            val order = transaction.get(orderRef).toObject(Order::class.java)
                ?: return@runTransaction null
            if (!order.isExpiredPending(now)) return@runTransaction null

            val reason = "Automatically cancelled because the station did not accept it within 24 hours."
            transaction.update(
                orderRef,
                mapOf(
                    "status" to "Cancelled",
                    "pendingExpiresAt" to order.pendingDeadline(),
                    "autoCancelledAt" to now,
                    "cancellationReason" to reason
                )
            )

            val stationName = order.stationName.ifBlank { order.stationOwnerUsername }
            if (order.customerName.isNotBlank()) {
                val customerNotificationRef = notificationsCollection
                    .document("${orderId}_customer_pending_timeout")
                transaction.set(
                    customerNotificationRef,
                    BeAquaNotification(
                        id = customerNotificationRef.id,
                        recipientUsername = order.customerName,
                        title = "Order automatically cancelled",
                        message = "Your ${order.productName} order from $stationName was cancelled " +
                            "because it stayed pending for 24 hours.",
                        type = BeAquaNotification.TYPE_ORDER_AUTO_CANCELLED,
                        orderId = orderId,
                        createdAt = now
                    )
                )
            }
            if (order.stationOwnerUsername.isNotBlank()) {
                val stationNotificationRef = notificationsCollection
                    .document("${orderId}_station_pending_timeout")
                transaction.set(
                    stationNotificationRef,
                    BeAquaNotification(
                        id = stationNotificationRef.id,
                        recipientUsername = order.stationOwnerUsername,
                        title = "Pending order automatically cancelled",
                        message = "${order.customerName}'s ${order.productName} order was cancelled " +
                            "because it was not accepted within 24 hours.",
                        type = BeAquaNotification.TYPE_ORDER_AUTO_CANCELLED,
                        orderId = orderId,
                        createdAt = now
                    )
                )
            }
            null
        }
    }

    fun markNotificationDelivered(notificationId: String): Task<Void> {
        return notificationsCollection.document(notificationId)
            .update("deliveredAt", System.currentTimeMillis())
    }

    // --- Automated delivery schedules ---
    private data class DeliveryResources(
        val station: User,
        val productsById: Map<String, Product>,
        val items: List<RecurringDeliveryItem>
    )

    private fun validateDelivery(
        transaction: com.google.firebase.firestore.Transaction,
        delivery: WeeklySubscription
    ): DeliveryResources {
        require(delivery.repeatEveryDays in 1..3650) { "Choose 1 to 3650 days" }
        val station = transaction.get(usersCollection.document(delivery.stationOwnerUsername)).toObject(User::class.java)
            ?: throw IllegalStateException("Station unavailable")
        check(station.isApprovedStationOwner()) { "Station is not approved" }
        require(DeliveryFinalization.canEdit(delivery.nextDeliveryAt, station.operatingHours, System.currentTimeMillis())) {
            "Editing closes when the station opens on delivery day. Choose a later delivery date."
        }
        require(delivery.offeringType == OFFERING_PURCHASE) {
            "Recurring delivery is available for station sale items only"
        }
        val items = delivery.deliveryItems()
        require(items.isNotEmpty()) { "Select at least one item" }
        require(items.all { it.quantity > 0 }) { "Enter a positive quantity for every selected item" }
        require(items.map { it.productId }.distinct().size == items.size) { "Each item can only be selected once" }
        val productsById = items.associate { item ->
            val product = transaction.get(productsCollection.document(item.productId)).toObject(Product::class.java)
                ?: throw IllegalStateException("${item.productName.ifBlank { "An item" }} is no longer available")
            check(product.ownerUsername == station.username) { "Choose items from this station" }
            item.productId to product
        }
        return DeliveryResources(station, productsById, items)
    }

    fun addSubscription(subscription: WeeklySubscription, orderToday: Boolean = false): Task<Void> {
        val ref = subscriptionsCollection.document()
        val now = System.currentTimeMillis()
        subscription.id = ref.id
        subscription.createdAt = now
        val firstOrderIds = if (orderToday) {
            subscription.deliveryItems().map {
                OrderIdGenerator.generate(subscription.stationName.ifBlank { subscription.stationOwnerUsername })
            }
        } else {
            emptyList()
        }
        return db.runTransaction<Void> { transaction ->
            val resources = validateDelivery(transaction, subscription)
            val customer = if (orderToday) {
                transaction.get(usersCollection.document(subscription.customerUsername)).toObject(User::class.java)
                    ?: throw IllegalStateException("Customer account unavailable")
            } else {
                null
            }

            if (orderToday) {
                check(resources.station.isStationOpen(
                    java.util.Calendar.getInstance(
                        java.util.TimeZone.getTimeZone(resources.station.operatingHours.timeZoneId)
                    )
                )) { "${resources.station.name.ifBlank { "This station" }} is currently closed" }
                check(!customer?.address.isNullOrBlank()) { "Add your delivery address in Profile first" }

                val deliveryFeePerItem = resources.station.deliveryFee / resources.items.size
                resources.items.forEachIndexed { index, item ->
                    val product = resources.productsById.getValue(item.productId)
                    val orderId = firstOrderIds[index]
                    val order = Order(
                        id = orderId,
                        productId = item.productId,
                        productName = product.name,
                        imageUri = product.imageUri,
                        customerName = customer!!.username,
                        customerAddress = customer.address,
                        stationOwnerUsername = resources.station.username,
                        stationName = resources.station.name.ifBlank { resources.station.username },
                        quantity = item.quantity,
                        totalPrice = (product.price * item.quantity) + deliveryFeePerItem,
                        containerType = product.name,
                        paymentMethod = "Cash on Delivery",
                        status = "Pending",
                        pendingExpiresAt = now + PENDING_ORDER_TIMEOUT_MILLIS,
                        customerLat = customer.latitude,
                        customerLon = customer.longitude,
                        timestamp = now,
                        isRated = false,
                        isRushOrder = false,
                        rushOrderFee = 0.0,
                        deliveryFee = deliveryFeePerItem,
                        isPaid = false,
                        isSubscriptionOrder = true,
                        subscriptionId = ref.id,
                        scheduledDeliveryDate = DeliveryEta.today(
                            now,
                            resources.station.operatingHours.timeZoneId
                        ),
                        offeringType = OFFERING_PURCHASE
                    )
                    transaction.set(ordersCollection.document(orderId), order)
                }
                subscription.lastOrderId = firstOrderIds.firstOrNull().orEmpty()
                subscription.lastOrderIds = firstOrderIds
                subscription.lastOrderAt = now
                subscription.lastStatus = "${firstOrderIds.size} order item(s) created today"
            }
            transaction.set(ref, subscription)
            null
        }
    }

    fun updateWeeklyDelivery(delivery: WeeklySubscription, original: WeeklySubscription): Task<Void> {
        return db.runTransaction<Void> { transaction ->
            val ref = subscriptionsCollection.document(delivery.id)
            val existing = transaction.get(ref).toObject(WeeklySubscription::class.java)
                ?: throw IllegalStateException("Delivery schedule no longer exists")
            check(existing.customerUsername == delivery.customerUsername &&
                existing.stationOwnerUsername == delivery.stationOwnerUsername)
            check(existing.nextDeliveryAt == original.nextDeliveryAt && existing.lastOrderAt == original.lastOrderAt) {
                "This delivery has changed. Reopen it to edit the current schedule."
            }
            requireDeliveryEditable(transaction, existing)
            validateDelivery(transaction, delivery)
            transaction.set(ref, delivery.copy(active = existing.active,
                createdAt = existing.createdAt, lastOrderId = existing.lastOrderId,
                lastOrderIds = existing.lastOrderIds,
                lastOrderAt = existing.lastOrderAt, lastStatus = if (existing.active) "Scheduled" else "Paused"))
            null
        }
    }

    fun getSubscriptionsForCustomer(customerUsername: String): Task<QuerySnapshot> =
        subscriptionsCollection.whereEqualTo("customerUsername", customerUsername).get()

    fun updateSubscriptionActive(subscriptionId: String, active: Boolean): Task<Void> {
        return db.runTransaction<Void> { transaction ->
            val ref = subscriptionsCollection.document(subscriptionId)
            val delivery = transaction.get(ref).toObject(WeeklySubscription::class.java)
                ?: throw IllegalStateException("Delivery schedule no longer exists")
            if (delivery.active) requireDeliveryEditable(transaction, delivery)
            if (active) {
                val station = transaction.get(usersCollection.document(delivery.stationOwnerUsername))
                    .toObject(User::class.java) ?: error("Station unavailable")
                if (!DeliveryFinalization.canEdit(delivery.nextDeliveryAt, station.operatingHours, System.currentTimeMillis())) {
                    delivery.nextDeliveryAt = DeliveryFinalization.nextDelivery(
                        delivery.nextDeliveryAt, delivery.repeatEveryDays, station.operatingHours, System.currentTimeMillis())
                }
                validateDelivery(transaction, delivery)
            }
            transaction.update(ref, mapOf("active" to active, "nextDeliveryAt" to delivery.nextDeliveryAt,
                "lastStatus" to if (active) "Scheduled" else "Paused"))
            null
        }
    }

    private fun requireDeliveryEditable(transaction: com.google.firebase.firestore.Transaction, delivery: WeeklySubscription) {
        val station = transaction.get(usersCollection.document(delivery.stationOwnerUsername))
            .toObject(User::class.java) ?: error("Station unavailable")
        check(DeliveryFinalization.canEdit(delivery.nextDeliveryAt, station.operatingHours, System.currentTimeMillis())) {
            "This delivery is finalized: the station's opening-time cutoff has passed. Refresh to manage the next delivery."
        }
    }

    fun deleteSubscription(subscriptionId: String): Task<Void> = db.runTransaction<Void> { transaction ->
        val ref = subscriptionsCollection.document(subscriptionId)
        val delivery = transaction.get(ref).toObject(WeeklySubscription::class.java)
        if (delivery != null) {
            if (delivery.active) requireDeliveryEditable(transaction, delivery)
            transaction.delete(ref)
        }
        null
    }

    /**
     * Creates each due recurring order at most once. Product stock, current price,
     * customer location and the station's current delivery fee are read inside the
     * same Firestore transaction used to create the order.
     */
    fun processDueSubscriptions(customerUsername: String? = null, stationOwnerUsername: String? = null): Task<Void> {
        val now = System.currentTimeMillis()
        var query: Query = subscriptionsCollection.whereEqualTo("active", true)
        if (customerUsername != null) query = query.whereEqualTo("customerUsername", customerUsername)
        if (stationOwnerUsername != null) query = query.whereEqualTo("stationOwnerUsername", stationOwnerUsername)
        return query
            .get()
            .continueWithTask { queryTask ->
                val dueTasks = queryTask.result?.documents
                    ?.mapNotNull { document ->
                        val subscription = document.toObject(WeeklySubscription::class.java)
                        // Opening-time adjustment can move a saved timestamp within its calendar day.
                        // Dates over two days away cannot be due, even across timezone/DST changes.
                        if (subscription != null && subscription.nextDeliveryAt <= now + 48L * 60 * 60 * 1000) {
                            processDueSubscription(document.id, now)
                        } else {
                            null
                        }
                    }
                    ?: emptyList()

                if (dueTasks.isEmpty()) {
                    Tasks.forResult(null)
                } else {
                    Tasks.whenAll(dueTasks)
                }
            }
    }

    private fun processDueSubscription(subscriptionId: String, now: Long): Task<Unit> {
        return db.runTransaction { transaction ->
            val subscriptionRef = subscriptionsCollection.document(subscriptionId)
            val subscriptionSnapshot = transaction.get(subscriptionRef)
            val subscription = subscriptionSnapshot.toObject(WeeklySubscription::class.java)
                ?: return@runTransaction Unit

            if (!subscription.active) {
                return@runTransaction Unit
            }
            if (subscription.offeringType != OFFERING_PURCHASE) {
                transaction.update(
                    subscriptionRef,
                    mapOf(
                        "active" to false,
                        "lastStatus" to "Paused: recurring refills are no longer supported"
                    )
                )
                return@runTransaction Unit
            }
            val items = subscription.deliveryItems()
            if (items.isEmpty()) {
                transaction.update(
                    subscriptionRef,
                    mapOf("active" to false, "lastStatus" to "Paused: no recurring items selected")
                )
                return@runTransaction Unit
            }

            val stationRef = usersCollection.document(subscription.stationOwnerUsername)
            val customerRef = usersCollection.document(subscription.customerUsername)

            // Firestore transactions require all reads before any writes.
            val stationSnapshot = transaction.get(stationRef)
            val station = stationSnapshot.toObject(User::class.java)
            val hours = station?.operatingHours ?: OperatingHours()
            if (DeliveryFinalization.canEdit(subscription.nextDeliveryAt, hours, now)) return@runTransaction Unit
            val customerSnapshot = transaction.get(customerRef)
            val productsById = items.associate { item ->
                item.productId to transaction.get(productsCollection.document(item.productId))
                    .toObject(Product::class.java)
            }

            val nextDelivery = DeliveryFinalization.nextDelivery(subscription.nextDeliveryAt, subscription.repeatEveryDays, hours, now)
            val customer = customerSnapshot.toObject(User::class.java)
            val offeringUnavailable = productsById.values.any { it == null } ||
                (station != null && productsById.values.filterNotNull().any { it.ownerUsername != station.username })

            if (offeringUnavailable || station == null || customer == null || !station.isApprovedStationOwner()) {
                transaction.update(
                    subscriptionRef,
                    mapOf(
                        "nextDeliveryAt" to nextDelivery,
                        "lastStatus" to "Skipped: account or service unavailable"
                    )
                )
                return@runTransaction Unit
            }

            val stationName = station.name.ifBlank { station.username }
            val deliveryFeePerItem = station.deliveryFee / items.size
            val orderIds = items.map { OrderIdGenerator.generate(stationName) }
            items.forEachIndexed { index, item ->
                val product = productsById.getValue(item.productId)!!
                val orderId = orderIds[index]
                val order = Order(
                    id = orderId,
                    productId = item.productId,
                    productName = product.name,
                    imageUri = product.imageUri,
                    customerName = customer.username,
                    customerAddress = customer.address,
                    stationOwnerUsername = station.username,
                    stationName = stationName,
                    quantity = item.quantity,
                    totalPrice = (product.price * item.quantity) + deliveryFeePerItem,
                    containerType = product.name,
                    paymentMethod = "Cash on Delivery",
                    status = "Pending",
                    pendingExpiresAt = now + PENDING_ORDER_TIMEOUT_MILLIS,
                    customerLat = customer.latitude,
                    customerLon = customer.longitude,
                    timestamp = now,
                    isRated = false,
                    isRushOrder = false,
                    rushOrderFee = 0.0,
                    deliveryFee = deliveryFeePerItem,
                    isPaid = false,
                    isSubscriptionOrder = true,
                    subscriptionId = subscriptionId,
                    scheduledDeliveryDate = subscription.nextDeliveryAt,
                    offeringType = OFFERING_PURCHASE
                )
                transaction.set(ordersCollection.document(orderId), order)
            }
            transaction.update(
                subscriptionRef,
                mapOf(
                    "nextDeliveryAt" to nextDelivery,
                    "lastOrderId" to orderIds.first(),
                    "lastOrderIds" to orderIds,
                    "lastOrderAt" to now,
                    "lastStatus" to "${orderIds.size} order item(s) created"
                )
            )
            Unit
        }
    }

    fun markOrderAsRated(orderId: String): Task<Void> {
        return ordersCollection.document(orderId).update("isRated", true)
    }

    // --- Cart Methods ---
    fun addToCart(cartItem: CartItem): Task<Void> {
        val docRef = cartCollection.document()
        cartItem.id = docRef.id
        return traceRequest("cart.add", docRef.set(cartItem))
    }

    fun updateCartItem(cartItem: CartItem): Task<Void> {
        return cartCollection.document(cartItem.id).set(cartItem)
    }

    fun incrementCartItem(cartItem: CartItem, additionalQuantity: Int): Task<Void> {
        require(additionalQuantity > 0)
        val updates = mutableMapOf<String, Any>(
            "quantity" to com.google.firebase.firestore.FieldValue.increment(additionalQuantity.toLong()),
            "totalPrice" to com.google.firebase.firestore.FieldValue.increment(cartItem.productPrice * additionalQuantity))
        if (cartItem.offeringType == OFFERING_REFILL) {
            updates["emptyContainerCount"] = com.google.firebase.firestore.FieldValue.increment(additionalQuantity.toLong())
        }
        return traceRequest("cart.increment", cartCollection.document(cartItem.id).update(updates))
    }

    private fun <T> traceRequest(operation: String, task: Task<T>): Task<T> {
        val startedAt = android.os.SystemClock.elapsedRealtime()
        if (BuildConfig.DEBUG) task.addOnCompleteListener {
            android.util.Log.d("BeAquaPerformance", "$operation completed in ${android.os.SystemClock.elapsedRealtime() - startedAt} ms; success=${it.isSuccessful}")
        }
        return task
    }

    fun getCartItems(customerUsername: String): Task<QuerySnapshot> {
        return traceRequest("cart.read", cartCollection.whereEqualTo("customerUsername", customerUsername).get())
    }

    fun removeCartItem(cartItemId: String): Task<Void> {
        return cartCollection.document(cartItemId).delete()
    }

    fun clearCart(customerUsername: String): Task<Void> {
        return cartCollection.whereEqualTo("customerUsername", customerUsername).get().continueWithTask { task ->
            val batch = db.batch()
            for (doc in task.result!!) {
                batch.delete(doc.reference)
            }
            batch.commit()
        }
    }

    // --- Message Methods ---
    fun generateChatId(u1: String, u2: String): String {
        return if (u1 < u2) "${u1}_${u2}" else "${u2}_${u1}"
    }

    fun sendMessage(message: Message): Task<Void> {
        val docRef = messagesCollection.document()
        message.id = docRef.id
        message.timestamp = System.currentTimeMillis()
        message.chatId = generateChatId(message.senderUsername, message.receiverUsername)
        return docRef.set(message)
    }

    fun getMessagesBetween(user1: String, user2: String): Query {
        val chatId = generateChatId(user1, user2)
        return messagesCollection.whereEqualTo("chatId", chatId)
    }
    
    fun getChatUsersForUser(username: String): Task<QuerySnapshot> {
        return ordersCollection.whereEqualTo("customerName", username).get()
    }
    
    fun getChatUsersForStation(username: String): Task<QuerySnapshot> {
        return ordersCollection.whereEqualTo("stationOwnerUsername", username).get()
    }

    // --- Feedback Methods ---
    fun addFeedback(feedback: Feedback): Task<Void> {
        val docRef = feedbacksCollection.document()
        feedback.id = docRef.id
        feedback.timestamp = System.currentTimeMillis()
        return docRef.set(feedback)
    }

    fun getFeedbacksForStation(ownerUsername: String, isAdmin: Boolean = false): Task<QuerySnapshot> {
        return if (isAdmin) {
            feedbacksCollection.get()
        } else {
            feedbacksCollection.whereEqualTo("stationOwnerUsername", ownerUsername).get()
        }
    }
}
