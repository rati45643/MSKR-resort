package com.resort.app.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.resort.app.databinding.FragmentIssuesBinding
import com.resort.app.models.IssueReport
import com.resort.app.utils.FirebaseHelper
import com.resort.app.utils.SmsHelper

class IssuesFragment : Fragment() {

    private var _binding: FragmentIssuesBinding? = null
    private val binding get() = _binding!!

    private var selectedIssueType = ""
    private var currentUser: com.resort.app.models.User? = null

    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) submitIssue()
        else {
            Toast.makeText(requireContext(), "Opening SMS app...", Toast.LENGTH_SHORT).show()
            submitIssueWithFallback()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIssuesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadUser()
        setupIssueTypes()

        binding.btnSubmitIssue.setOnClickListener {
            validateAndSubmit()
        }
    }

    private fun loadUser() {
        val uid = FirebaseHelper.currentUid ?: return

        FirebaseHelper.getUser(uid) { user ->
            currentUser = user
            binding.etGuestName.setText(user?.fullName ?: "")
        }
    }

    private fun setupIssueTypes() {

        val issueTypes = listOf(
            "water" to "Water Problem",
            "electricity" to "Electricity",
            "cleaning" to "Room Cleaning",
            "laundry" to "Laundry",
            "ac" to "AC / Heating",
            "wifi" to "Wi-Fi Issue",
            "plumbing" to "Plumbing",
            "other" to "Other"
        )

        issueTypes.forEach { (type, label) ->
            val chip = Chip(requireContext()).apply {
                text = label
                isCheckable = true
                tag = type
                chipCornerRadius = 24f
            }

            chip.setOnCheckedChangeListener { _, checked ->
                if (checked) selectedIssueType = type
            }

            binding.chipGroupIssues.addView(chip)
        }
    }

    private fun validateAndSubmit() {

        val name = binding.etGuestName.text.toString().trim()
        val room = binding.etRoomNumber.text.toString().trim()
        val desc = binding.etDescription.text.toString().trim()

        when {
            name.isEmpty() -> {
                binding.etGuestName.error = "Required"
                return
            }

            room.isEmpty() -> {
                binding.etRoomNumber.error = "Required"
                return
            }

            selectedIssueType.isEmpty() -> {
                Toast.makeText(requireContext(), "Select an issue type", Toast.LENGTH_SHORT).show()
                return
            }
        }

        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            submitIssue()
        } else {
            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
        }
    }

    private fun submitIssue() {

        val name = binding.etGuestName.text.toString().trim()
        val room = binding.etRoomNumber.text.toString().trim()
        val desc = binding.etDescription.text.toString().trim()

        binding.progressBar.visibility = View.VISIBLE
        binding.btnSubmitIssue.isEnabled = false

        val issue = IssueReport(
            userId = FirebaseHelper.currentUid ?: "",
            userEmail = currentUser?.email ?: FirebaseHelper.currentEmail ?: "",
            userName = name,
            roomNumber = room,
            issueType = selectedIssueType,
            description = desc.ifEmpty { "No additional description" },
            status = "open"
        )

        FirebaseHelper.saveIssue(issue) {

            // ✅ SEND SMS TO NEW HELPLINE
            SmsHelper.sendIssueToHelpline(
                context = requireContext(),
                guestName = name,
                roomNumber = room,
                issueType = selectedIssueType,
                description = desc.ifEmpty { "No additional description" }
            )

            binding.progressBar.visibility = View.GONE
            binding.btnSubmitIssue.isEnabled = true

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Issue Reported ✅")
                .setMessage(
                    "Your issue has been reported successfully.\n\n" +
                            "Our team will reach your room shortly.\n\n" +
                            "Helpline: +91 9019542275"
                )
                .setPositiveButton("OK") { _, _ ->
                    resetForm()
                }
                .show()
        }
    }

    private fun submitIssueWithFallback() {

        val name = binding.etGuestName.text.toString().trim()
        val room = binding.etRoomNumber.text.toString().trim()
        val desc = binding.etDescription.text.toString().trim()

        SmsHelper.sendIssueToHelpline(
            context = requireContext(),
            guestName = name,
            roomNumber = room,
            issueType = selectedIssueType,
            description = desc.ifEmpty { "No additional description" }
        )
    }

    private fun resetForm() {
        binding.etDescription.text?.clear()
        binding.etRoomNumber.text?.clear()
        binding.chipGroupIssues.clearCheck()
        selectedIssueType = ""
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}