package com.resort.app.models

// ─── User Model ──────────────────────────────────────────────────────────────
data class User(
    val uid: String = "",
    val fullName: String = "",
    val email: String = "",
    val role: String = "user",          // "user" | "kitchen" | "room"
    val photoUrl: String = "",
    val phone: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

// ─── Room Booking Model ───────────────────────────────────────────────────────
data class RoomBooking(
    val bookingId: String = "",
    val userId: String = "",
    val userName: String = "",
    val roomNumber: Int = 0,
    val numberOfRooms: Int = 1,
    val numberOfDays: Int = 1,
    val budgetRange: String = "",
    val totalCost: Double = 0.0,
    val checkInDate: Long = 0,
    val checkOutDate: Long = 0,
    val status: String = "pending",
    val createdAt: Long = System.currentTimeMillis()
) {

    companion object {

        // ✅ SAFE BASE PRICE (case-insensitive)
        fun getBasePrice(range: String): Double {
            return when (range.lowercase()) {
                "low" -> 3000.0
                "medium" -> 6000.0
                "high" -> 12000.0
                else -> 6000.0
            }
        }

        // ✅ COST CALCULATION WITH VALIDATION
        fun calculateCost(rooms: Int, days: Int, range: String): Double {

            if (rooms <= 0 || days <= 0) return 0.0

            val basePrice = getBasePrice(range)
            val subtotal = basePrice * rooms * days

            // ✅ Discount for 2+ rooms
            return if (rooms >= 2) subtotal * 0.90 else subtotal
        }
    }
}

// ─── Food Item Model ──────────────────────────────────────────────────────────
data class FoodItem(
    val itemId: String = "",
    val name: String = "",
    val category: String = "",   // veg_main | non_veg_main | chat | soup | ice_cream | juice | milkshake | cakes
    val isVeg: Boolean = true,
    val pricePerPiece: Double = 0.0,
    val description: String = "",
    val imageUrl: String = "",      // ✅ NEW for food images
    val available: Boolean = true
)

// ─── Food Order Model ─────────────────────────────────────────────────────────
data class FoodOrder(
    val orderId: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val userName: String = "",
    val roomNumber: String = "",         // ✅ required while ordering
    val birthdayName: String = "",       // ✅ required for cakes
    val items: List<OrderItem> = emptyList(),
    val totalCost: Double = 0.0,
    val status: String = "placed",       // placed | preparing | delivered
    val createdAt: Long = System.currentTimeMillis()
)

data class OrderItem(
    val itemId: String = "",
    val itemName: String = "",
    val quantity: Int = 1,
    val unitType: String = "piece",      // ✅ piece / kg
    val pricePerPiece: Double = 0.0,
    val subtotal: Double = 0.0
)

// ─── Issue Report Model ───────────────────────────────────────────────────────
data class IssueReport(
    val issueId: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val userName: String = "",
    val roomNumber: String = "",
    val issueType: String = "",   // water | electricity | cleaning | laundry | other
    val description: String = "",
    val status: String = "open",   // open | in_progress | resolved
    val createdAt: Long = System.currentTimeMillis()
)

// ─── Resort Info Model ────────────────────────────────────────────────────────
data class ResortInfo(
    val name: String = "Palm Grove Resort",
    val description: String = "",
    val rating: Float = 4.5f,
    val totalReviews: Int = 0,
    val achievements: List<String> = emptyList(),
    val amenities: List<String> = emptyList(),
    val coverImageUrl: String = "",
    val helplineNumber: String = "+919019542275"
)