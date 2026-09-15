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
        return usersCollection.document(username).get()
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
                    item.productPrice
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
                    deliveryTimeSlot = currentStation.etaSettings
                        .deliveryEstimateAt(checkoutTimestamp),
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

    fun cancelExpiredPendingOrders(now: Long = System.currentTimeMillis()): Task<Void> {
        return ordersCollection
            .whereEqualTo("status", "Pending")
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
    private fun validateDelivery(transaction: com.google.firebase.firestore.Transaction, delivery: WeeklySubscription) {
        require(delivery.quantity > 0) { "Enter a positive quantity" }
        require(delivery.repeatEveryDays in 1..3650) { "Choose 1 to 3650 days" }
        val station = transaction.get(usersCollection.document(delivery.stationOwnerUsername)).toObject(User::class.java)
            ?: throw IllegalStateException("Station unavailable")
        check(station.isApprovedStationOwner()) { "Station is not approved" }
        check(delivery.deliveryTimeSlot in station.etaSettings.customerDeliveryWindows()) { "Choose an available delivery window" }
        if (delivery.offeringType == OFFERING_REFILL) {
            check(station.refillServiceEnabled && station.refillFee > 0.0) { "Refill service unavailable" }
        } else {
            val product = transaction.get(productsCollection.document(delivery.productId)).toObject(Product::class.java)
                ?: throw IllegalStateException("Bottle type no longer available")
            check(product.ownerUsername == station.username) { "Choose a bottle from this station" }
        }
    }

    fun addSubscription(subscription: WeeklySubscription): Task<Void> {
        val ref = subscriptionsCollection.document()
        subscription.id = ref.id
        subscription.createdAt = System.currentTimeMillis()
        return db.runTransaction<Void> { transaction ->
            require(subscription.nextDeliveryAt > System.currentTimeMillis()) { "Choose a future delivery date" }
            validateDelivery(transaction, subscription)
            transaction.set(ref, subscription)
            null
        }
    }

    fun updateWeeklyDelivery(delivery: WeeklySubscription): Task<Void> {
        return db.runTransaction<Void> { transaction ->
            val ref = subscriptionsCollection.document(delivery.id)
            val existing = transaction.get(ref).toObject(WeeklySubscription::class.java)
                ?: throw IllegalStateException("Delivery schedule no longer exists")
            check(existing.customerUsername == delivery.customerUsername &&
                existing.stationOwnerUsername == delivery.stationOwnerUsername)
            require(delivery.nextDeliveryAt > System.currentTimeMillis()) { "Choose a future delivery date" }
            validateDelivery(transaction, delivery)
            transaction.set(ref, delivery.copy(active = existing.active,
                createdAt = existing.createdAt, lastOrderId = existing.lastOrderId,
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
            if (active) {
                validateDelivery(transaction, delivery)
                delivery.nextDeliveryAt = DeliveryRecurrence.nextAfter(
                    delivery.nextDeliveryAt, delivery.repeatEveryDays, System.currentTimeMillis())
            }
            transaction.update(ref, mapOf("active" to active, "nextDeliveryAt" to delivery.nextDeliveryAt,
                "lastStatus" to if (active) "Scheduled" else "Paused"))
            null
        }
    }

    fun deleteSubscription(subscriptionId: String): Task<Void> =
        subscriptionsCollection.document(subscriptionId).delete()

    /**
     * Creates each due recurring order at most once. Product stock, current price,
     * customer location and the station's current delivery fee are read inside the
     * same Firestore transaction used to create the order.
     */
    fun processDueSubscriptions(): Task<Void> {
        val now = System.currentTimeMillis()
        return subscriptionsCollection
            .whereEqualTo("active", true)
            .get()
            .continueWithTask { queryTask ->
                val dueTasks = queryTask.result?.documents
                    ?.mapNotNull { document ->
                        val subscription = document.toObject(WeeklySubscription::class.java)
                        if (subscription != null && subscription.nextDeliveryAt <= now) {
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

            if (!subscription.active || subscription.nextDeliveryAt > now) {
                return@runTransaction Unit
            }

            val stationRef = usersCollection.document(subscription.stationOwnerUsername)
            val customerRef = usersCollection.document(subscription.customerUsername)

            // Firestore transactions require all reads before any writes.
            val stationSnapshot = transaction.get(stationRef)
            val customerSnapshot = transaction.get(customerRef)
            val productSnapshot = if (subscription.offeringType != OFFERING_REFILL) {
                transaction.get(productsCollection.document(subscription.productId))
            } else {
                null
            }

            val nextDelivery = DeliveryRecurrence.nextAfter(subscription.nextDeliveryAt, subscription.repeatEveryDays, now)
            val station = stationSnapshot.toObject(User::class.java)
            val customer = customerSnapshot.toObject(User::class.java)
            val product = if (subscription.offeringType != OFFERING_REFILL) {
                productSnapshot?.toObject(Product::class.java)
            } else {
                null
            }
            val offeringUnavailable = if (subscription.offeringType == OFFERING_REFILL) {
                station == null || !station.refillServiceEnabled || station.refillFee <= 0.0
            } else {
                product == null
            }

            if (offeringUnavailable || station == null || customer == null || !station.isApprovedStationOwner() || (product != null && product.ownerUsername != station.username)) {
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
            val orderId = OrderIdGenerator.generate(stationName)
            val orderRef = ordersCollection.document(orderId)
            val deliveryFee = station.deliveryFee
            val offeringId = if (subscription.offeringType == OFFERING_REFILL) {
                subscription.productId.ifBlank { "REFILL_${station.username}" }
            } else {
                product?.id.orEmpty()
            }
            val offeringName = if (subscription.offeringType == OFFERING_REFILL) {
                "Water Refill"
            } else {
                product?.name.orEmpty()
            }
            val offeringPrice = if (subscription.offeringType == OFFERING_REFILL) {
                station.refillFee
            } else {
                product?.price ?: 0.0
            }
            val order = Order(
                id = orderId,
                productId = offeringId,
                productName = offeringName,
                imageUri = product?.imageUri,
                customerName = customer.username,
                customerAddress = customer.address,
                stationOwnerUsername = station.username,
                stationName = stationName,
                quantity = subscription.quantity,
                totalPrice = (offeringPrice * subscription.quantity) + deliveryFee,
                containerType = subscription.containerType,
                deliveryTimeSlot = subscription.deliveryTimeSlot,
                paymentMethod = "Cash on Delivery",
                status = "Pending",
                pendingExpiresAt = now + PENDING_ORDER_TIMEOUT_MILLIS,
                customerLat = customer.latitude,
                customerLon = customer.longitude,
                timestamp = now,
                isRated = false,
                isRushOrder = false,
                rushOrderFee = 0.0,
                deliveryFee = deliveryFee,
                isPaid = false,
                isSubscriptionOrder = true,
                subscriptionId = subscriptionId,
                scheduledDeliveryDate = subscription.nextDeliveryAt,
                offeringType = subscription.offeringType,
                refillServiceId = subscription.refillServiceId,
                refillInstructions = subscription.refillInstructions,
                emptyContainerCount = if (subscription.offeringType == OFFERING_REFILL) {
                    subscription.emptyContainerCount.coerceAtLeast(subscription.quantity)
                } else {
                    0
                }
            )

            transaction.set(orderRef, order)
            transaction.update(
                subscriptionRef,
                mapOf(
                    "nextDeliveryAt" to nextDelivery,
                    "lastOrderId" to orderId,
                    "lastOrderAt" to now,
                    "lastStatus" to "Order created"
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
        return docRef.set(cartItem)
    }

    fun updateCartItem(cartItem: CartItem): Task<Void> {
        return cartCollection.document(cartItem.id).set(cartItem)
    }

    fun getCartItems(customerUsername: String): Task<QuerySnapshot> {
        return cartCollection.whereEqualTo("customerUsername", customerUsername).get()
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
