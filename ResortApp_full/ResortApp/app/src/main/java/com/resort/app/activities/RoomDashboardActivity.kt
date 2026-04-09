package com.resort.app.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.resort.app.adapters.RoomBookingsAdapter
import com.resort.app.databinding.ActivityRoomDashboardBinding
import com.resort.app.models.RoomBooking
import com.resort.app.utils.FirebaseHelper

class RoomDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRoomDashboardBinding
    private lateinit var adapter: RoomBookingsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ SAFE VIEWBINDING INIT
        binding = ActivityRoomDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        loadBookings()

        binding.btnLogout.setOnClickListener { logout() }

        binding.btnFilterAll.setOnClickListener { loadBookings("all") }
        binding.btnFilterConfirmed.setOnClickListener { loadBookings("confirmed") }
        binding.btnFilterCheckedIn.setOnClickListener { loadBookings("checked_in") }
        binding.btnFilterCheckedOut.setOnClickListener { loadBookings("checked_out") }
    }

    // ✅ TOOLBAR SETUP (SAFE)
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Room Department Dashboard"

        val uid = FirebaseHelper.currentUid

        if (uid != null) {
            FirebaseHelper.getUser(uid) { user ->
                binding.tvStaffName.text =
                    "Welcome, ${user?.fullName ?: "Room Staff"}"
            }
        } else {
            binding.tvStaffName.text = "Welcome, Room Staff"
        }
    }

    // ✅ RECYCLERVIEW SETUP
    private fun setupRecyclerView() {
        adapter = RoomBookingsAdapter(mutableListOf()) { booking, newStatus ->
            handleStatusUpdate(booking, newStatus)
        }

        binding.rvBookings.layoutManager = LinearLayoutManager(this)
        binding.rvBookings.adapter = adapter
    }

    // ✅ LOAD BOOKINGS (FULL SAFE)
    private fun loadBookings(filter: String = "all") {

        binding.progressBar.visibility = View.VISIBLE

        FirebaseHelper.db.collection("bookings")
            .addSnapshotListener { snapshot, _ ->

                binding.progressBar.visibility = View.GONE

                if (snapshot == null) {
                    Toast.makeText(this, "Failed to load bookings", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                val bookings = snapshot.toObjects(RoomBooking::class.java)

                val filtered = if (filter == "all") bookings
                else bookings.filter { it.status.equals(filter, true) }

                adapter.updateBookings(filtered)

                // ✅ ONLY COUNT REVENUE AFTER CHECK-OUT
                val totalRevenue = bookings
                    .filter { it.status.equals("checked_out", true) }
                    .sumOf { it.totalCost }

                binding.tvTotalBookings.text = "Total: ${bookings.size}"
                binding.tvConfirmedCount.text =
                    "Confirmed: ${bookings.count { it.status.equals("confirmed", true) }}"
                binding.tvCheckedInCount.text =
                    "Checked In: ${bookings.count { it.status.equals("checked_in", true) }}"
                binding.tvCheckedOutCount.text =
                    "Checked Out: ${bookings.count { it.status.equals("checked_out", true) }}"
                binding.tvTotalRevenue.text =
                    "Revenue: ₹${String.format("%,.0f", totalRevenue)}"

                if (filtered.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.rvBookings.visibility = View.GONE
                } else {
                    binding.tvEmpty.visibility = View.GONE
                    binding.rvBookings.visibility = View.VISIBLE
                }
            }
    }

    // ✅ STATUS CONTROL
    private fun handleStatusUpdate(booking: RoomBooking, newStatus: String) {

        val currentStatus = booking.status

        // 🚫 SAME STATUS
        if (currentStatus.equals(newStatus, true)) {
            Toast.makeText(this, "Already $newStatus", Toast.LENGTH_SHORT).show()
            return
        }

        when {
            currentStatus.equals("cancelled", true) -> {
                Toast.makeText(this, "Booking already cancelled", Toast.LENGTH_SHORT).show()
                return
            }

            currentStatus.equals("checked_out", true) -> {
                Toast.makeText(this, "Already checked out", Toast.LENGTH_SHORT).show()
                return
            }

            currentStatus.equals("checked_in", true) &&
                    newStatus.equals("confirmed", true) -> {
                Toast.makeText(this, "Invalid status change", Toast.LENGTH_SHORT).show()
                return
            }
        }

        updateBookingStatus(booking, newStatus)
    }

    // ✅ UPDATE STATUS (SAFE)
    private fun updateBookingStatus(booking: RoomBooking, newStatus: String) {

        val bookingId = booking.bookingId

        if (bookingId.isEmpty()) {
            Toast.makeText(this, "Invalid booking ID", Toast.LENGTH_SHORT).show()
            return
        }

        FirebaseHelper.updateBookingStatus(bookingId, newStatus) { success ->

            if (success) {
                Toast.makeText(this, "Updated to $newStatus", Toast.LENGTH_SHORT).show()
                loadBookings()

                if (newStatus.equals("checked_in", true)) {
                    Toast.makeText(
                        this,
                        "User can no longer cancel this booking",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } else {
                Toast.makeText(this, "Update failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ✅ LOGOUT
    private fun logout() {
        FirebaseHelper.auth.signOut()

        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        startActivity(intent)
        finish()
    }
}