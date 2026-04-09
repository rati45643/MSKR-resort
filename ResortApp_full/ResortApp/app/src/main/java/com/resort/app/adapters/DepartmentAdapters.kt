package com.resort.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.resort.app.R
import com.resort.app.databinding.ItemBookingBinding
import com.resort.app.databinding.ItemOrderBinding
import com.resort.app.models.FoodOrder
import com.resort.app.models.RoomBooking
import java.text.SimpleDateFormat
import java.util.*

// ── Kitchen Orders Adapter ──────────────────────────────────────────────────
class KitchenOrdersAdapter(
    private var orders: List<FoodOrder>,
    private val onStatusChange: (FoodOrder, String) -> Unit
) : RecyclerView.Adapter<KitchenOrdersAdapter.OrderViewHolder>() {

    inner class OrderViewHolder(private val binding: ItemOrderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(order: FoodOrder) {
            binding.tvOrderId.text = "Order #${order.orderId.takeLast(8)}"
            binding.tvGuestName.text = order.userName
            binding.tvGuestEmail.text = order.userEmail

            val itemsSummary =
                order.items.joinToString("\n") {
                    "• ${it.itemName} x${it.quantity}"
                }

            binding.tvItems.text = itemsSummary
            binding.tvTotal.text =
                "₹${String.format("%,.2f", order.totalCost)}"

            val sdf = SimpleDateFormat(
                "dd MMM, hh:mm a",
                Locale.getDefault()
            )
            binding.tvTime.text =
                sdf.format(Date(order.createdAt))

            // Status display
            val statusText = when (order.status) {
                "placed" -> "🟡 Placed"
                "preparing" -> "🔵 Preparing"
                "delivered" -> "🟢 Delivered"
                "payment_completed" -> "💰 Payment Completed"
                else -> order.status
            }

            binding.tvStatus.text = statusText

            // Status action buttons
            when (order.status) {

                "placed" -> {
                    binding.btnAction.text = "Mark Preparing"
                    binding.btnAction.visibility =
                        android.view.View.VISIBLE
                    binding.btnAction.setOnClickListener {
                        onStatusChange(order, "preparing")
                    }
                }

                "preparing" -> {
                    binding.btnAction.text = "Mark Delivered"
                    binding.btnAction.visibility =
                        android.view.View.VISIBLE
                    binding.btnAction.setOnClickListener {
                        onStatusChange(order, "delivered")
                    }
                }

                "delivered" -> {
                    binding.btnAction.text = "Payment Completed"
                    binding.btnAction.visibility =
                        android.view.View.VISIBLE
                    binding.btnAction.setOnClickListener {
                        onStatusChange(order, "payment_completed")
                    }
                }

                else -> {
                    binding.btnAction.visibility =
                        android.view.View.GONE
                }
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): OrderViewHolder {
        val binding = ItemOrderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return OrderViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: OrderViewHolder,
        position: Int
    ) {
        holder.bind(orders[position])
    }

    override fun getItemCount(): Int = orders.size

    fun updateOrders(newOrders: List<FoodOrder>) {
        orders = newOrders
        notifyDataSetChanged()
    }
}

// ── Room Bookings Adapter ────────────────────────────────────────────────────
class RoomBookingsAdapter(
    private var bookings: List<RoomBooking>,
    private val onStatusChange: (RoomBooking, String) -> Unit
) : RecyclerView.Adapter<RoomBookingsAdapter.BookingViewHolder>() {

    inner class BookingViewHolder(
        private val binding: ItemBookingBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(booking: RoomBooking) {

            val bookingIdShort =
                if (booking.bookingId.length >= 8)
                    booking.bookingId.takeLast(8)
                else booking.bookingId

            binding.tvBookingId.text =
                "Booking #$bookingIdShort"

            binding.tvGuestName.text =
                booking.userName.ifEmpty { "Guest" }

            binding.tvRooms.text =
                "${booking.numberOfRooms} Room(s) × ${booking.numberOfDays} Day(s)"

            val sdf = SimpleDateFormat(
                "dd MMM yyyy",
                Locale.getDefault()
            )

            val checkIn =
                if (booking.checkInDate != 0L)
                    sdf.format(Date(booking.checkInDate))
                else "N/A"

            val checkOut =
                if (booking.checkOutDate != 0L)
                    sdf.format(Date(booking.checkOutDate))
                else "N/A"

            binding.tvDates.text =
                "$checkIn → $checkOut"

            val budget = booking.budgetRange
                .lowercase()
                .replaceFirstChar { it.uppercase() }

            binding.tvBudget.text =
                "Range: $budget"

            binding.tvTotal.text =
                "₹${String.format("%,.2f", booking.totalCost)}"

            binding.tvDiscount.visibility =
                android.view.View.GONE

            val status = booking.status.lowercase()

            val statusText = when (status) {
                "confirmed" -> "🟢 Confirmed"
                "checked_in" -> "🔵 Checked In"
                "checked_out" -> "⚫ Checked Out"
                "cancelled" -> "🔴 Cancelled"
                "pending" -> "🟡 Pending"
                else -> status.replaceFirstChar {
                    it.uppercase()
                }
            }

            binding.tvStatus.text = statusText

            when (status) {

                "confirmed" -> {
                    binding.btnAction.text = "Check In"
                    binding.btnAction.visibility =
                        android.view.View.VISIBLE
                    binding.btnAction.setOnClickListener {
                        onStatusChange(
                            booking,
                            "checked_in"
                        )
                    }
                }

                "checked_in" -> {
                    binding.btnAction.text = "Check Out"
                    binding.btnAction.visibility =
                        android.view.View.VISIBLE
                    binding.btnAction.setOnClickListener {
                        onStatusChange(
                            booking,
                            "checked_out"
                        )
                    }
                }

                else -> {
                    binding.btnAction.visibility =
                        android.view.View.GONE
                }
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): BookingViewHolder {
        val binding = ItemBookingBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BookingViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: BookingViewHolder,
        position: Int
    ) {
        holder.bind(bookings[position])
    }

    override fun getItemCount(): Int = bookings.size

    fun updateBookings(newBookings: List<RoomBooking>) {
        bookings = newBookings.toList()
        notifyDataSetChanged()
    }
}