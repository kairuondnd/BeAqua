package com.example.beaqua

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "beaqua.db"
        private const val DATABASE_VERSION = 7

        private const val TABLE_USERS = "users"
        private const val TABLE_PRODUCTS = "products"
        private const val TABLE_ORDERS = "orders"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        // Users table
        val createUsers = """
            CREATE TABLE $TABLE_USERS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                contactNumber TEXT,
                address TEXT,
                username TEXT UNIQUE NOT NULL,
                password TEXT NOT NULL,
                accountType TEXT NOT NULL
            )
        """.trimIndent()
        db?.execSQL(createUsers)

        // Default admin
        val cvAdmin = ContentValues().apply {
            put("name", "Admin")
            put("contactNumber", "")
            put("address", "")
            put("username", "admin")
            put("password", "admin")
            put("accountType", "Admin")
        }
        db?.insert(TABLE_USERS, null, cvAdmin)

        // Products table
        val createProducts = """
            CREATE TABLE $TABLE_PRODUCTS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                price REAL NOT NULL,
                image TEXT,
                ownerUsername TEXT NOT NULL
            )
        """.trimIndent()
        db?.execSQL(createProducts)

        // Orders table
        val createOrders = """
            CREATE TABLE $TABLE_ORDERS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                productName TEXT NOT NULL,
                customerName TEXT NOT NULL,
                stationOwnerUsername TEXT NOT NULL,
                quantity INTEGER NOT NULL,
                containerType TEXT,
                deliveryTimeSlot TEXT,
                status TEXT DEFAULT 'Pending'
            )
        """.trimIndent()
        db?.execSQL(createOrders)

        val createMessages = """
            CREATE TABLE messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                senderUsername TEXT,
                receiverUsername TEXT,
                message TEXT,
                timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """.trimIndent()
        db?.execSQL(createMessages)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        // Resetting the database for the major revision
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_USERS")
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_PRODUCTS")
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_ORDERS")
        db?.execSQL("DROP TABLE IF EXISTS messages")
        onCreate(db)
    }

    // ----------------------------------------------------------
    // USERS
    // ----------------------------------------------------------

    fun insertUser(
        name: String,
        contact: String,
        address: String,
        username: String,
        password: String,
        accountType: String
    ): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("name", name)
            put("contactNumber", contact)
            put("address", address)
            put("username", username)
            put("password", password)
            put("accountType", accountType)
        }
        return db.insert(TABLE_USERS, null, cv)
    }

    fun getUserByUsername(username: String): User? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_USERS WHERE username = ?",
            arrayOf(username)
        )

        var user: User? = null
        if (cursor.moveToFirst()) {
            user = User(
                name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                contactNumber = cursor.getString(cursor.getColumnIndexOrThrow("contactNumber")),
                address = cursor.getString(cursor.getColumnIndexOrThrow("address")),
                username = cursor.getString(cursor.getColumnIndexOrThrow("username")),
                password = cursor.getString(cursor.getColumnIndexOrThrow("password")),
                accountType = cursor.getString(cursor.getColumnIndexOrThrow("accountType"))
            )
        }
        cursor.close()
        return user
    }

    fun updateUserPersonal(username: String, name: String, contact: String, address: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("name", name)
            put("contactNumber", contact)
            put("address", address)
        }
        db.update(TABLE_USERS, values, "username = ?", arrayOf(username))
    }

    fun deleteUser(username: String) {
        val db = writableDatabase
        db.delete(TABLE_USERS, "username = ?", arrayOf(username))
        db.close()
    }

    fun getAllUsers(): MutableList<User> {
        val users = mutableListOf<User>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_USERS", null)
        if (cursor.moveToFirst()) {
            do {
                users.add(User(
                    name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                    contactNumber = cursor.getString(cursor.getColumnIndexOrThrow("contactNumber")),
                    address = cursor.getString(cursor.getColumnIndexOrThrow("address")),
                    username = cursor.getString(cursor.getColumnIndexOrThrow("username")),
                    password = cursor.getString(cursor.getColumnIndexOrThrow("password")),
                    accountType = cursor.getString(cursor.getColumnIndexOrThrow("accountType"))
                ))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return users
    }

    fun getAllStationOwners(): List<User> {
        val stations = mutableListOf<User>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_USERS WHERE accountType = ?",
            arrayOf("Station Owner")
        )

        if (cursor.moveToFirst()) {
            do {
                stations.add(
                    User(
                        name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                        contactNumber = cursor.getString(cursor.getColumnIndexOrThrow("contactNumber")),
                        address = cursor.getString(cursor.getColumnIndexOrThrow("address")),
                        username = cursor.getString(cursor.getColumnIndexOrThrow("username")),
                        password = cursor.getString(cursor.getColumnIndexOrThrow("password")),
                        accountType = cursor.getString(cursor.getColumnIndexOrThrow("accountType"))
                    )
                )
            } while (cursor.moveToNext())
        }
        cursor.close()
        return stations
    }

    // ----------------------------------------------------------
    // PRODUCTS
    // ----------------------------------------------------------

    fun insertProduct(product: Product, ownerUsername: String) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("name", product.name)
            put("price", product.price)
            put("image", product.imageUri)
            put("ownerUsername", ownerUsername)
        }
        db.insert(TABLE_PRODUCTS, null, cv)
    }

    fun updateProduct(id: String, product: Product) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("name", product.name)
            put("price", product.price)
            put("image", product.imageUri)
        }
        db.update(TABLE_PRODUCTS, cv, "id = ?", arrayOf(id))
    }

    fun deleteProduct(id: String) {
        val db = writableDatabase
        db.delete(TABLE_PRODUCTS, "id = ?", arrayOf(id))
    }

    fun getProductsByStation(stationUsername: String): List<Product> {
        val products = mutableListOf<Product>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_PRODUCTS WHERE ownerUsername = ?",
            arrayOf(stationUsername)
        )
        cursor.use {
            if (it.moveToFirst()) {
                do {
                    val id = it.getInt(it.getColumnIndexOrThrow("id")).toString()
                    val name = it.getString(it.getColumnIndexOrThrow("name"))
                    val price = it.getDouble(it.getColumnIndexOrThrow("price"))
                    val imageUri = it.getString(it.getColumnIndexOrThrow("image"))
                    val owner = it.getString(it.getColumnIndexOrThrow("ownerUsername"))
                    products.add(Product(id, name, price, imageUri, owner))
                } while (it.moveToNext())
            }
        }
        return products
    }

    // ----------------------------------------------------------
    // ORDERS
    // ----------------------------------------------------------

    fun insertOrder(
        productName: String,
        customerName: String,
        stationOwnerUsername: String,
        quantity: Int,
        containerType: String = "",
        deliveryTimeSlot: String = ""
    ) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("productName", productName)
            put("customerName", customerName)
            put("stationOwnerUsername", stationOwnerUsername)
            put("quantity", quantity)
            put("containerType", containerType)
            put("deliveryTimeSlot", deliveryTimeSlot)
            put("status", "Pending")
        }
        db.insert(TABLE_ORDERS, null, cv)
    }

    fun getOrdersForStation(ownerUsername: String): List<Order> {
        val orders = mutableListOf<Order>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_ORDERS WHERE stationOwnerUsername = ?",
            arrayOf(ownerUsername)
        )
        if (cursor.moveToFirst()) {
            do {
                orders.add(
                    Order(
                        id = cursor.getInt(cursor.getColumnIndexOrThrow("id")).toString(),
                        productName = cursor.getString(cursor.getColumnIndexOrThrow("productName")),
                        customerName = cursor.getString(cursor.getColumnIndexOrThrow("customerName")),
                        stationOwnerUsername = cursor.getString(cursor.getColumnIndexOrThrow("stationOwnerUsername")),
                        quantity = cursor.getInt(cursor.getColumnIndexOrThrow("quantity")),
                        containerType = cursor.getString(cursor.getColumnIndexOrThrow("containerType")),
                        deliveryTimeSlot = cursor.getString(cursor.getColumnIndexOrThrow("deliveryTimeSlot")),
                        status = cursor.getString(cursor.getColumnIndexOrThrow("status"))
                    )
                )
            } while (cursor.moveToNext())
        }
        cursor.close()
        return orders
    }

    fun updateOrderStatus(orderId: String, status: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("status", status)
        }
        db.update(TABLE_ORDERS, values, "id = ?", arrayOf(orderId))
    }

    fun getOrdersForCustomer(customerName: String): List<Order> {
        val orders = mutableListOf<Order>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_ORDERS WHERE customerName = ?",
            arrayOf(customerName)
        )
        if (cursor.moveToFirst()) {
            do {
                orders.add(
                    Order(
                        id = cursor.getInt(cursor.getColumnIndexOrThrow("id")).toString(),
                        productName = cursor.getString(cursor.getColumnIndexOrThrow("productName")),
                        customerName = cursor.getString(cursor.getColumnIndexOrThrow("customerName")),
                        stationOwnerUsername = cursor.getString(cursor.getColumnIndexOrThrow("stationOwnerUsername")),
                        quantity = cursor.getInt(cursor.getColumnIndexOrThrow("quantity")),
                        containerType = cursor.getString(cursor.getColumnIndexOrThrow("containerType")),
                        deliveryTimeSlot = cursor.getString(cursor.getColumnIndexOrThrow("deliveryTimeSlot")),
                        status = cursor.getString(cursor.getColumnIndexOrThrow("status"))
                    )
                )
            } while (cursor.moveToNext())
        }
        cursor.close()
        return orders
    }

    fun getPendingOrdersByCustomer(customerName: String): List<Order> {
        val orders = mutableListOf<Order>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_ORDERS WHERE customerName = ? AND status = ?",
            arrayOf(customerName, "Pending")
        )
        if (cursor.moveToFirst()) {
            do {
                orders.add(
                    Order(
                        id = cursor.getInt(cursor.getColumnIndexOrThrow("id")).toString(),
                        productName = cursor.getString(cursor.getColumnIndexOrThrow("productName")),
                        customerName = cursor.getString(cursor.getColumnIndexOrThrow("customerName")),
                        stationOwnerUsername = cursor.getString(cursor.getColumnIndexOrThrow("stationOwnerUsername")),
                        quantity = cursor.getInt(cursor.getColumnIndexOrThrow("quantity")),
                        containerType = cursor.getString(cursor.getColumnIndexOrThrow("containerType")),
                        deliveryTimeSlot = cursor.getString(cursor.getColumnIndexOrThrow("deliveryTimeSlot")),
                        status = cursor.getString(cursor.getColumnIndexOrThrow("status"))
                    )
                )
            } while (cursor.moveToNext())
        }
        cursor.close()
        return orders
    }
}
