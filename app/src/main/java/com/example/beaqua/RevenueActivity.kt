package com.example.beaqua

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.*

class RevenueActivity : AppCompatActivity() {

    private lateinit var currentUsername: String
    private lateinit var tvLifetime: TextView
    private lateinit var tvToday: TextView
    private lateinit var tvThisMonth: TextView
    private lateinit var tvSelectedMonthYear: TextView
    private lateinit var tvSelectedRevenue: TextView
    private lateinit var tvSelectedOrderCount: TextView
    private lateinit var spinnerMonth: Spinner
    private lateinit var spinnerYear: Spinner
    private lateinit var btnBack: ImageButton

    private val allOrders = mutableListOf<Order>()
    private var weekly = true
    private val selectedWeekDate = Calendar.getInstance()
    private val months = arrayOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    private val years = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_revenue)

        currentUsername = intent.getStringExtra("USERNAME") ?: ""

        tvLifetime = findViewById(R.id.tvLifetimeRevenue)
        tvToday = findViewById(R.id.tvTodayRevenue)
        tvThisMonth = findViewById(R.id.tvCurrentMonthRevenue)
        tvSelectedMonthYear = findViewById(R.id.tvSelectedMonthYear)
        tvSelectedRevenue = findViewById(R.id.tvSelectedRevenue)
        tvSelectedOrderCount = findViewById(R.id.tvSelectedOrderCount)
        spinnerMonth = findViewById(R.id.spinnerMonth)
        spinnerYear = findViewById(R.id.spinnerYear)
        btnBack = findViewById(R.id.btnBackRevenue)

        btnBack.setOnClickListener { returnToDashboard() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                returnToDashboard()
            }
        })
        
        setupSpinners()
        weekly = savedInstanceState?.getBoolean("revenueWeekly", true) ?: true
        selectedWeekDate.timeInMillis = savedInstanceState?.getLong("revenueWeekDate")
            ?: System.currentTimeMillis()
        val periodOptions = findViewById<android.widget.RadioGroup>(R.id.revenuePeriodOptions)
        periodOptions.check(if (weekly) R.id.revenueWeekOption else R.id.revenueMonthOption)
        periodOptions.setOnCheckedChangeListener { _, checked ->
            weekly = checked == R.id.revenueWeekOption
            calculateFilteredRevenue()
        }
        findViewById<MaterialButton>(R.id.btnRevenueWeekDate).setOnClickListener {
            android.app.DatePickerDialog(this, { _, year, month, day ->
                selectedWeekDate.set(year, month, day)
                calculateFilteredRevenue()
            }, selectedWeekDate.get(Calendar.YEAR), selectedWeekDate.get(Calendar.MONTH),
                selectedWeekDate.get(Calendar.DAY_OF_MONTH)).show()
        }
        calculateFilteredRevenue()
        loadOrders()
    }

    private fun returnToDashboard() {
        if (currentUsername.isEmpty()) {
            finish()
            return
        }

        val targetActivity = if (currentUsername.equals("admin", ignoreCase = true)) {
            AdminActivity::class.java
        } else {
            StationOwnerActivity::class.java
        }

        val intent = Intent(this, targetActivity).apply {
            putExtra("USERNAME", currentUsername)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }

    private fun setupSpinners() {
        val monthAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, months)
        monthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerMonth.adapter = monthAdapter

        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        for (i in 0..5) {
            years.add((currentYear - i).toString())
        }
        val yearAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, years)
        yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerYear.adapter = yearAdapter

        val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
        spinnerMonth.setSelection(currentMonth)

        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                calculateFilteredRevenue()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spinnerMonth.onItemSelectedListener = listener
        spinnerYear.onItemSelectedListener = listener
    }

    private fun loadOrders() {
        val query = if (currentUsername.lowercase() == "admin") {
            FirebaseHelper.ordersCollection.whereEqualTo("status", "Delivered")
        } else {
            FirebaseHelper.ordersCollection
                .whereEqualTo("stationOwnerUsername", currentUsername)
                .whereEqualTo("status", "Delivered")
        }

        query.get().addOnSuccessListener { result ->
            allOrders.clear()
            for (doc in result) {
                val order = doc.toObject(Order::class.java)
                allOrders.add(order)
            }
            calculateStats()
            calculateFilteredRevenue()
        }
    }

    private fun calculateStats() {
        var lifetime = 0.0
        var today = 0.0
        var thisMonth = 0.0

        val now = Calendar.getInstance()
        val todayDay = now.get(Calendar.DAY_OF_YEAR)
        val todayYear = now.get(Calendar.YEAR)
        val currentMonth = now.get(Calendar.MONTH)

        for (order in allOrders) {
            lifetime += order.totalPrice

            val orderCal = Calendar.getInstance()
            orderCal.timeInMillis = order.timestamp

            if (orderCal.get(Calendar.DAY_OF_YEAR) == todayDay && orderCal.get(Calendar.YEAR) == todayYear) {
                today += order.totalPrice
            }

            if (orderCal.get(Calendar.MONTH) == currentMonth && orderCal.get(Calendar.YEAR) == todayYear) {
                thisMonth += order.totalPrice
            }
        }

        tvLifetime.text = String.format(Locale.getDefault(), "₱%.2f", lifetime)
        tvToday.text = String.format(Locale.getDefault(), "₱%.2f", today)
        tvThisMonth.text = String.format(Locale.getDefault(), "₱%.2f", thisMonth)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("revenueWeekly", weekly)
        outState.putLong("revenueWeekDate", selectedWeekDate.timeInMillis)
        super.onSaveInstanceState(outState)
    }

    private fun calculateFilteredRevenue() {
        val selectedMonthIndex = spinnerMonth.selectedItemPosition
        val selectedYear = spinnerYear.selectedItem?.toString()?.toIntOrNull() ?: return
        if (selectedMonthIndex !in months.indices) return
        findViewById<View>(R.id.revenueMonthFilters).visibility = if (weekly) View.GONE else View.VISIBLE
        val weekButton = findViewById<MaterialButton>(R.id.btnRevenueWeekDate)
        weekButton.visibility = if (weekly) View.VISIBLE else View.GONE
        val start = (selectedWeekDate.clone() as Calendar).apply {
            if (weekly) {
                add(Calendar.DAY_OF_MONTH, -((get(Calendar.DAY_OF_WEEK) + 5) % 7))
            } else {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.YEAR, selectedYear)
                set(Calendar.MONTH, selectedMonthIndex)
            }
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = (start.clone() as Calendar).apply {
            if (weekly) add(Calendar.DAY_OF_MONTH, 7) else add(Calendar.MONTH, 1)
        }

        var filteredRevenue = 0.0
        var orderCount = 0

        for (order in allOrders) {
            if (order.timestamp >= start.timeInMillis && order.timestamp < end.timeInMillis) {
                filteredRevenue += order.totalPrice
                orderCount++
            }
        }

        val format = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        val lastDay = (end.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -1) }
        tvSelectedMonthYear.text = if (weekly) {
            "Weekly sales: ${format.format(start.time)} – ${format.format(lastDay.time)}"
        } else "Monthly sales: ${months[selectedMonthIndex]} $selectedYear"
        weekButton.text = "Week of ${format.format(start.time)} — change date"
        tvSelectedRevenue.text = String.format(Locale.getDefault(), "₱%.2f", filteredRevenue)
        tvSelectedOrderCount.text = "$orderCount Successful Orders"
    }

}
