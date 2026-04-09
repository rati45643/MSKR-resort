package com.resort.app.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.resort.app.R
import com.resort.app.activities.LoginActivity
import com.resort.app.databinding.FragmentProfileBinding
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadUserData()
        setupButtons()
    }

    private fun loadUserData() {
        val user = auth.currentUser

        if (user == null) {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        binding.tvEmail.text = user.email

        // 🔥 REAL-TIME LISTENER (AUTO UPDATE)
        db.collection("users").document(user.uid)
            .addSnapshotListener { doc, _ ->
                if (doc != null && doc.exists()) {

                    val name = doc.getString("name") ?: "User"
                    val phone = doc.getString("phone") ?: "No phone"
                    val image = doc.getString("image") ?: ""

                    binding.tvName.text = name
                    binding.tvPhone.text = phone   // ✅ SHOW BELOW IMAGE

                    binding.etName.setText(name)
                    binding.etPhone.setText(phone)

                    if (image.isNotEmpty()) {
                        Glide.with(this)
                            .load(image)
                            .placeholder(R.drawable.bg_profile)
                            .into(binding.profileImage)
                    }
                } else {
                    Toast.makeText(requireContext(), "User data not found", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun setupButtons() {

        // SAVE NAME
        binding.btnSaveName.setOnClickListener {
            val name = binding.etName.text.toString()
            val uid = auth.currentUser?.uid ?: return@setOnClickListener

            db.collection("users").document(uid)
                .update("name", name)
                .addOnSuccessListener {
                    binding.tvName.text = name
                    Toast.makeText(requireContext(), "Name updated", Toast.LENGTH_SHORT).show()
                }
        }

        // SAVE PHONE
        binding.btnSavePhone.setOnClickListener {
            val phone = binding.etPhone.text.toString()
            val uid = auth.currentUser?.uid ?: return@setOnClickListener

            db.collection("users").document(uid)
                .update("phone", phone)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Phone updated", Toast.LENGTH_SHORT).show()
                    binding.tvPhone.text = phone
                }
        }

        // ✅ ONLY KEEP PASSWORD CHANGE
        binding.btnChangePassword.setOnClickListener {

            val currentPass = binding.etCurrentPassword.text.toString().trim()
            val newPass = binding.etNewPassword.text.toString().trim()
            val user = auth.currentUser

            // VALIDATION
            if (currentPass.isEmpty() || newPass.isEmpty()) {
                Toast.makeText(requireContext(), "Enter both passwords", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newPass.length < 6) {
                Toast.makeText(requireContext(), "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (user != null && user.email != null) {

                val credential = EmailAuthProvider
                    .getCredential(user.email!!, currentPass)

                user.reauthenticate(credential)
                    .addOnSuccessListener {

                        user.updatePassword(newPass)
                            .addOnSuccessListener {
                                Toast.makeText(requireContext(), "Password updated successfully", Toast.LENGTH_SHORT).show()

                                // OPTIONAL: clear fields
                                binding.etCurrentPassword.text?.clear()
                                binding.etNewPassword.text?.clear()
                            }
                            .addOnFailureListener {
                                Toast.makeText(requireContext(), "Update failed: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .addOnFailureListener {
                        Toast.makeText(requireContext(), "Wrong current password", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        // SAVE PROFILE IMAGE
        binding.btnSavePicture.setOnClickListener {
            val imageUrl = binding.etImageUrl.text.toString()
            val uid = auth.currentUser?.uid ?: return@setOnClickListener

            db.collection("users").document(uid)
                .update("image", imageUrl)
                .addOnSuccessListener {
                    Glide.with(this)
                        .load(imageUrl)
                        .into(binding.profileImage)

                    Toast.makeText(requireContext(), "Image updated", Toast.LENGTH_SHORT).show()
                }
        }

        // LOGOUT
        binding.btnLogout.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes") { _, _ ->
                    auth.signOut()
                    startActivity(Intent(requireContext(), LoginActivity::class.java))
                    requireActivity().finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // DELETE ACCOUNT
        binding.btnDelete.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Delete Account")
                .setMessage("This action cannot be undone!")
                .setPositiveButton("Delete") { _, _ ->
                    val user = auth.currentUser

                    user?.delete()?.addOnCompleteListener {
                        auth.signOut()
                        startActivity(Intent(requireContext(), LoginActivity::class.java))
                        requireActivity().finish()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}