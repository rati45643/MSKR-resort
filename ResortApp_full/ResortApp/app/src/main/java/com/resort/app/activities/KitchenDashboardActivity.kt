package com.resort.app.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.resort.app.adapters.KitchenOrdersAdapter
import com.resort.app.databinding.ActivityKitchenDashboardBinding
import com.resort.app.models.FoodOrder
import com.resort.app.utils.FirebaseHelper

class KitchenDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKitchenDashboardBinding
    private lateinit var adapter: KitchenOrdersAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKitchenDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        loadOrders()

        binding.btnLogout.setOnClickListener { logout() }

        // Revenue button click
        binding.btnRevenue.setOnClickListener {
            calculateRevenue()
        }

        // Filter tabs
        binding.tabAll.setOnClickListener              { loadOrders("all") }
        binding.tabPlaced.setOnClickListener           { loadOrders("placed") }
        binding.tabPreparing.setOnClickListener        { loadOrders("preparing") }
        binding.tabDelivered.setOnClickListener        { loadOrders("delivered") }
        binding.tabPaymentCompleted.setOnClickListener { loadOrders("payment_completed") }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Kitchen Dashboard"

        val uid = FirebaseHelper.currentUid ?: return
        FirebaseHelper.getUser(uid) { user ->
            binding.tvStaffName.text = "Welcome, ${user?.fullName ?: "Staff"}"
        }
    }

    private fun setupRecyclerView() {
        adapter = KitchenOrdersAdapter(emptyList()) { order, newStatus ->
            updateOrderStatus(order, newStatus)
        }

        binding.rvOrders.layoutManager = LinearLayoutManager(this)
        binding.rvOrders.adapter = adapter
    }

    private var currentFilter = "all"

    private fun loadOrders(filter: String = "all") {
        currentFilter = filter
        binding.progressBar.visibility = View.VISIBLE

        FirebaseHelper.getAllFoodOrders { orders ->
            binding.progressBar.visibility = View.GONE

            val filtered = if (filter == "all") {
                orders
            } else {
                orders.filter { it.status == filter }
            }

            adapter.updateOrders(filtered)

            // Update counts
            binding.tvTotalOrders.text =
                "Total: ${orders.size}"

            binding.tvPlacedCount.text =
                "Placed: ${orders.count { it.status == "placed" }}"

            binding.tvPreparingCount.text =
                "Preparing: ${orders.count { it.status == "preparing" }}"

            binding.tvDeliveredCount.text =
                "Delivered: ${orders.count { it.status == "delivered" }}"

            binding.tvPaymentCompletedCount.text =
                "Payment Completed: ${orders.count { it.status == "payment_completed" }}"

            // Empty state
            if (filtered.isEmpty()) {
                binding.tvEmpty.visibility = View.VISIBLE
                binding.rvOrders.visibility = View.GONE
            } else {
                binding.tvEmpty.visibility = View.GONE
                binding.rvOrders.visibility = View.VISIBLE
            }
        }
    }

    private fun updateOrderStatus(order: FoodOrder, newStatus: String) {
        FirebaseHelper.db.collection(FirebaseHelper.FOOD_ORDERS_COL)
            .document(order.orderId)
            .update("status", newStatus)
            .addOnSuccessListener {
                Toast.makeText(
                    this,
                    "Order updated to $newStatus",
                    Toast.LENGTH_SHORT
                ).show()

                loadOrders(currentFilter)
            }
            .addOnFailureListener {
                Toast.makeText(
                    this,
                    "Update failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    // Revenue only from payment completed orders
    private fun calculateRevenue() {
        FirebaseHelper.getAllFoodOrders { orders ->

            val paymentCompletedOrders = orders.filter {
                it.status == "payment_completed"
            }

            var totalRevenue = 0.0
            paymentCompletedOrders.forEach { order ->
                totalRevenue += order.totalCost
            }

            AlertDialog.Builder(this)
                .setTitle("Revenue Report")
                .setMessage(
                    "Payment Completed Orders: ${paymentCompletedOrders.size}\n\n" +
                            "Total Revenue: ₹%.2f".format(totalRevenue)
                )
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun logout() {
        FirebaseHelper.auth.signOut()

        val intent = Intent(this, LoginActivity::class.java)
        intent.flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK

        startActivity(intent)
    }
}