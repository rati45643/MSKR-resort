package com.resort.app.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.datepicker.CalendarConstraints
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.resort.app.R
import com.resort.app.databinding.FragmentRoomBookingBinding
import com.resort.app.models.RoomBooking
import com.resort.app.utils.FirebaseHelper
import java.util.*

class RoomBookingFragment : Fragment() {

    private var _binding: FragmentRoomBookingBinding? = null
    private val binding get() = _binding!!

    private var selectedRooms = 1
    private var selectedDays = 0

    private var startDate: Long = 0
    private var endDate: Long = 0

    private val selectedRoomNumbers = mutableListOf<Int>()
    private var availableRoomsList = listOf<Int>()

    private var bookingListener: ListenerRegistration? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRoomBookingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        setupSpinners()
        setupListeners()
        loadAvailableRooms()
        loadBookingDetails()
        updateTotalPrice()


    }

    // ---------------- DATE RANGE PICKER ----------------
    private fun openDateRangePicker() {

        val constraints = CalendarConstraints.Builder()
            .setStart(System.currentTimeMillis() + (24 * 60 * 60 * 1000)) // tomorrow
            .build()

        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Select Stay Dates")
            .setCalendarConstraints(constraints)
            .build()

        picker.show(parentFragmentManager, "DATE_RANGE")

        picker.addOnPositiveButtonClickListener { selection ->

            startDate = selection.first!!
            endDate = selection.second!!

            val diff = endDate - startDate
            selectedDays = (diff / (1000 * 60 * 60 * 24)).toInt()

            if (selectedDays <= 0) {
                Toast.makeText(requireContext(), "Invalid date range", Toast.LENGTH_SHORT).show()
                return@addOnPositiveButtonClickListener
            }

            binding.btnSelectDates.text =
                "Stay: ${Date(startDate)} → ${Date(endDate)}"

            updateTotalPrice()
        }
    }

    // ---------------- SPINNERS ----------------
    private fun setupSpinners() {

        val adapter = object : ArrayAdapter<Int>(
            requireContext(),
            android.R.layout.simple_spinner_item,
            (1..5).toList()
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                (view as TextView).setTextColor(Color.BLACK)
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                (view as TextView).setTextColor(Color.BLACK)
                view.setBackgroundColor(Color.WHITE)
                return view
            }
        }

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        binding.spinnerRooms.adapter = adapter
        binding.spinnerRooms.setPopupBackgroundResource(android.R.color.white)

        binding.spinnerRooms.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateTotalPrice()
                generateRoomSelectors()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    // ---------------- LISTENERS ----------------
    private fun setupListeners() {

        binding.btnSelectDates.setOnClickListener {
            openDateRangePicker()
        }

        binding.chipGroupBudget.setOnCheckedStateChangeListener { _, _ ->
            updateTotalPrice()
            loadAvailableRooms()
        }

        binding.btnBookNow.setOnClickListener { validateAndBook() }

    }

    // ---------------- VALIDATE & BOOK ----------------
    private fun validateAndBook() {

        selectedRooms = binding.spinnerRooms.selectedItem.toString().toInt()

        if (startDate == 0L || endDate == 0L) {
            Toast.makeText(requireContext(), "Select dates first", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedRoomNumbers.size != selectedRooms) {
            Toast.makeText(requireContext(), "Select all rooms", Toast.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Confirm Booking")
            .setMessage("Rooms: $selectedRoomNumbers\nDays: $selectedDays")
            .setPositiveButton("Confirm") { _, _ -> bookWithValidation() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------------- OVERLAP CHECK ----------------
    private fun bookWithValidation() {

        val db = Firebase.firestore

        var conflictFound = false
        var conflictMessage = ""
        var checkedCount = 0

        selectedRoomNumbers.forEach { room ->

            db.collection("bookings")
                .whereEqualTo("roomNumber", room)
                .get()
                .addOnSuccessListener { snapshot ->

                    val bookings = snapshot.toObjects(RoomBooking::class.java)

                    bookings.forEach { booking ->

                        // ✅ BLOCK if NOT cancelled or checked_out
                        if (booking.status != "cancelled" && booking.status != "checked_out") {

                            val isOverlap =
                                (startDate <= booking.checkOutDate &&
                                        endDate >= booking.checkInDate)

                            if (isOverlap) {

                                conflictMessage =
                                    "Room $room not available\n" +
                                            "${Date(booking.checkInDate)} → ${Date(booking.checkOutDate)}\n" +
                                            "Status: ${booking.status}"

                                conflictFound = true
                                return@forEach
                            }
                        }
                    }

                    checkedCount++

                    // ✅ AFTER CHECKING ALL ROOMS
                    if (checkedCount == selectedRoomNumbers.size) {

                        if (conflictFound) {

                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Room Blocked ❌")
                                .setMessage(conflictMessage)
                                .setPositiveButton("OK", null)
                                .show()

                            return@addOnSuccessListener
                        }

                        // ✅ SAFE TO BOOK
                        saveBookingTransaction()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(
                        requireContext(),
                        "Error checking availability",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }
    // ---------------- SAVE BOOKING ----------------
    private fun saveBookingTransaction() {

        val db = Firebase.firestore

        db.runTransaction { transaction ->

            selectedRoomNumbers.forEach { room ->

                val docRef = db.collection("bookings").document()

                val booking = RoomBooking(
                    bookingId = docRef.id,
                    userId = FirebaseHelper.currentUid ?: "",
                    userName = "Guest",
                    roomNumber = room,
                    numberOfRooms = 1,
                    numberOfDays = selectedDays,
                    budgetRange = getBudgetRange(),
                    totalCost = RoomBooking.calculateCost(1, selectedDays, getBudgetRange()),
                    checkInDate = startDate,
                    checkOutDate = endDate,
                    status = "confirmed",
                    createdAt = System.currentTimeMillis()
                )

                transaction.set(docRef, booking)
            }
        }.addOnSuccessListener {
            Toast.makeText(requireContext(), "Booking successful ✅", Toast.LENGTH_SHORT).show()
        }
    }
    // ---------------- REAL-TIME BOOKINGS ----------------
    private fun loadBookingDetails() {

        val uid = FirebaseHelper.currentUid ?: return

        bookingListener?.remove()

        bookingListener = FirebaseHelper.db.collection("bookings")
            .whereEqualTo("userId", uid)
            .addSnapshotListener { snapshot, _ ->

                // 🔄 Clear old UI
                binding.layoutBookingHistory.removeAllViews()

                // ❌ No bookings
                if (snapshot == null || snapshot.isEmpty) {
                    val tv = TextView(requireContext())
                    tv.text = "No bookings"
                    tv.setTextColor(Color.BLACK)
                    binding.layoutBookingHistory.addView(tv)
                    return@addSnapshotListener
                }

                val bookings = snapshot.toObjects(RoomBooking::class.java)

                // 🔁 Loop through bookings
                bookings.forEach { booking ->

                    val container = LinearLayout(requireContext())
                    container.orientation = LinearLayout.VERTICAL
                    container.setPadding(16, 16, 16, 16)

                    val info = TextView(requireContext())
                    info.setTextColor(Color.BLACK)

                    val checkIn = Date(booking.checkInDate)
                    val checkOut = Date(booking.checkOutDate)

                    info.text =
                        "Room: ${booking.roomNumber}\n" +
                                "Check-in: $checkIn\n" +
                                "Check-out: $checkOut\n" +
                                "Status: ${booking.status}"

                    container.addView(info)

                    // ✅ SHOW CANCEL BUTTON ONLY IF CONFIRMED
                    if (booking.status.equals("confirmed", true)) {

                        val cancelBtn = Button(requireContext())
                        cancelBtn.text = "Cancel Booking"

                        cancelBtn.setOnClickListener {
                            cancelSingleBooking(booking.bookingId)
                        }

                        container.addView(cancelBtn)
                    }

                    binding.layoutBookingHistory.addView(container)
                }
            }
    }
    // ---------------- CANCEL ----------------
    private fun cancelSingleBooking(bookingId: String) {

        val docRef = FirebaseHelper.db.collection("bookings").document(bookingId)

        docRef.get().addOnSuccessListener { doc ->

            val status = doc.getString("status")

            // 🔐 FINAL CHECK
            if (status != "confirmed") {
                Toast.makeText(requireContext(), "Already checked-in ❌", Toast.LENGTH_LONG).show()
                return@addOnSuccessListener
            }

            docRef.update("status", "cancelled")
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Cancelled ✅", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // ---------------- ROOM LIST ----------------
    private fun loadAvailableRooms() {

        availableRoomsList = when (getBudgetRange()) {
            "low" -> (1..200).toList()
            "medium" -> (201..260).toList()
            "high" -> (270..300).toList()
            else -> emptyList()
        }

        generateRoomSelectors()
    }

    // ---------------- ROOM SELECTORS ----------------
    private fun generateRoomSelectors() {

        binding.layoutRoomSelectors.removeAllViews()
        selectedRoomNumbers.clear()

        val count = binding.spinnerRooms.selectedItem.toString().toInt()

        repeat(count) { index ->

            val spinner = Spinner(requireContext())

            // ✅ FIX: custom adapter with BLACK text
            val adapter = object : ArrayAdapter<Int>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                availableRoomsList
            ) {

                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent)
                    (view as TextView).setTextColor(Color.BLACK) // ✅ visible
                    return view
                }

                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getDropDownView(position, convertView, parent)
                    (view as TextView).setTextColor(Color.BLACK) // ✅ visible
                    view.setBackgroundColor(Color.WHITE) // ✅ force white bg
                    return view
                }
            }

            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

            spinner.adapter = adapter

            // ✅ IMPORTANT: force popup background
            spinner.setPopupBackgroundResource(android.R.color.white)

            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {

                    val room = availableRoomsList[pos]

                    if (selectedRoomNumbers.contains(room)) {
                        Toast.makeText(requireContext(), "Duplicate room!", Toast.LENGTH_SHORT).show()
                        return
                    }

                    if (selectedRoomNumbers.size > index)
                        selectedRoomNumbers[index] = room
                    else
                        selectedRoomNumbers.add(room)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

            binding.layoutRoomSelectors.addView(spinner)
        }
    }
    private fun getBudgetRange(): String {
        return when {
            binding.chipLow.isChecked -> "low"
            binding.chipHigh.isChecked -> "high"
            else -> "medium"
        }
    }

    private fun updateTotalPrice() {

        val total = RoomBooking.calculateCost(
            binding.spinnerRooms.selectedItem.toString().toInt(),
            selectedDays,
            getBudgetRange()
        )

        binding.tvTotalCost.text = "Total ₹$total"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bookingListener?.remove()
        _binding = null
    }
}