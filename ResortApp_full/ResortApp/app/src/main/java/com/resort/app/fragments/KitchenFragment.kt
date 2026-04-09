package com.resort.app.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.resort.app.R
import com.resort.app.adapters.FoodCategoryAdapter
import com.resort.app.databinding.FragmentKitchenBinding
import com.resort.app.models.FoodItem
import com.resort.app.models.FoodOrder
import com.resort.app.models.OrderItem
import com.resort.app.models.User
import com.resort.app.utils.EmailHelper
import com.resort.app.utils.FirebaseHelper
import kotlinx.coroutines.launch

class KitchenFragment : Fragment() {

    private var _binding: FragmentKitchenBinding? = null
    private val binding get() = _binding!!

    private val cart = mutableListOf<OrderItem>()
    private var allFoodItems = listOf<FoodItem>()
    private var currentUser: User? = null

    private var roomNumberForOrder: String = ""
    private var birthdayBoyName: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentKitchenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecycler()
        loadUser()
        loadMenu()


        // RUN ONLY ONCE TO SEED HUGE MENU
        FirebaseHelper.seedHugeKitchenMenu { success ->
            if (success) {
                Toast.makeText(
                    requireContext(),
                    "Huge Kitchen Menu Added",
                    Toast.LENGTH_LONG
                ).show()
            }
        }


        binding.btnViewCart.setOnClickListener {
            showRoomNumberDialog()
        }

        updateCartBadge()
    }

    private fun setupRecycler() {
        binding.rvFood.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun loadUser() {
        val uid = FirebaseHelper.currentUid ?: return

        FirebaseHelper.getUser(uid) { user ->
            currentUser = user
        }
    }

    private fun loadMenu() {
        binding.progressBar.visibility = View.VISIBLE

        FirebaseHelper.getFoodMenu { items ->
            binding.progressBar.visibility = View.GONE

            allFoodItems = items.sortedBy { it.category }
            setupCategoryTabs(allFoodItems)
        }
    }

    private fun setupCategoryTabs(items: List<FoodItem>) {

        val categories = linkedMapOf(
            "all" to "🍽️ All",
            "Indian" to "🍛 Indian",
            "Pizza" to "🍕 Pizza",
            "Maggi" to "🍜 Maggi",
            "Pasta" to "🍝 Pasta",
            "Burger" to "🍔 Burger",
            "Sandwich" to "🥪 Sandwich",
            "Snacks" to "🍟 Snacks",
            "Wrap" to "🌯 Wrap",
            "Chinese" to "🥡 Chinese",
            "Momos" to "🥟 Momos",
            "Soup" to "🍲 Soup",
            "Roll" to "🌮 Roll",
            "Noodles" to "🍜 Noodles",
            "World Food" to "🌍 World Food",
            "Chats" to "🥙 Chats",
            "Ice Cream" to "🍦 Ice Cream",
            "Juice" to "🧃 Juice",
            "Milkshake" to "🥤 Milkshake",
            "Cake" to "🎂 Cake"
        )

        binding.tabLayout.removeAllTabs()

        categories.forEach { (_, label) ->
            binding.tabLayout.addTab(
                binding.tabLayout.newTab().setText(label)
            )
        }

        val categoryKeys = categories.keys.toList()

        showCategory(items, "all")

        binding.tabLayout.clearOnTabSelectedListeners()

        binding.tabLayout.addOnTabSelectedListener(
            object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {

                override fun onTabSelected(
                    tab: com.google.android.material.tabs.TabLayout.Tab
                ) {
                    val selectedKey = categoryKeys[tab.position]
                    showCategory(items, selectedKey)
                }

                override fun onTabUnselected(
                    tab: com.google.android.material.tabs.TabLayout.Tab
                ) = Unit

                override fun onTabReselected(
                    tab: com.google.android.material.tabs.TabLayout.Tab
                ) = Unit
            }
        )
    }

    private fun showCategory(
        items: List<FoodItem>,
        category: String
    ) {

        val filtered =
            if (category == "all") {
                items
            } else {
                items.filter {
                    it.category.trim()
                        .equals(category.trim(), ignoreCase = true)
                }
            }

        binding.rvFood.adapter =
            FoodCategoryAdapter(filtered) { item ->
                showQuantityDialog(item)
            }
    }

    private fun showQuantityDialog(item: FoodItem) {

        val dialogView =
            layoutInflater.inflate(R.layout.dialog_quantity, null)

        val tvItemName =
            dialogView.findViewById<TextView>(R.id.tvItemName)

        val tvPrice =
            dialogView.findViewById<TextView>(R.id.tvPrice)

        val tvTotal =
            dialogView.findViewById<TextView>(R.id.tvTotal)

        val etQty =
            dialogView.findViewById<EditText>(R.id.etQuantity)

        val btnMinus =
            dialogView.findViewById<ImageButton>(R.id.btnMinus)

        val btnPlus =
            dialogView.findViewById<ImageButton>(R.id.btnPlus)

        val isCake = item.category.equals("Cake", true)

        tvItemName.text = item.name

        tvPrice.text =
            if (isCake) {
                "₹${String.format("%,.2f", item.pricePerPiece)} per kg"
            } else {
                "₹${String.format("%,.2f", item.pricePerPiece)} per piece"
            }

        var qty = 1
        var isUpdatingText = false

        etQty.setText(qty.toString())
        etQty.setSelection(etQty.text?.length ?: 0)

        fun calculateDiscountedTotal(): Double {

            val originalTotal = qty * item.pricePerPiece

            val discountPercent =
                when {
                    qty >= 5 -> 12
                    qty == 4 -> 9
                    qty == 3 -> 7
                    else -> 0
                }

            val discountAmount =
                originalTotal * discountPercent / 100.0

            return originalTotal - discountAmount
        }

        fun updateTotal() {

            val finalTotal = calculateDiscountedTotal()

            val discountText =
                when {
                    qty >= 5 -> " (12% OFF)"
                    qty == 4 -> " (9% OFF)"
                    qty == 3 -> " (7% OFF)"
                    else -> ""
                }

            tvTotal.text =
                "Total: ₹${String.format("%,.2f", finalTotal)}$discountText"
        }

        updateTotal()

        btnMinus.setOnClickListener {
            if (qty > 1) {
                qty--

                isUpdatingText = true
                etQty.setText(qty.toString())
                etQty.setSelection(etQty.text?.length ?: 0)
                isUpdatingText = false

                updateTotal()
            }
        }

        btnPlus.setOnClickListener {
            qty++

            isUpdatingText = true
            etQty.setText(qty.toString())
            etQty.setSelection(etQty.text?.length ?: 0)
            isUpdatingText = false

            updateTotal()
        }

        etQty.addTextChangedListener(object : TextWatcher {

            override fun afterTextChanged(s: Editable?) {

                if (isUpdatingText) return

                val enteredQty = s.toString()
                    .toIntOrNull()
                    ?.coerceAtLeast(1)

                if (enteredQty != null) {
                    qty = enteredQty
                    updateTotal()
                }
            }

            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) = Unit

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) = Unit
        })

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add To Cart")
            .setView(dialogView)
            .setPositiveButton("Add To Cart") { _, _ ->

                val finalSubtotal = calculateDiscountedTotal()

                val existingIndex = cart.indexOfFirst {
                    it.itemId == item.itemId
                }

                val unitType =
                    if (isCake) "kg" else "piece"

                if (existingIndex >= 0) {

                    val oldItem = cart[existingIndex]
                    val newQty = oldItem.quantity + qty

                    val updatedOriginal =
                        newQty * item.pricePerPiece

                    val updatedDiscountPercent =
                        when {
                            newQty >= 5 -> 12
                            newQty == 4 -> 9
                            newQty == 3 -> 7
                            else -> 0
                        }

                    val updatedDiscountAmount =
                        updatedOriginal * updatedDiscountPercent / 100.0

                    val updatedSubtotal =
                        updatedOriginal - updatedDiscountAmount

                    cart[existingIndex] = oldItem.copy(
                        quantity = newQty,
                        subtotal = updatedSubtotal
                    )

                } else {

                    cart.add(
                        OrderItem(
                            itemId = item.itemId,
                            itemName = item.name,
                            quantity = qty,
                            unitType = unitType,
                            pricePerPiece = item.pricePerPiece,
                            subtotal = finalSubtotal
                        )
                    )
                }

                updateCartBadge()

                Toast.makeText(
                    requireContext(),
                    "${item.name} added to cart",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    private fun updateCartBadge() {

        val totalItems = cart.sumOf { it.quantity }

        binding.btnViewCart.text =
            if (totalItems > 0) {
                "View Cart ($totalItems)"
            } else {
                "View Cart"
            }
    }

    private fun showRoomNumberDialog() {

        if (cart.isEmpty()) {
            Toast.makeText(
                requireContext(),
                "Cart is empty",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val input = EditText(requireContext())
        input.hint = "Enter Room Number"

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Room Number Required")
            .setView(input)
            .setPositiveButton("Continue") { _, _ ->

                val room = input.text.toString().trim()

                if (room.isEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        "Room number required",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setPositiveButton
                }

                roomNumberForOrder = room

                val hasCake = cart.any {
                    it.unitType == "kg"
                }

                if (hasCake) {
                    askBirthdayName()
                } else {
                    showCartDialog()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun askBirthdayName() {

        val input = EditText(requireContext())
        input.hint = "Enter Birthday Boy Name"

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Cake Customization")
            .setView(input)
            .setPositiveButton("Continue") { _, _ ->

                val name = input.text.toString().trim()

                if (name.isEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        "Birthday boy name required",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setPositiveButton
                }

                birthdayBoyName = name
                showCartDialog()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCartDialog() {

        val total = cart.sumOf { it.subtotal }

        val itemList = cart.joinToString("\n") {
            "${it.itemName} x${it.quantity} ${it.unitType} = ₹${
                String.format("%,.2f", it.subtotal)
            }"
        }

        val message =
            "$itemList\n\n" +
                    "Room No: $roomNumberForOrder\n" +
                    "Total: ₹${String.format("%,.2f", total)}"

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Confirm Order")
            .setMessage(message)
            .setPositiveButton("Confirm Ordering") { _, _ ->
                placeOrder()
            }
            .setNeutralButton("Clear Cart") { _, _ ->
                cart.clear()
                updateCartBadge()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun placeOrder() {

        binding.progressBar.visibility = View.VISIBLE

        val total = cart.sumOf { it.subtotal }

        val order = FoodOrder(
            userId = FirebaseHelper.currentUid ?: "",
            userEmail = currentUser?.email ?: "",
            userName = currentUser?.fullName ?: "",
            roomNumber = roomNumberForOrder,
            birthdayName = birthdayBoyName,
            items = cart.toList(),
            totalCost = total,
            status = "placed"
        )

        FirebaseHelper.saveFoodOrder(order) { success, orderId ->

            binding.progressBar.visibility = View.GONE

            if (success) {

                val email = currentUser?.email ?: ""

                if (email.isNotEmpty()) {
                    lifecycleScope.launch {
                        EmailHelper.sendOrderConfirmation(
                            toEmail = email,
                            guestName = currentUser?.fullName ?: "",
                            orderId = orderId,
                            items = cart.toList(),
                            totalCost = total
                        )
                    }
                }

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Order Placed Successfully")
                    .setMessage(
                        "Order ID: $orderId\n" +
                                "Room: $roomNumberForOrder\n" +
                                "Total: ₹${String.format("%,.2f", total)}"
                    )
                    .setPositiveButton("OK") { _, _ ->
                        cart.clear()
                        birthdayBoyName = ""
                        roomNumberForOrder = ""
                        updateCartBadge()
                    }
                    .setCancelable(false)
                    .show()

            } else {
                Toast.makeText(
                    requireContext(),
                    "Order failed",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}