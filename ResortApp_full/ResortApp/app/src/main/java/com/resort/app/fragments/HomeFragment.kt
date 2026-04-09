package com.resort.app.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.resort.app.databinding.FragmentHomeBinding
import com.resort.app.utils.FirebaseHelper

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadResortInfo()
    }

    private fun loadResortInfo() {
        binding.progressBar.visibility = View.VISIBLE

        FirebaseHelper.getResortInfo { resort ->

            binding.progressBar.visibility = View.GONE

            // ✅ FORCE YOUR NAME (NO FIREBASE OVERRIDE)
            binding.tvResortName.text = "MSKR Resort"

            // ✅ FORCE YOUR DESCRIPTION
            binding.tvDescription.text =
                "Experience luxury and comfort at MSKR Resort, offering world-class hospitality, scenic views, and premium services for an unforgettable stay."

            // ✅ SAFE VALUES (avoid crash)
            val rating = resort?.rating ?: 4.7f
            val reviews = resort?.totalReviews ?: 1248

            binding.ratingBar.rating = rating
            binding.tvRating.text = "$rating ($reviews reviews)"

            // ✅ IMAGE (optional from Firebase)
            if (resort?.coverImageUrl?.isNotEmpty() == true) {
                Glide.with(requireContext())
                    .load(resort.coverImageUrl)
                    .into(binding.ivResortCover)
            }

            // ✅ Achievements (safe)
            val achievementsText = resort?.achievements
                ?.joinToString("\n") { "🏆 $it" }
                ?: "🏆 Best Luxury Resort\n🏆 Excellence in Hospitality\n🏆 Top Rated Resort"

            binding.tvAchievements.text = achievementsText

            // ✅ Amenities (safe)
            val amenitiesText = resort?.amenities
                ?.joinToString("\n") { "✓ $it" }
                ?: "✓ Swimming Pool\n✓ Free WiFi\n✓ Spa\n✓ Beach Access"

            binding.tvAmenities.text = amenitiesText
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}