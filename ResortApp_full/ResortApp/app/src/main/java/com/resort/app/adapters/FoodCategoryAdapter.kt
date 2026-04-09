package com.resort.app.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.resort.app.R
import com.resort.app.databinding.ItemFoodBinding
import com.resort.app.models.FoodItem

class FoodCategoryAdapter(
    private val items: List<FoodItem>,
    private val onAddClick: (FoodItem) -> Unit
) : RecyclerView.Adapter<FoodCategoryAdapter.FoodViewHolder>() {

    inner class FoodViewHolder(
        private val binding: ItemFoodBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FoodItem) {

            // Food texts
            binding.tvFoodName.text = item.name
            binding.tvFoodDescription.text = item.description

            binding.tvFoodPrice.text =
                "₹${String.format("%,.2f", item.pricePerPiece)}"

            binding.tvCategory.text =
                getCategoryLabel(item.category)

            // Smart Veg / Non-Veg Detection
            val nonVegKeywords = listOf(
                "chicken",
                "mutton",
                "fish",
                "prawn",
                "egg",
                "tandoori",
                "seafood",
                "meat",
                "tuna",
                "bbqwings",
                "calamari",
                "dragon",
                "kung pao",
                "beef"
            )

            val foodText =
                "${item.name} ${item.description} ${item.category}"
                    .lowercase()

            val isNonVeg = nonVegKeywords.any { keyword ->
                foodText.contains(keyword)
            }

            if (isNonVeg) {
                binding.tvVegTag.text = "🔴 Non-Veg"
                binding.tvVegTag.setTextColor(
                    Color.parseColor("#C62828")
                )
            } else {
                binding.tvVegTag.text = "🟢 Veg"
                binding.tvVegTag.setTextColor(
                    Color.parseColor("#2E7D32")
                )
            }

            // Load food image from online source
            Glide.with(binding.root.context)
                .load(item.imageUrl)
                .placeholder(R.drawable.ic_launcher_background)
                .error(R.drawable.ic_launcher_background)
                .centerCrop()
                .into(binding.ivFoodImage)

            // Add button click
            binding.btnAddToCart.setOnClickListener {
                onAddClick(item)
            }
        }

        private fun getCategoryLabel(category: String): String {
            return when (category) {
                "veg_main" -> "Veg"
                "non_veg_main" -> "Non-Veg"
                "chat" -> "Chats"
                "soup" -> "Soup"
                "ice_cream" -> "Ice Cream"
                "juice" -> "Juice"
                "milkshake" -> "Milkshake"
                "cakes" -> "Cakes"
                "birthday_cakes" -> "Birthday Cakes"
                else -> category
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): FoodViewHolder {

        val binding = ItemFoodBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return FoodViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: FoodViewHolder,
        position: Int
    ) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}