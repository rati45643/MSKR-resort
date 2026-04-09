package com.resort.app.utils

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.resort.app.models.*
import java.text.SimpleDateFormat
import java.util.Locale

object FirebaseHelper {

    val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    val storage: FirebaseStorage
        get() = FirebaseStorage.getInstance()

    // Collections
    const val USERS_COL = "users"
    const val BOOKINGS_COL = "room_bookings"
    const val FOOD_ORDERS_COL = "food_orders"
    const val ISSUES_COL = "issues"
    const val RESORT_INFO_COL = "resort_info"
    const val FOOD_MENU_COL = "food_menu"


    // Current user
    val currentUid: String?
        get() = auth.currentUser?.uid

    val currentEmail: String?
        get() = auth.currentUser?.email

    // ───────────────── USER ─────────────────

    fun saveUser(
        user: User,
        onResult: (Boolean, String?) -> Unit
    ) {
        db.collection(USERS_COL)
            .document(user.uid)
            .set(user)
            .addOnSuccessListener {
                onResult(true, null)
            }
            .addOnFailureListener {
                onResult(false, it.message)
            }
    }

    fun getUser(
        uid: String,
        onResult: (User?) -> Unit
    ) {
        db.collection(USERS_COL)
            .document(uid)
            .get()
            .addOnSuccessListener { doc ->
                onResult(doc.toObject(User::class.java))
            }
            .addOnFailureListener {
                onResult(null)
            }
    }

    // ───────────────── ROOM BOOKING ─────────────────

    fun isRoomAvailable(
        roomNumber: Int,
        checkIn: Long,
        checkOut: Long,
        onResult: (Boolean) -> Unit
    ) {
        db.collection(BOOKINGS_COL)
            .whereEqualTo("roomNumber", roomNumber)
            .get()
            .addOnSuccessListener { snap ->

                val available = snap.documents.none { doc ->

                    val booking =
                        doc.toObject(RoomBooking::class.java)
                            ?: return@none false

                    val status = booking.status.lowercase()

                    val isActive =
                        status != "cancelled" &&
                                status != "checked_out"

                    val overlaps =
                        !(checkOut <= booking.checkInDate ||
                                checkIn >= booking.checkOutDate)

                    isActive && overlaps
                }

                onResult(available)
            }
            .addOnFailureListener {
                onResult(false)
            }
    }

    fun saveRoomBooking(
        booking: RoomBooking,
        onResult: (Boolean, String) -> Unit
    ) {

        val docRef = db.collection(BOOKINGS_COL).document()

        val finalBooking = booking.copy(
            bookingId = docRef.id,
            status = if (booking.status.isEmpty())
                "confirmed"
            else
                booking.status.lowercase(),
            createdAt = System.currentTimeMillis()
        )

        isRoomAvailable(
            finalBooking.roomNumber,
            finalBooking.checkInDate,
            finalBooking.checkOutDate
        ) { available ->

            if (!available) {
                onResult(
                    false,
                    "Room already booked for selected dates"
                )
                return@isRoomAvailable
            }

            docRef.set(finalBooking)
                .addOnSuccessListener {
                    onResult(true, docRef.id)
                }
                .addOnFailureListener {
                    onResult(false, it.message ?: "Error")
                }
        }
    }

    private fun parseDate(value: Any?): Long {
        return when (value) {

            is Long -> value

            is Int -> value.toLong()

            is String -> {
                value.toLongOrNull()
                    ?: try {
                        val sdf = SimpleDateFormat(
                            "dd MMM yyyy",
                            Locale.getDefault()
                        )
                        sdf.parse(value)?.time ?: 0L
                    } catch (e: Exception) {
                        0L
                    }
            }

            else -> 0L
        }
    }

    fun getAllBookings(
        onResult: (List<RoomBooking>?) -> Unit
    ) {

        db.collection(BOOKINGS_COL)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->

                if (error != null || snap == null) {
                    onResult(null)
                    return@addSnapshotListener
                }

                val bookings = snap.documents.mapNotNull { doc ->

                    val data = doc.data ?: return@mapNotNull null

                    try {
                        RoomBooking(
                            bookingId = data["bookingId"] as? String ?: "",
                            userId = data["userId"] as? String ?: "",
                            userName = data["userName"] as? String ?: "",
                            roomNumber = (data["roomNumber"] as? Number)?.toInt() ?: 0,
                            numberOfRooms = (data["numberOfRooms"] as? Number)?.toInt() ?: 1,
                            numberOfDays = (data["numberOfDays"] as? Number)?.toInt() ?: 1,
                            budgetRange = data["budgetRange"] as? String ?: "",
                            totalCost = (data["totalCost"] as? Number)?.toDouble() ?: 0.0,
                            checkInDate = parseDate(data["checkInDate"]),
                            checkOutDate = parseDate(data["checkOutDate"]),
                            status = data["status"] as? String ?: "pending",
                            createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                onResult(bookings)
            }
    }

    fun updateBookingStatus(
        id: String,
        status: String,
        callback: (Boolean) -> Unit
    ) {

        if (id.isEmpty()) {
            callback(false)
            return
        }

        db.collection(BOOKINGS_COL)
            .document(id)
            .update("status", status)
            .addOnSuccessListener {
                callback(true)
            }
            .addOnFailureListener {
                callback(false)
            }
    }

    // ───────────────── HUGE KITCHEN MENU ─────────────────

    fun seedHugeKitchenMenu(
        onComplete: (Boolean) -> Unit
    ) {

        val items = mutableListOf<FoodItem>()


        // ================= VEG Indian ITEMS =================

        items.addAll(listOf(
            FoodItem(itemId = "1", name = "Paneer Butter Masala", category = "Indian", description = "Rich paneer curry in buttery tomato gravy", isVeg = true, pricePerPiece = 220.0),
            FoodItem(itemId = "2", name = "Shahi Paneer", category = "Indian", description = "Creamy royal paneer curry", isVeg = true, pricePerPiece = 230.0),
            FoodItem(itemId = "3", name = "Palak Paneer", category = "Indian", description = "Paneer cooked in spinach gravy", isVeg = true, pricePerPiece = 210.0),
            FoodItem(itemId = "4", name = "Kadai Paneer", category = "Indian", description = "Spicy paneer curry", isVeg = true, pricePerPiece = 215.0),
            FoodItem(itemId = "5", name = "Matar Paneer", category = "Indian", description = "Paneer with green peas curry", isVeg = true, pricePerPiece = 200.0),
            FoodItem(itemId = "6", name = "Mix Veg Curry", category = "Indian", description = "Fresh vegetables in masala gravy", isVeg = true, pricePerPiece = 180.0),
            FoodItem(itemId = "7", name = "Aloo Gobi", category = "Indian", description = "Potato and cauliflower curry", isVeg = true, pricePerPiece = 170.0),
            FoodItem(itemId = "8", name = "Bhindi Masala", category = "Indian", description = "Okra cooked with spices", isVeg = true, pricePerPiece = 175.0),
            FoodItem(itemId = "9", name = "Dal Tadka", category = "Indian", description = "Yellow lentils tempered with spices", isVeg = true, pricePerPiece = 150.0),
            FoodItem(itemId = "10", name = "Dal Makhani", category = "Indian", description = "Creamy black lentils", isVeg = true, pricePerPiece = 190.0),
            FoodItem(itemId = "11", name = "Chole Masala", category = "Indian", description = "Chickpeas curry", isVeg = true, pricePerPiece = 160.0),
            FoodItem(itemId = "12", name = "Rajma Curry", category = "Indian", description = "Kidney beans masala", isVeg = true, pricePerPiece = 165.0),
            FoodItem(itemId = "13", name = "Veg Kolhapuri", category = "Indian", description = "Spicy mixed veg curry", isVeg = true, pricePerPiece = 185.0),
            FoodItem(itemId = "14", name = "Malai Kofta", category = "Indian", description = "Veg dumplings in creamy gravy", isVeg = true, pricePerPiece = 210.0),
            FoodItem(itemId = "15", name = "Paneer Bhurji", category = "Indian", description = "Scrambled paneer masala", isVeg = true, pricePerPiece = 205.0),
            FoodItem(itemId = "16", name = "Jeera Rice", category = "Indian", description = "Cumin flavored rice", isVeg = true, pricePerPiece = 120.0),
            FoodItem(itemId = "17", name = "Veg Pulao", category = "Indian", description = "Rice with vegetables", isVeg = true, pricePerPiece = 150.0),
            FoodItem(itemId = "18", name = "Paneer Biryani", category = "Indian", description = "Spiced paneer rice", isVeg = true, pricePerPiece = 240.0),
            FoodItem(itemId = "19", name = "Veg Biryani", category = "Indian", description = "Vegetable dum biryani", isVeg = true, pricePerPiece = 220.0),
            FoodItem(itemId = "20", name = "Curd Rice", category = "Indian", description = "Rice mixed with yogurt", isVeg = true, pricePerPiece = 110.0),
            FoodItem(itemId = "21", name = "Lemon Rice", category = "Indian", description = "Tangy flavored rice", isVeg = true, pricePerPiece = 115.0),
            FoodItem(itemId = "22", name = "Tomato Rice", category = "Indian", description = "Rice cooked with tomatoes", isVeg = true, pricePerPiece = 120.0),
            FoodItem(itemId = "23", name = "Fried Rice Veg", category = "Indian", description = "Indian style veg fried rice", isVeg = true, pricePerPiece = 160.0),
            FoodItem(itemId = "24", name = "Masala Dosa", category = "Indian", description = "Crispy dosa with potato filling", isVeg = true, pricePerPiece = 90.0),
            FoodItem(itemId = "25", name = "Plain Dosa", category = "Indian", description = "Classic crispy dosa", isVeg = true, pricePerPiece = 70.0),
            FoodItem(itemId = "26", name = "Rava Dosa", category = "Indian", description = "Semolina crispy dosa", isVeg = true, pricePerPiece = 95.0),
            FoodItem(itemId = "27", name = "Idli Sambar", category = "Indian", description = "Soft idli with sambar", isVeg = true, pricePerPiece = 60.0),
            FoodItem(itemId = "28", name = "Vada Sambar", category = "Indian", description = "Medu vada with sambar", isVeg = true, pricePerPiece = 70.0),
            FoodItem(itemId = "29", name = "Pongal", category = "Indian", description = "Rice lentil breakfast dish", isVeg = true, pricePerPiece = 85.0),
            FoodItem(itemId = "30", name = "Upma", category = "Indian", description = "Savory semolina breakfast", isVeg = true, pricePerPiece = 65.0)

        ))
        // =============== PIZZA / MAGGI/PASTA ==================//
        items.addAll(listOf(
            FoodItem(itemId = "31", name = "Margherita Pizza", category = "Pizza", isVeg = true, pricePerPiece = 250.0, description = "Classic cheese pizza"),
            FoodItem(itemId = "32", name = "Farmhouse Pizza", category = "Pizza", isVeg = true, pricePerPiece = 320.0, description = "Veg loaded farmhouse pizza"),
            FoodItem(itemId = "33", name = "Paneer Tikka Pizza", category = "Pizza", isVeg = true, pricePerPiece = 340.0, description = "Paneer tikka topping pizza"),
            FoodItem(itemId = "34", name = "Cheese Burst Pizza", category = "Pizza", isVeg = true, pricePerPiece = 360.0, description = "Extra cheesy pizza"),
            FoodItem(itemId = "35", name = "Veggie Delight Pizza", category = "Pizza", isVeg = true, pricePerPiece = 300.0, description = "Loaded veggie pizza"),
            FoodItem(itemId = "36", name = "Corn Cheese Pizza", category = "Pizza", isVeg = true, pricePerPiece = 290.0, description = "Corn and cheese topping"),
            FoodItem(itemId = "37", name = "Mushroom Pizza", category = "Pizza", isVeg = true, pricePerPiece = 310.0, description = "Fresh mushroom pizza"),
            FoodItem(itemId = "38", name = "Capsicum Onion Pizza", category = "Pizza", isVeg = true, pricePerPiece = 280.0, description = "Veg toppings pizza"),
            FoodItem(itemId = "39", name = "Mexican Green Wave Pizza", category = "Pizza", isVeg = true, pricePerPiece = 350.0, description = "Spicy mexican veg pizza"),
            FoodItem(itemId = "40", name = "Tandoori Paneer Pizza", category = "Pizza", isVeg = true, pricePerPiece = 370.0, description = "Indian paneer style pizza"),
            FoodItem(itemId = "41", name = "Classic Masala Maggi", category = "Maggi", isVeg = true, pricePerPiece = 60.0, description = "Regular masala noodles"),
            FoodItem(itemId = "42", name = "Cheese Maggi", category = "Maggi", isVeg = true, pricePerPiece = 80.0, description = "Maggi with melted cheese"),
            FoodItem(itemId = "43", name = "Veg Maggi", category = "Maggi", isVeg = true, pricePerPiece = 75.0, description = "Maggi with vegetables"),
            FoodItem(itemId = "44", name = "Paneer Maggi", category = "Maggi", isVeg = true, pricePerPiece = 90.0, description = "Maggi with paneer cubes"),
            FoodItem(itemId = "45", name = "Butter Maggi", category = "Maggi", isVeg = true, pricePerPiece = 70.0, description = "Creamy butter maggi"),
            FoodItem(itemId = "46", name = "Peri Peri Maggi", category = "Maggi", isVeg = true, pricePerPiece = 85.0, description = "Spicy peri peri noodles"),
            FoodItem(itemId = "47", name = "Corn Maggi", category = "Maggi", isVeg = true, pricePerPiece = 78.0, description = "Sweet corn maggi"),
            FoodItem(itemId = "48", name = "Mushroom Maggi", category = "Maggi", isVeg = true, pricePerPiece = 88.0, description = "Maggi with mushrooms"),
            FoodItem(itemId = "49", name = "Tandoori Maggi", category = "Maggi", isVeg = true, pricePerPiece = 92.0, description = "Tandoori flavored maggi"),
            FoodItem(itemId = "50", name = "Loaded Veg Maggi", category = "Maggi", isVeg = true, pricePerPiece = 95.0, description = "Extra veggies maggi"),
            FoodItem(itemId = "51", name = "White Sauce Pasta", category = "Pasta", isVeg = true, pricePerPiece = 180.0, description = "Creamy white sauce pasta"),
            FoodItem(itemId = "52", name = "Red Sauce Pasta", category = "Pasta", isVeg = true, pricePerPiece = 170.0, description = "Tangy tomato pasta"),
            FoodItem(itemId = "53", name = "Pink Sauce Pasta", category = "Pasta", isVeg = true, pricePerPiece = 190.0, description = "Mix white and red sauce"),
            FoodItem(itemId = "54", name = "Cheese Pasta", category = "Pasta", isVeg = true, pricePerPiece = 200.0, description = "Extra cheesy pasta"),
            FoodItem(itemId = "55", name = "Veg Alfredo Pasta", category = "Pasta", isVeg = true, pricePerPiece = 210.0, description = "Creamy alfredo veg pasta"),
            FoodItem(itemId = "56", name = "Penne Arrabbiata", category = "Pasta", isVeg = true, pricePerPiece = 195.0, description = "Spicy red sauce penne"),
            FoodItem(itemId = "57", name = "Mac and Cheese", category = "Pasta", isVeg = true, pricePerPiece = 220.0, description = "Classic cheese pasta"),
            FoodItem(itemId = "58", name = "Mushroom Pasta", category = "Pasta", isVeg = true, pricePerPiece = 205.0, description = "Creamy mushroom pasta"),
            FoodItem(itemId = "59", name = "Corn Cheese Pasta", category = "Pasta", isVeg = true, pricePerPiece = 198.0, description = "Corn and cheese pasta"),
            FoodItem(itemId = "60", name = "Paneer Pasta", category = "Pasta", isVeg = true, pricePerPiece = 215.0, description = "Paneer loaded pasta")
        ))
        //============== Burgers/Sandwitch/More veg foods===//
        items.addAll(listOf(
            FoodItem(itemId = "61", name = "Veg Burger", category = "Burger", isVeg = true, pricePerPiece = 120.0, description = "Crispy veg patty burger"),
            FoodItem(itemId = "62", name = "Cheese Burger Veg", category = "Burger", isVeg = true, pricePerPiece = 140.0, description = "Veg burger with cheese"),
            FoodItem(itemId = "63", name = "Paneer Burger", category = "Burger", isVeg = true, pricePerPiece = 150.0, description = "Paneer patty burger"),
            FoodItem(itemId = "64", name = "Aloo Tikki Burger", category = "Burger", isVeg = true, pricePerPiece = 110.0, description = "Indian style burger"),
            FoodItem(itemId = "65", name = "Mushroom Burger", category = "Burger", isVeg = true, pricePerPiece = 145.0, description = "Burger with mushroom filling"),
            FoodItem(itemId = "66", name = "Veg Club Sandwich", category = "Sandwich", isVeg = true, pricePerPiece = 130.0, description = "Triple layer sandwich"),
            FoodItem(itemId = "67", name = "Cheese Sandwich", category = "Sandwich", isVeg = true, pricePerPiece = 115.0, description = "Grilled cheese sandwich"),
            FoodItem(itemId = "68", name = "Paneer Sandwich", category = "Sandwich", isVeg = true, pricePerPiece = 135.0, description = "Paneer stuffed sandwich"),
            FoodItem(itemId = "69", name = "Corn Mayo Sandwich", category = "Sandwich", isVeg = true, pricePerPiece = 125.0, description = "Corn mayo filling"),
            FoodItem(itemId = "70", name = "Veg Grilled Sandwich", category = "Sandwich", isVeg = true, pricePerPiece = 128.0, description = "Loaded grilled sandwich"),
            FoodItem(itemId = "71", name = "French Fries", category = "Snacks", isVeg = true, pricePerPiece = 90.0, description = "Crispy salted fries"),
            FoodItem(itemId = "72", name = "Peri Peri Fries", category = "Snacks", isVeg = true, pricePerPiece = 110.0, description = "Spicy fries"),
            FoodItem(itemId = "73", name = "Cheese Fries", category = "Snacks", isVeg = true, pricePerPiece = 130.0, description = "Loaded cheese fries"),
            FoodItem(itemId = "74", name = "Veg Nuggets", category = "Snacks", isVeg = true, pricePerPiece = 140.0, description = "Crispy vegetable nuggets"),
            FoodItem(itemId = "75", name = "Mozzarella Sticks", category = "Snacks", isVeg = true, pricePerPiece = 170.0, description = "Cheesy snack sticks"),
            FoodItem(itemId = "76", name = "Spring Rolls Veg", category = "Snacks", isVeg = true, pricePerPiece = 150.0, description = "Crispy veg rolls"),
            FoodItem(itemId = "77", name = "Garlic Bread", category = "Snacks", isVeg = true, pricePerPiece = 120.0, description = "Toasted garlic bread"),
            FoodItem(itemId = "78", name = "Cheesy Garlic Bread", category = "Snacks", isVeg = true, pricePerPiece = 150.0, description = "Garlic bread with cheese"),
            FoodItem(itemId = "79", name = "Paneer Wrap", category = "Wrap", isVeg = true, pricePerPiece = 160.0, description = "Paneer stuffed wrap"),
            FoodItem(itemId = "80", name = "Veg Wrap", category = "Wrap", isVeg = true, pricePerPiece = 145.0, description = "Loaded veggie wrap"),
            FoodItem(itemId = "81", name = "Hakka Noodles Veg", category = "Chinese", isVeg = true, pricePerPiece = 170.0, description = "Veg hakka noodles"),
            FoodItem(itemId = "82", name = "Schezwan Noodles", category = "Chinese", isVeg = true, pricePerPiece = 180.0, description = "Spicy schezwan noodles"),
            FoodItem(itemId = "83", name = "Veg Manchurian", category = "Chinese", isVeg = true, pricePerPiece = 190.0, description = "Veg balls in sauce"),
            FoodItem(itemId = "84", name = "Gobi Manchurian", category = "Chinese", isVeg = true, pricePerPiece = 175.0, description = "Cauliflower manchurian"),
            FoodItem(itemId = "85", name = "Paneer Chilli", category = "Chinese", isVeg = true, pricePerPiece = 210.0, description = "Spicy paneer starter"),
            FoodItem(itemId = "86", name = "Veg Fried Momos", category = "Momos", isVeg = true, pricePerPiece = 160.0, description = "Crispy fried dumplings"),
            FoodItem(itemId = "87", name = "Steamed Veg Momos", category = "Momos", isVeg = true, pricePerPiece = 150.0, description = "Soft steamed dumplings"),
            FoodItem(itemId = "88", name = "Cheese Momos", category = "Momos", isVeg = true, pricePerPiece = 175.0, description = "Cheesy stuffed momos"),
            FoodItem(itemId = "89", name = "Paneer Momos", category = "Momos", isVeg = true, pricePerPiece = 180.0, description = "Paneer filled momos"),
            FoodItem(itemId = "90", name = "Tandoori Momos", category = "Momos", isVeg = true, pricePerPiece = 190.0, description = "Roasted spicy momos"),
            FoodItem(itemId = "91", name = "Stuffed Paratha", category = "Indian", isVeg = true, pricePerPiece = 90.0, description = "Aloo stuffed paratha"),
            FoodItem(itemId = "92", name = "Paneer Paratha", category = "Indian", isVeg = true, pricePerPiece = 110.0, description = "Paneer stuffed flatbread"),
            FoodItem(itemId = "93", name = "Veg Noodles Soup", category = "Soup", isVeg = true, pricePerPiece = 140.0, description = "Healthy noodle soup"),
            FoodItem(itemId = "94", name = "Tomato Soup", category = "Soup", isVeg = true, pricePerPiece = 100.0, description = "Creamy tomato soup"),
            FoodItem(itemId = "95", name = "Sweet Corn Soup", category = "Soup", isVeg = true, pricePerPiece = 110.0, description = "Corn flavored soup"),
            FoodItem(itemId = "96", name = "Hot and Sour Soup", category = "Soup", isVeg = true, pricePerPiece = 115.0, description = "Spicy tangy soup"),
            FoodItem(itemId = "97", name = "Paneer Roll", category = "Roll", isVeg = true, pricePerPiece = 150.0, description = "Paneer stuffed roll"),
            FoodItem(itemId = "98", name = "Veg Kathi Roll", category = "Roll", isVeg = true, pricePerPiece = 145.0, description = "Vegetable wrap roll"),
            FoodItem(itemId = "99", name = "Cheese Roll", category = "Roll", isVeg = true, pricePerPiece = 155.0, description = "Cheesy snack roll"),
            FoodItem(itemId = "100", name = "Mushroom Roll", category = "Roll", isVeg = true, pricePerPiece = 160.0, description = "Stuffed mushroom roll")
        ))

        // ================ Non veg items===============//
        items.addAll(listOf(
            FoodItem(itemId = "101", name = "Butter Chicken", category = "Indian", isVeg = false, pricePerPiece = 280.0, description = "Creamy tomato chicken curry"),
            FoodItem(itemId = "102", name = "Chicken Tikka Masala", category = "Indian", isVeg = false, pricePerPiece = 290.0, description = "Grilled chicken in spicy gravy"),
            FoodItem(itemId = "103", name = "Chicken Korma", category = "Indian", isVeg = false, pricePerPiece = 275.0, description = "Mild creamy chicken curry"),
            FoodItem(itemId = "104", name = "Chicken Chettinad", category = "Indian", isVeg = false, pricePerPiece = 295.0, description = "Spicy South Indian chicken curry"),
            FoodItem(itemId = "105", name = "Chicken Curry", category = "Indian", isVeg = false, pricePerPiece = 260.0, description = "Traditional Indian chicken gravy"),
            FoodItem(itemId = "106", name = "Mutton Rogan Josh", category = "Indian", isVeg = false, pricePerPiece = 340.0, description = "Rich Kashmiri mutton curry"),
            FoodItem(itemId = "107", name = "Mutton Korma", category = "Indian", isVeg = false, pricePerPiece = 350.0, description = "Slow cooked creamy mutton curry"),
            FoodItem(itemId = "108", name = "Fish Curry", category = "Indian", isVeg = false, pricePerPiece = 300.0, description = "Spicy coastal style fish curry"),
            FoodItem(itemId = "109", name = "Prawn Masala", category = "Indian", isVeg = false, pricePerPiece = 330.0, description = "Prawns cooked in spicy masala"),
            FoodItem(itemId = "110", name = "Egg Curry", category = "Indian", isVeg = false, pricePerPiece = 180.0, description = "Boiled eggs in curry gravy"),
            FoodItem(itemId = "111", name = "Chicken Biryani", category = "Indian", isVeg = false, pricePerPiece = 320.0, description = "Spiced dum chicken biryani"),
            FoodItem(itemId = "112", name = "Mutton Biryani", category = "Indian", isVeg = false, pricePerPiece = 380.0, description = "Rich aromatic mutton biryani"),
            FoodItem(itemId = "113", name = "Fish Biryani", category = "Indian", isVeg = false, pricePerPiece = 340.0, description = "Flavorful fish dum biryani"),
            FoodItem(itemId = "114", name = "Prawn Biryani", category = "Indian", isVeg = false, pricePerPiece = 360.0, description = "Seafood biryani with prawns"),
            FoodItem(itemId = "115", name = "Egg Biryani", category = "Indian", isVeg = false, pricePerPiece = 220.0, description = "Rice cooked with boiled eggs"),
            FoodItem(itemId = "116", name = "Chicken Fried Rice", category = "Indian", isVeg = false, pricePerPiece = 230.0, description = "Indo-Chinese chicken rice"),
            FoodItem(itemId = "117", name = "Egg Fried Rice", category = "Indian", isVeg = false, pricePerPiece = 190.0, description = "Rice tossed with egg"),
            FoodItem(itemId = "118", name = "Chicken Keema", category = "Indian", isVeg = false, pricePerPiece = 260.0, description = "Minced chicken masala"),
            FoodItem(itemId = "119", name = "Mutton Keema", category = "Indian", isVeg = false, pricePerPiece = 330.0, description = "Minced mutton spicy curry"),
            FoodItem(itemId = "120", name = "Chicken Kebab", category = "Indian", isVeg = false, pricePerPiece = 250.0, description = "Indian grilled chicken kebabs"),
            FoodItem(itemId = "121", name = "Chicken Pepperoni Pizza", category = "Pizza", isVeg = false, pricePerPiece = 420.0, description = "Pepperoni chicken pizza"),
            FoodItem(itemId = "122", name = "BBQ Chicken Pizza", category = "Pizza", isVeg = false, pricePerPiece = 410.0, description = "Barbecue chicken topping pizza"),
            FoodItem(itemId = "123", name = "Chicken Sausage Pizza", category = "Pizza", isVeg = false, pricePerPiece = 395.0, description = "Loaded sausage pizza"),
            FoodItem(itemId = "124", name = "Tandoori Chicken Pizza", category = "Pizza", isVeg = false, pricePerPiece = 430.0, description = "Indian style chicken pizza"),
            FoodItem(itemId = "125", name = "Chicken Supreme Pizza", category = "Pizza", isVeg = false, pricePerPiece = 440.0, description = "Loaded chicken toppings pizza"),
            FoodItem(itemId = "126", name = "Seafood Pizza", category = "Pizza", isVeg = false, pricePerPiece = 460.0, description = "Pizza topped with seafood"),
            FoodItem(itemId = "127", name = "Prawn Pizza", category = "Pizza", isVeg = false, pricePerPiece = 455.0, description = "Prawn cheese pizza"),
            FoodItem(itemId = "128", name = "Meat Lovers Pizza", category = "Pizza", isVeg = false, pricePerPiece = 480.0, description = "Loaded meat pizza"),
            FoodItem(itemId = "129", name = "Chicken Cheese Burst Pizza", category = "Pizza", isVeg = false, pricePerPiece = 450.0, description = "Extra cheesy chicken pizza"),
            FoodItem(itemId = "130", name = "Spicy Chicken Pizza", category = "Pizza", isVeg = false, pricePerPiece = 425.0, description = "Hot spicy chicken pizza"),
            FoodItem(itemId = "131", name = "Chicken Noodles", category = "Noodles", isVeg = false, pricePerPiece = 220.0, description = "Stir fried chicken noodles"),
            FoodItem(itemId = "132", name = "Egg Noodles", category = "Noodles", isVeg = false, pricePerPiece = 190.0, description = "Noodles tossed with egg"),
            FoodItem(itemId = "133", name = "Prawn Noodles", category = "Noodles", isVeg = false, pricePerPiece = 260.0, description = "Seafood style noodles"),
            FoodItem(itemId = "134", name = "Chicken Hakka Noodles", category = "Noodles", isVeg = false, pricePerPiece = 230.0, description = "Chinese style chicken noodles"),
            FoodItem(itemId = "135", name = "Schezwan Chicken Noodles", category = "Noodles", isVeg = false, pricePerPiece = 240.0, description = "Spicy schezwan noodles"),
            FoodItem(itemId = "136", name = "Chicken Maggi", category = "Maggi", isVeg = false, pricePerPiece = 140.0, description = "Masala maggi with chicken"),
            FoodItem(itemId = "137", name = "Egg Maggi", category = "Maggi", isVeg = false, pricePerPiece = 120.0, description = "Maggi with scrambled egg"),
            FoodItem(itemId = "138", name = "Chicken Cheese Maggi", category = "Maggi", isVeg = false, pricePerPiece = 155.0, description = "Cheesy chicken noodles"),
            FoodItem(itemId = "139", name = "Spicy Chicken Ramen", category = "Noodles", isVeg = false, pricePerPiece = 270.0, description = "Hot ramen with chicken"),
            FoodItem(itemId = "140", name = "Seafood Ramen", category = "Noodles", isVeg = false, pricePerPiece = 320.0, description = "Japanese ramen with seafood"),
            FoodItem(itemId = "141", name = "Chicken Alfredo Pasta", category = "Pasta", isVeg = false, pricePerPiece = 290.0, description = "Creamy chicken pasta"),
            FoodItem(itemId = "142", name = "Chicken Red Sauce Pasta", category = "Pasta", isVeg = false, pricePerPiece = 280.0, description = "Tomato chicken pasta"),
            FoodItem(itemId = "143", name = "Chicken Pink Sauce Pasta", category = "Pasta", isVeg = false, pricePerPiece = 300.0, description = "Mixed sauce chicken pasta"),
            FoodItem(itemId = "144", name = "Prawn Alfredo Pasta", category = "Pasta", isVeg = false, pricePerPiece = 340.0, description = "Creamy prawn pasta"),
            FoodItem(itemId = "145", name = "Seafood Pasta", category = "Pasta", isVeg = false, pricePerPiece = 360.0, description = "Mixed seafood pasta"),
            FoodItem(itemId = "146", name = "Chicken Mac and Cheese", category = "Pasta", isVeg = false, pricePerPiece = 310.0, description = "Cheesy chicken pasta"),
            FoodItem(itemId = "147", name = "Chicken Penne Arrabbiata", category = "Pasta", isVeg = false, pricePerPiece = 295.0, description = "Spicy chicken penne"),
            FoodItem(itemId = "148", name = "Chicken Mushroom Pasta", category = "Pasta", isVeg = false, pricePerPiece = 305.0, description = "Chicken mushroom creamy pasta"),
            FoodItem(itemId = "149", name = "Egg Carbonara Pasta", category = "Pasta", isVeg = false, pricePerPiece = 285.0, description = "Creamy egg pasta"),
            FoodItem(itemId = "150", name = "Chicken Cheese Pasta", category = "Pasta", isVeg = false, pricePerPiece = 315.0, description = "Extra cheesy chicken pasta")
        ))
        items.addAll(listOf(
            FoodItem(itemId = "151", name = "Chicken Burger", category = "Burger", isVeg = false, pricePerPiece = 190.0, description = "Crispy chicken burger"),
            FoodItem(itemId = "152", name = "Chicken Cheese Burger", category = "Burger", isVeg = false, pricePerPiece = 210.0, description = "Burger with cheese chicken patty"),
            FoodItem(itemId = "153", name = "Grilled Chicken Burger", category = "Burger", isVeg = false, pricePerPiece = 220.0, description = "Healthy grilled burger"),
            FoodItem(itemId = "154", name = "Zinger Chicken Burger", category = "Burger", isVeg = false, pricePerPiece = 230.0, description = "Crunchy spicy chicken burger"),
            FoodItem(itemId = "155", name = "Fish Burger", category = "Burger", isVeg = false, pricePerPiece = 215.0, description = "Fish fillet burger"),
            FoodItem(itemId = "156", name = "Chicken Club Sandwich", category = "Sandwich", isVeg = false, pricePerPiece = 210.0, description = "Triple layer chicken sandwich"),
            FoodItem(itemId = "157", name = "Egg Sandwich", category = "Sandwich", isVeg = false, pricePerPiece = 150.0, description = "Boiled egg sandwich"),
            FoodItem(itemId = "158", name = "Chicken Mayo Sandwich", category = "Sandwich", isVeg = false, pricePerPiece = 205.0, description = "Creamy mayo chicken sandwich"),
            FoodItem(itemId = "159", name = "Tuna Sandwich", category = "Sandwich", isVeg = false, pricePerPiece = 220.0, description = "Tuna stuffed sandwich"),
            FoodItem(itemId = "160", name = "Chicken Grilled Sandwich", category = "Sandwich", isVeg = false, pricePerPiece = 215.0, description = "Grilled chicken sandwich"),
            FoodItem(itemId = "161", name = "Chicken Wings", category = "Snacks", isVeg = false, pricePerPiece = 260.0, description = "Crispy spicy wings"),
            FoodItem(itemId = "162", name = "BBQ Wings", category = "Snacks", isVeg = false, pricePerPiece = 275.0, description = "Barbecue glazed wings"),
            FoodItem(itemId = "163", name = "Chicken Nuggets", category = "Snacks", isVeg = false, pricePerPiece = 230.0, description = "Crispy nuggets"),
            FoodItem(itemId = "164", name = "Popcorn Chicken", category = "Snacks", isVeg = false, pricePerPiece = 240.0, description = "Bite sized fried chicken"),
            FoodItem(itemId = "165", name = "Fish Fingers", category = "Snacks", isVeg = false, pricePerPiece = 250.0, description = "Crispy fish strips"),
            FoodItem(itemId = "166", name = "Calamari Rings", category = "Snacks", isVeg = false, pricePerPiece = 290.0, description = "Fried squid rings"),
            FoodItem(itemId = "167", name = "Chicken Wrap", category = "Wrap", isVeg = false, pricePerPiece = 210.0, description = "Chicken stuffed wrap"),
            FoodItem(itemId = "168", name = "Egg Wrap", category = "Wrap", isVeg = false, pricePerPiece = 170.0, description = "Egg loaded wrap"),
            FoodItem(itemId = "169", name = "Chicken Kathi Roll", category = "Roll", isVeg = false, pricePerPiece = 220.0, description = "Indian chicken roll"),
            FoodItem(itemId = "170", name = "Mutton Roll", category = "Roll", isVeg = false, pricePerPiece = 260.0, description = "Stuffed mutton roll"),
            FoodItem(itemId = "171", name = "Chicken Manchurian", category = "Chinese", isVeg = false, pricePerPiece = 270.0, description = "Chicken balls in sauce"),
            FoodItem(itemId = "172", name = "Chicken Chilli", category = "Chinese", isVeg = false, pricePerPiece = 280.0, description = "Spicy chilli chicken"),
            FoodItem(itemId = "173", name = "Dragon Chicken", category = "Chinese", isVeg = false, pricePerPiece = 290.0, description = "Crispy spicy dragon chicken"),
            FoodItem(itemId = "174", name = "Kung Pao Chicken", category = "Chinese", isVeg = false, pricePerPiece = 300.0, description = "Chinese stir fry chicken"),
            FoodItem(itemId = "175", name = "Sweet and Sour Chicken", category = "Chinese", isVeg = false, pricePerPiece = 285.0, description = "Tangy chicken dish"),
            FoodItem(itemId = "176", name = "Chicken Momos", category = "Momos", isVeg = false, pricePerPiece = 190.0, description = "Steamed chicken dumplings"),
            FoodItem(itemId = "177", name = "Fried Chicken Momos", category = "Momos", isVeg = false, pricePerPiece = 210.0, description = "Crispy chicken dumplings"),
            FoodItem(itemId = "178", name = "Prawn Momos", category = "Momos", isVeg = false, pricePerPiece = 240.0, description = "Seafood dumplings"),
            FoodItem(itemId = "179", name = "Chicken Dim Sum", category = "Momos", isVeg = false, pricePerPiece = 230.0, description = "Asian chicken dumplings"),
            FoodItem(itemId = "180", name = "Seafood Dumplings", category = "Momos", isVeg = false, pricePerPiece = 260.0, description = "Mixed seafood dumplings"),
            FoodItem(itemId = "181", name = "Grilled Chicken Steak", category = "World Food", isVeg = false, pricePerPiece = 340.0, description = "Juicy grilled chicken steak"),
            FoodItem(itemId = "182", name = "Fish and Chips", category = "World Food", isVeg = false, pricePerPiece = 320.0, description = "Classic fried fish meal"),
            FoodItem(itemId = "183", name = "Chicken Schnitzel", category = "World Food", isVeg = false, pricePerPiece = 330.0, description = "Breaded fried chicken cutlet"),
            FoodItem(itemId = "184", name = "Roast Chicken", category = "World Food", isVeg = false, pricePerPiece = 350.0, description = "Herb roasted chicken"),
            FoodItem(itemId = "185", name = "Prawn Tempura", category = "World Food", isVeg = false, pricePerPiece = 360.0, description = "Japanese fried prawns"),
            FoodItem(itemId = "186", name = "Chicken Tacos", category = "World Food", isVeg = false, pricePerPiece = 280.0, description = "Mexican chicken tacos"),
            FoodItem(itemId = "187", name = "Beef Taco Style Chicken", category = "World Food", isVeg = false, pricePerPiece = 285.0, description = "Loaded chicken taco"),
            FoodItem(itemId = "188", name = "Chicken Burrito", category = "World Food", isVeg = false, pricePerPiece = 300.0, description = "Mexican stuffed burrito"),
            FoodItem(itemId = "189", name = "Chicken Quesadilla", category = "World Food", isVeg = false, pricePerPiece = 295.0, description = "Cheesy chicken tortilla"),
            FoodItem(itemId = "190", name = "Seafood Paella", category = "World Food", isVeg = false, pricePerPiece = 390.0, description = "Spanish rice seafood dish"),
            FoodItem(itemId = "191", name = "Chicken Shawarma", category = "World Food", isVeg = false, pricePerPiece = 240.0, description = "Middle eastern wrap"),
            FoodItem(itemId = "192", name = "Chicken Falafel Wrap", category = "World Food", isVeg = false, pricePerPiece = 250.0, description = "Fusion chicken wrap"),
            FoodItem(itemId = "193", name = "Chicken Souvlaki", category = "World Food", isVeg = false, pricePerPiece = 310.0, description = "Greek grilled chicken skewers"),
            FoodItem(itemId = "194", name = "Chicken Satay", category = "World Food", isVeg = false, pricePerPiece = 300.0, description = "Thai grilled chicken skewers"),
            FoodItem(itemId = "195", name = "Teriyaki Chicken", category = "World Food", isVeg = false, pricePerPiece = 320.0, description = "Japanese glazed chicken"),
            FoodItem(itemId = "196", name = "Korean Fried Chicken", category = "World Food", isVeg = false, pricePerPiece = 330.0, description = "Crispy korean chicken"),
            FoodItem(itemId = "197", name = "Chicken Lasagna", category = "World Food", isVeg = false, pricePerPiece = 340.0, description = "Layered baked chicken pasta"),
            FoodItem(itemId = "198", name = "Seafood Lasagna", category = "World Food", isVeg = false, pricePerPiece = 370.0, description = "Seafood layered pasta bake"),
            FoodItem(itemId = "199", name = "Chicken Meatballs", category = "World Food", isVeg = false, pricePerPiece = 290.0, description = "Saucy chicken meatballs"),
            FoodItem(itemId = "200", name = "Prawn Risotto", category = "World Food", isVeg = false, pricePerPiece = 380.0, description = "Creamy italian seafood rice")
        ))

        // ================ Chats==============//
        items.addAll(listOf(
            FoodItem(itemId = "201", name = "Pani Puri", category = "Chats", isVeg = true, pricePerPiece = 60.0, description = "Crispy puris with spicy tangy water"),
            FoodItem(itemId = "202", name = "Sev Puri", category = "Chats", isVeg = true, pricePerPiece = 70.0, description = "Flat puris topped with chutneys and sev"),
            FoodItem(itemId = "203", name = "Bhel Puri", category = "Chats", isVeg = true, pricePerPiece = 65.0, description = "Puffed rice mixed with chutneys"),
            FoodItem(itemId = "204", name = "Dahi Puri", category = "Chats", isVeg = true, pricePerPiece = 75.0, description = "Puris filled with yogurt and chutneys"),
            FoodItem(itemId = "205", name = "Masala Puri", category = "Chats", isVeg = true, pricePerPiece = 80.0, description = "Crushed puris with spicy gravy"),
            FoodItem(itemId = "206", name = "Samosa Chaat", category = "Chats", isVeg = true, pricePerPiece = 85.0, description = "Samosa topped with curd and chutneys"),
            FoodItem(itemId = "207", name = "Aloo Tikki Chaat", category = "Chats", isVeg = true, pricePerPiece = 90.0, description = "Potato tikki with chutneys"),
            FoodItem(itemId = "208", name = "Papdi Chaat", category = "Chats", isVeg = true, pricePerPiece = 88.0, description = "Papdi with yogurt and masala"),
            FoodItem(itemId = "209", name = "Raj Kachori", category = "Chats", isVeg = true, pricePerPiece = 110.0, description = "Large crispy shell stuffed with chaat fillings"),
            FoodItem(itemId = "210", name = "Ragda Pattice", category = "Chats", isVeg = true, pricePerPiece = 95.0, description = "Potato patties with white peas curry"),
            FoodItem(itemId = "211", name = "Dahi Bhalla", category = "Chats", isVeg = true, pricePerPiece = 100.0, description = "Soft lentil dumplings with curd"),
            FoodItem(itemId = "212", name = "Kachori Chaat", category = "Chats", isVeg = true, pricePerPiece = 85.0, description = "Kachori served with spicy toppings"),
            FoodItem(itemId = "213", name = "Corn Chaat", category = "Chats", isVeg = true, pricePerPiece = 75.0, description = "Sweet corn tossed with spices"),
            FoodItem(itemId = "214", name = "Sprouts Chaat", category = "Chats", isVeg = true, pricePerPiece = 80.0, description = "Healthy sprouts with masala"),
            FoodItem(itemId = "215", name = "Matar Chaat", category = "Chats", isVeg = true, pricePerPiece = 78.0, description = "Spiced green peas snack"),
            FoodItem(itemId = "216", name = "Cheese Sev Puri", category = "Chats", isVeg = true, pricePerPiece = 95.0, description = "Sev puri topped with cheese"),
            FoodItem(itemId = "217", name = "Paneer Chaat", category = "Chats", isVeg = true, pricePerPiece = 110.0, description = "Paneer cubes tossed with chutneys"),
            FoodItem(itemId = "218", name = "Khasta Chaat", category = "Chats", isVeg = true, pricePerPiece = 90.0, description = "Crunchy khasta with toppings"),
            FoodItem(itemId = "219", name = "Chana Chaat", category = "Chats", isVeg = true, pricePerPiece = 82.0, description = "Spicy chickpea salad snack"),
            FoodItem(itemId = "220", name = "Fruit Chaat", category = "Chats", isVeg = true, pricePerPiece = 85.0, description = "Fresh fruits with chaat masala"),
            FoodItem(itemId = "221", name = "Gobi Manchurian Dry", category = "Chats", isVeg = true, pricePerPiece = 140.0, description = "Crispy cauliflower in spicy sauce"),
            FoodItem(itemId = "222", name = "Gobi Manchurian Gravy", category = "Chats", isVeg = true, pricePerPiece = 150.0, description = "Cauliflower in manchurian gravy"),
            FoodItem(itemId = "223", name = "Chilli Gobi", category = "Chats", isVeg = true, pricePerPiece = 145.0, description = "Spicy chilli cauliflower"),
            FoodItem(itemId = "224", name = "Crispy Gobi", category = "Chats", isVeg = true, pricePerPiece = 135.0, description = "Deep fried crispy cauliflower bites"),
            FoodItem(itemId = "225", name = "Schezwan Gobi", category = "Chats", isVeg = true, pricePerPiece = 155.0, description = "Cauliflower tossed in schezwan sauce")
        ))
        //================= Soup==============//
        items.addAll(listOf(
            FoodItem(itemId = "226", name = "Tomato Soup", category = "Soup", isVeg = true, pricePerPiece = 90.0, description = "Classic creamy tomato soup"),
            FoodItem(itemId = "227", name = "Sweet Corn Soup", category = "Soup", isVeg = true, pricePerPiece = 100.0, description = "Sweet corn flavored creamy soup"),
            FoodItem(itemId = "228", name = "Hot and Sour Soup", category = "Soup", isVeg = true, pricePerPiece = 110.0, description = "Spicy tangy vegetable soup"),
            FoodItem(itemId = "229", name = "Manchow Soup", category = "Soup", isVeg = true, pricePerPiece = 115.0, description = "Spicy Indo-Chinese soup with crispy noodles"),
            FoodItem(itemId = "230", name = "Veg Clear Soup", category = "Soup", isVeg = true, pricePerPiece = 95.0, description = "Light healthy vegetable broth"),
            FoodItem(itemId = "231", name = "Lemon Coriander Soup", category = "Soup", isVeg = true, pricePerPiece = 105.0, description = "Refreshing lemon herb soup"),
            FoodItem(itemId = "232", name = "Cream of Mushroom Soup", category = "Soup", isVeg = true, pricePerPiece = 120.0, description = "Rich creamy mushroom soup"),
            FoodItem(itemId = "233", name = "Cream of Broccoli Soup", category = "Soup", isVeg = true, pricePerPiece = 125.0, description = "Smooth broccoli cream soup"),
            FoodItem(itemId = "234", name = "Mixed Vegetable Soup", category = "Soup", isVeg = true, pricePerPiece = 110.0, description = "Healthy assorted veggie soup"),
            FoodItem(itemId = "235", name = "Spinach Soup", category = "Soup", isVeg = true, pricePerPiece = 100.0, description = "Nutritious spinach blended soup"),
            FoodItem(itemId = "236", name = "Carrot Ginger Soup", category = "Soup", isVeg = true, pricePerPiece = 105.0, description = "Sweet carrot soup with ginger"),
            FoodItem(itemId = "237", name = "Pumpkin Soup", category = "Soup", isVeg = true, pricePerPiece = 115.0, description = "Creamy pumpkin delight"),
            FoodItem(itemId = "238", name = "French Onion Soup", category = "Soup", isVeg = true, pricePerPiece = 130.0, description = "Rich onion broth soup"),
            FoodItem(itemId = "239", name = "Minestrone Soup", category = "Soup", isVeg = true, pricePerPiece = 140.0, description = "Italian vegetable pasta soup"),
            FoodItem(itemId = "240", name = "Cheese Corn Soup", category = "Soup", isVeg = true, pricePerPiece = 125.0, description = "Creamy cheese and corn soup"),
            FoodItem(itemId = "241", name = "Cabbage Soup", category = "Soup", isVeg = true, pricePerPiece = 95.0, description = "Light healthy cabbage broth"),
            FoodItem(itemId = "242", name = "Beetroot Soup", category = "Soup", isVeg = true, pricePerPiece = 110.0, description = "Earthy beetroot blended soup"),
            FoodItem(itemId = "243", name = "Potato Leek Soup", category = "Soup", isVeg = true, pricePerPiece = 130.0, description = "Creamy potato and leek soup"),
            FoodItem(itemId = "244", name = "Thai Coconut Soup Veg", category = "Soup", isVeg = true, pricePerPiece = 145.0, description = "Coconut based Thai vegetable soup"),
            FoodItem(itemId = "245", name = "Mexican Bean Soup", category = "Soup", isVeg = true, pricePerPiece = 135.0, description = "Spicy bean loaded soup"),
            FoodItem(itemId = "246", name = "Noodle Soup Veg", category = "Soup", isVeg = true, pricePerPiece = 120.0, description = "Vegetable noodle broth soup"),
            FoodItem(itemId = "247", name = "Paneer Soup", category = "Soup", isVeg = true, pricePerPiece = 135.0, description = "Paneer cubes in light broth"),
            FoodItem(itemId = "248", name = "Mushroom Clear Soup", category = "Soup", isVeg = true, pricePerPiece = 115.0, description = "Light mushroom flavored broth"),
            FoodItem(itemId = "249", name = "Corn Spinach Soup", category = "Soup", isVeg = true, pricePerPiece = 125.0, description = "Corn and spinach creamy soup"),
            FoodItem(itemId = "250", name = "Herbal Vegetable Soup", category = "Soup", isVeg = true, pricePerPiece = 130.0, description = "Healthy herbs and veggie soup")
        ))
        //================== Ice-cream===========//
        items.addAll(listOf(
            FoodItem(itemId = "251", name = "Vanilla Ice Cream", category = "Ice Cream", isVeg = true, pricePerPiece = 80.0, description = "Classic creamy vanilla flavor"),
            FoodItem(itemId = "252", name = "Chocolate Ice Cream", category = "Ice Cream", isVeg = true, pricePerPiece = 90.0, description = "Rich chocolate delight"),
            FoodItem(itemId = "253", name = "Strawberry Ice Cream", category = "Ice Cream", isVeg = true, pricePerPiece = 90.0, description = "Sweet strawberry creamy scoop"),
            FoodItem(itemId = "254", name = "Butterscotch Ice Cream", category = "Ice Cream", isVeg = true, pricePerPiece = 95.0, description = "Crunchy caramel butterscotch flavor"),
            FoodItem(itemId = "255", name = "Mango Ice Cream", category = "Ice Cream", isVeg = true, pricePerPiece = 95.0, description = "Refreshing mango flavored ice cream"),
            FoodItem(itemId = "256", name = "Black Currant Ice Cream", category = "Ice Cream", isVeg = true, pricePerPiece = 100.0, description = "Tangy black currant dessert"),
            FoodItem(itemId = "257", name = "Pista Ice Cream", category = "Ice Cream", isVeg = true, pricePerPiece = 100.0, description = "Nutty pistachio creamy treat"),
            FoodItem(itemId = "258", name = "Kulfi Malai", category = "Ice Cream", isVeg = true, pricePerPiece = 110.0, description = "Traditional rich malai kulfi"),
            FoodItem(itemId = "259", name = "Kesar Pista Kulfi", category = "Ice Cream", isVeg = true, pricePerPiece = 120.0, description = "Saffron pista kulfi"),
            FoodItem(itemId = "260", name = "Choco Chip Ice Cream", category = "Ice Cream", isVeg = true, pricePerPiece = 105.0, description = "Chocolate chips in creamy base"),
            FoodItem(itemId = "261", name = "Cookies and Cream", category = "Ice Cream", isVeg = true, pricePerPiece = 110.0, description = "Crunchy cookies mixed ice cream"),
            FoodItem(itemId = "262", name = "Coffee Ice Cream", category = "Ice Cream", isVeg = true, pricePerPiece = 100.0, description = "Smooth coffee flavored dessert"),
            FoodItem(itemId = "263", name = "Caramel Swirl Ice Cream", category = "Ice Cream", isVeg = true, pricePerPiece = 115.0, description = "Creamy caramel ribbon dessert"),
            FoodItem(itemId = "264", name = "Mint Chocolate Ice Cream", category = "Ice Cream", isVeg = true, pricePerPiece = 110.0, description = "Minty chocolate freshness"),
            FoodItem(itemId = "265", name = "Blueberry Ice Cream", category = "Ice Cream", isVeg = true, pricePerPiece = 115.0, description = "Berry flavored creamy scoop"),
            FoodItem(itemId = "266", name = "Raspberry Ripple", category = "Ice Cream", isVeg = true, pricePerPiece = 115.0, description = "Tangy raspberry swirls"),
            FoodItem(itemId = "267", name = "Tender Coconut Ice Cream", category = "Ice Cream", isVeg = true, pricePerPiece = 120.0, description = "Fresh coconut creamy dessert"),
            FoodItem(itemId = "268", name = "Sitaphal Ice Cream", category = "Ice Cream", isVeg = true, pricePerPiece = 125.0, description = "Custard apple flavored treat"),
            FoodItem(itemId = "269", name = "Brownie Fudge Ice Cream", category = "Ice Cream", isVeg = true, pricePerPiece = 130.0, description = "Brownie chunks with fudge"),
            FoodItem(itemId = "270", name = "Belgian Chocolate Ice Cream", category = "Ice Cream", isVeg = true, pricePerPiece = 135.0, description = "Premium chocolate richness"),
            FoodItem(itemId = "271", name = "Rocky Road Ice Cream", category = "Ice Cream", isVeg = true, pricePerPiece = 130.0, description = "Nuts marshmallow chocolate mix"),
            FoodItem(itemId = "272", name = "Falooda Ice Cream", category = "Ice Cream", isVeg = true, pricePerPiece = 140.0, description = "Ice cream served with falooda"),
            FoodItem(itemId = "273", name = "Sundae Special", category = "Ice Cream", isVeg = true, pricePerPiece = 150.0, description = "Ice cream with toppings and syrup"),
            FoodItem(itemId = "274", name = "Cassata Ice Cream", category = "Ice Cream", isVeg = true, pricePerPiece = 145.0, description = "Layered fruity frozen dessert"),
            FoodItem(itemId = "275", name = "Fruit n Nut Ice Cream", category = "Ice Cream", isVeg = true, pricePerPiece = 135.0, description = "Mixed fruits and nuts delight")
        ))
        //============ Juice===============//
        items.addAll(listOf(
            FoodItem(itemId = "276", name = "Orange Juice", category = "Juice", isVeg = true, pricePerPiece = 90.0, description = "Fresh squeezed orange juice"),
            FoodItem(itemId = "277", name = "Mango Juice", category = "Juice", isVeg = true, pricePerPiece = 100.0, description = "Sweet ripe mango drink"),
            FoodItem(itemId = "278", name = "Apple Juice", category = "Juice", isVeg = true, pricePerPiece = 95.0, description = "Refreshing apple fruit juice"),
            FoodItem(itemId = "279", name = "Pineapple Juice", category = "Juice", isVeg = true, pricePerPiece = 95.0, description = "Tropical pineapple refreshment"),
            FoodItem(itemId = "280", name = "Watermelon Juice", category = "Juice", isVeg = true, pricePerPiece = 85.0, description = "Cool hydrating watermelon drink"),
            FoodItem(itemId = "281", name = "Mosambi Juice", category = "Juice", isVeg = true, pricePerPiece = 90.0, description = "Fresh sweet lime juice"),
            FoodItem(itemId = "282", name = "Pomegranate Juice", category = "Juice", isVeg = true, pricePerPiece = 110.0, description = "Healthy antioxidant rich juice"),
            FoodItem(itemId = "283", name = "Grape Juice", category = "Juice", isVeg = true, pricePerPiece = 95.0, description = "Sweet chilled grape drink"),
            FoodItem(itemId = "284", name = "Guava Juice", category = "Juice", isVeg = true, pricePerPiece = 100.0, description = "Fresh guava blended juice"),
            FoodItem(itemId = "285", name = "Papaya Juice", category = "Juice", isVeg = true, pricePerPiece = 95.0, description = "Healthy papaya fruit juice"),
            FoodItem(itemId = "286", name = "Kiwi Juice", category = "Juice", isVeg = true, pricePerPiece = 120.0, description = "Tangy kiwi refreshing drink"),
            FoodItem(itemId = "287", name = "Litchi Juice", category = "Juice", isVeg = true, pricePerPiece = 110.0, description = "Sweet litchi summer drink"),
            FoodItem(itemId = "288", name = "Strawberry Juice", category = "Juice", isVeg = true, pricePerPiece = 115.0, description = "Berry flavored chilled juice"),
            FoodItem(itemId = "289", name = "Blueberry Juice", category = "Juice", isVeg = true, pricePerPiece = 125.0, description = "Fresh blueberry fruity drink"),
            FoodItem(itemId = "290", name = "Mixed Fruit Juice", category = "Juice", isVeg = true, pricePerPiece = 130.0, description = "Blend of seasonal fruits"),
            FoodItem(itemId = "291", name = "Carrot Juice", category = "Juice", isVeg = true, pricePerPiece = 90.0, description = "Healthy vitamin rich carrot juice"),
            FoodItem(itemId = "292", name = "Beetroot Juice", category = "Juice", isVeg = true, pricePerPiece = 95.0, description = "Nutritious beetroot health drink"),
            FoodItem(itemId = "293", name = "Carrot Beetroot Juice", category = "Juice", isVeg = true, pricePerPiece = 100.0, description = "Healthy mixed veggie juice"),
            FoodItem(itemId = "294", name = "Amla Juice", category = "Juice", isVeg = true, pricePerPiece = 85.0, description = "Vitamin C rich gooseberry juice"),
            FoodItem(itemId = "295", name = "Lemon Mint Juice", category = "Juice", isVeg = true, pricePerPiece = 80.0, description = "Refreshing lemon mint cooler"),
            FoodItem(itemId = "296", name = "Coconut Water", category = "Juice", isVeg = true, pricePerPiece = 70.0, description = "Natural tender coconut drink"),
            FoodItem(itemId = "297", name = "Sugarcane Juice", category = "Juice", isVeg = true, pricePerPiece = 75.0, description = "Fresh sweet sugarcane drink"),
            FoodItem(itemId = "298", name = "Dragon Fruit Juice", category = "Juice", isVeg = true, pricePerPiece = 140.0, description = "Exotic dragon fruit refreshment"),
            FoodItem(itemId = "299", name = "Avocado Juice", category = "Juice", isVeg = true, pricePerPiece = 135.0, description = "Creamy avocado blended drink"),
            FoodItem(itemId = "300", name = "Passion Fruit Juice", category = "Juice", isVeg = true, pricePerPiece = 145.0, description = "Tangy tropical fruit juice")
        ))
        //===========MILK Shake=======//
        items.addAll(listOf(
            FoodItem(itemId = "301", name = "Chocolate Milkshake", category = "Milkshake", isVeg = true, pricePerPiece = 120.0, description = "Rich creamy chocolate shake"),
            FoodItem(itemId = "302", name = "Vanilla Milkshake", category = "Milkshake", isVeg = true, pricePerPiece = 110.0, description = "Classic smooth vanilla shake"),
            FoodItem(itemId = "303", name = "Strawberry Milkshake", category = "Milkshake", isVeg = true, pricePerPiece = 125.0, description = "Sweet strawberry blended shake"),
            FoodItem(itemId = "304", name = "Mango Milkshake", category = "Milkshake", isVeg = true, pricePerPiece = 130.0, description = "Fresh mango creamy shake"),
            FoodItem(itemId = "305", name = "Banana Milkshake", category = "Milkshake", isVeg = true, pricePerPiece = 115.0, description = "Healthy banana thick shake"),
            FoodItem(itemId = "306", name = "Oreo Milkshake", category = "Milkshake", isVeg = true, pricePerPiece = 140.0, description = "Cookies blended creamy shake"),
            FoodItem(itemId = "307", name = "KitKat Milkshake", category = "Milkshake", isVeg = true, pricePerPiece = 145.0, description = "Chocolate wafer shake"),
            FoodItem(itemId = "308", name = "Butterscotch Milkshake", category = "Milkshake", isVeg = true, pricePerPiece = 135.0, description = "Crunchy caramel creamy shake"),
            FoodItem(itemId = "309", name = "Coffee Milkshake", category = "Milkshake", isVeg = true, pricePerPiece = 130.0, description = "Cold coffee creamy shake"),
            FoodItem(itemId = "310", name = "Black Currant Milkshake", category = "Milkshake", isVeg = true, pricePerPiece = 140.0, description = "Berry flavored thick shake"),
            FoodItem(itemId = "311", name = "Blueberry Milkshake", category = "Milkshake", isVeg = true, pricePerPiece = 150.0, description = "Fresh blueberry creamy drink"),
            FoodItem(itemId = "312", name = "Pista Milkshake", category = "Milkshake", isVeg = true, pricePerPiece = 145.0, description = "Nutty pistachio rich shake"),
            FoodItem(itemId = "313", name = "Kesar Badam Milkshake", category = "Milkshake", isVeg = true, pricePerPiece = 155.0, description = "Saffron almond premium shake"),
            FoodItem(itemId = "314", name = "Caramel Milkshake", category = "Milkshake", isVeg = true, pricePerPiece = 145.0, description = "Sweet caramel blended shake"),
            FoodItem(itemId = "315", name = "Brownie Milkshake", category = "Milkshake", isVeg = true, pricePerPiece = 160.0, description = "Brownie chunks creamy shake"),
            FoodItem(itemId = "316", name = "Belgian Chocolate Shake", category = "Milkshake", isVeg = true, pricePerPiece = 165.0, description = "Premium chocolate thick shake"),
            FoodItem(itemId = "317", name = "Peanut Butter Milkshake", category = "Milkshake", isVeg = true, pricePerPiece = 155.0, description = "Nutty creamy protein shake"),
            FoodItem(itemId = "318", name = "Avocado Milkshake", category = "Milkshake", isVeg = true, pricePerPiece = 150.0, description = "Healthy creamy avocado shake"),
            FoodItem(itemId = "319", name = "Tender Coconut Milkshake", category = "Milkshake", isVeg = true, pricePerPiece = 145.0, description = "Fresh coconut creamy drink"),
            FoodItem(itemId = "320", name = "Rose Milkshake", category = "Milkshake", isVeg = true, pricePerPiece = 130.0, description = "Rose flavored chilled shake"),
            FoodItem(itemId = "321", name = "Chikoo Milkshake", category = "Milkshake", isVeg = true, pricePerPiece = 135.0, description = "Sapota blended thick shake"),
            FoodItem(itemId = "322", name = "Dates Milkshake", category = "Milkshake", isVeg = true, pricePerPiece = 145.0, description = "Healthy dates energy shake"),
            FoodItem(itemId = "323", name = "Dry Fruit Milkshake", category = "Milkshake", isVeg = true, pricePerPiece = 160.0, description = "Mixed nuts rich shake"),
            FoodItem(itemId = "324", name = "Kulfi Milkshake", category = "Milkshake", isVeg = true, pricePerPiece = 155.0, description = "Traditional kulfi flavored shake"),
            FoodItem(itemId = "325", name = "Falooda Milkshake", category = "Milkshake", isVeg = true, pricePerPiece = 165.0, description = "Shake with falooda flavor")
        ))
        //=============== Cakes==========//
        items.addAll(listOf(
            FoodItem(itemId = "326", name = "Chocolate Cake", category = "Cake", isVeg = true, pricePerPiece = 180.0, description = "Rich moist chocolate layered cake"),
            FoodItem(itemId = "327", name = "Black Forest Cake", category = "Cake", isVeg = true, pricePerPiece = 220.0, description = "Chocolate cake with cream and cherries"),
            FoodItem(itemId = "328", name = "White Forest Cake", category = "Cake", isVeg = true, pricePerPiece = 220.0, description = "Vanilla cream cake with cherries"),
            FoodItem(itemId = "329", name = "Red Velvet Cake", category = "Cake", isVeg = true, pricePerPiece = 240.0, description = "Soft cocoa cake with cream cheese frosting"),
            FoodItem(itemId = "330", name = "Vanilla Cake", category = "Cake", isVeg = true, pricePerPiece = 170.0, description = "Classic vanilla sponge cake"),
            FoodItem(itemId = "331", name = "Butterscotch Cake", category = "Cake", isVeg = true, pricePerPiece = 210.0, description = "Crunchy caramel flavored cake"),
            FoodItem(itemId = "332", name = "Strawberry Cake", category = "Cake", isVeg = true, pricePerPiece = 230.0, description = "Fresh strawberry cream cake"),
            FoodItem(itemId = "333", name = "Pineapple Cake", category = "Cake", isVeg = true, pricePerPiece = 220.0, description = "Tropical pineapple cream cake"),
            FoodItem(itemId = "334", name = "Mango Cake", category = "Cake", isVeg = true, pricePerPiece = 230.0, description = "Fresh mango layered cake"),
            FoodItem(itemId = "335", name = "Blueberry Cake", category = "Cake", isVeg = true, pricePerPiece = 240.0, description = "Berry flavored creamy cake"),
            FoodItem(itemId = "336", name = "Oreo Cake", category = "Cake", isVeg = true, pricePerPiece = 250.0, description = "Cookies and cream layered cake"),
            FoodItem(itemId = "337", name = "KitKat Cake", category = "Cake", isVeg = true, pricePerPiece = 260.0, description = "Chocolate cake topped with KitKat"),
            FoodItem(itemId = "338", name = "Ferrero Rocher Cake", category = "Cake", isVeg = true, pricePerPiece = 290.0, description = "Hazelnut chocolate premium cake"),
            FoodItem(itemId = "339", name = "Coffee Mocha Cake", category = "Cake", isVeg = true, pricePerPiece = 240.0, description = "Coffee flavored chocolate cake"),
            FoodItem(itemId = "340", name = "Cheese Cake", category = "Cake", isVeg = true, pricePerPiece = 270.0, description = "Creamy baked cheesecake"),
            FoodItem(itemId = "341", name = "Caramel Cake", category = "Cake", isVeg = true, pricePerPiece = 230.0, description = "Sweet caramel layered dessert cake"),
            FoodItem(itemId = "342", name = "Brownie Cake", category = "Cake", isVeg = true, pricePerPiece = 250.0, description = "Dense chocolate brownie cake"),
            FoodItem(itemId = "343", name = "Fruit Cake", category = "Cake", isVeg = true, pricePerPiece = 220.0, description = "Mixed fruit rich cake"),
            FoodItem(itemId = "344", name = "Dry Fruit Cake", category = "Cake", isVeg = true, pricePerPiece = 240.0, description = "Nut loaded festive cake"),
            FoodItem(itemId = "345", name = "Rainbow Cake", category = "Cake", isVeg = true, pricePerPiece = 260.0, description = "Colorful layered cream cake"),
            FoodItem(itemId = "346", name = "Tiramisu Cake", category = "Cake", isVeg = true, pricePerPiece = 290.0, description = "Coffee cream Italian dessert cake"),
            FoodItem(itemId = "347", name = "Belgian Chocolate Cake", category = "Cake", isVeg = true, pricePerPiece = 300.0, description = "Premium dark chocolate cake"),
            FoodItem(itemId = "348", name = "Rose Milk Cake", category = "Cake", isVeg = true, pricePerPiece = 235.0, description = "Rose flavored creamy cake"),
            FoodItem(itemId = "349", name = "Kulfi Cake", category = "Cake", isVeg = true, pricePerPiece = 250.0, description = "Traditional kulfi inspired dessert cake"),
            FoodItem(itemId = "350", name = "Ice Cream Cake", category = "Cake", isVeg = true, pricePerPiece = 320.0, description = "Frozen creamy celebration cake")
        ))

        // ================= SAVE TO FIRESTORE =================

        val batch = db.batch()

        items.forEach { item ->
            val ref = db.collection(FOOD_MENU_COL)
                .document(item.itemId)
            batch.set(ref, item)
        }

        batch.commit()
            .addOnSuccessListener {
                onComplete(true)
            }
            .addOnFailureListener {
                onComplete(false)
            }
    }

    // ───────────────── FOOD ORDER ─────────────────

    fun saveFoodOrder(
        order: FoodOrder,
        onResult: (Boolean, String) -> Unit
    ) {

        val docRef = db.collection(FOOD_ORDERS_COL).document()

        val finalOrder = order.copy(
            orderId = docRef.id
        )

        docRef.set(finalOrder)
            .addOnSuccessListener {
                onResult(true, docRef.id)
            }
            .addOnFailureListener {
                onResult(false, it.message ?: "Error")
            }
    }

    fun getAllFoodOrders(
        onResult: (List<FoodOrder>) -> Unit
    ) {

        db.collection(FOOD_ORDERS_COL)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->

                val orders =
                    snap?.documents?.mapNotNull {
                        it.toObject(FoodOrder::class.java)
                    } ?: emptyList()

                onResult(orders)
            }
    }

    // ───────────────── ISSUES ─────────────────

    fun saveIssue(
        issue: IssueReport,
        onResult: (Boolean) -> Unit
    ) {

        val docRef = db.collection(ISSUES_COL).document()

        val finalIssue = issue.copy(
            issueId = docRef.id
        )

        docRef.set(finalIssue)
            .addOnSuccessListener {
                onResult(true)
            }
            .addOnFailureListener {
                onResult(false)
            }
    }

    // ───────────────── RESORT INFO ─────────────────

    fun getResortInfo(
        onResult: (ResortInfo?) -> Unit
    ) {
        db.collection(RESORT_INFO_COL)
            .document("main")
            .get()
            .addOnSuccessListener { doc ->
                onResult(doc.toObject(ResortInfo::class.java))
            }
            .addOnFailureListener {
                onResult(null)
            }
    }

    // ───────────────── FOOD MENU ─────────────────

    fun getFoodMenu(
        onResult: (List<FoodItem>) -> Unit
    ) {
        db.collection(FOOD_MENU_COL)
            .get()
            .addOnSuccessListener { snap ->

                val items =
                    snap.documents.mapNotNull {
                        it.toObject(FoodItem::class.java)
                    }

                onResult(items)
            }
            .addOnFailureListener {
                onResult(emptyList())
            }
    }
}