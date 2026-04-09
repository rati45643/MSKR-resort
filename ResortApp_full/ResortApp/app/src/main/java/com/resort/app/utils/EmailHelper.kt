package com.resort.app.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

object EmailHelper {

    // ⚠️ Replace with your Gmail App Password (not regular password)
    // Enable 2FA on Gmail → App Passwords → create one for "Mail"
    private const val SENDER_EMAIL    = "your.resort.email@gmail.com"
    private const val SENDER_PASSWORD = "your_app_password_here"

    suspend fun sendBookingConfirmation(
        toEmail: String,
        guestName: String,
        bookingId: String,
        numberOfRooms: Int,
        numberOfDays: Int,
        budgetRange: String,
        checkIn: String,
        checkOut: String,
        totalCost: Double,
        discountApplied: Boolean
    ) = withContext(Dispatchers.IO) {
        try {
            val props = Properties().apply {
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.host", "smtp.gmail.com")
                put("mail.smtp.port", "587")
                put("mail.smtp.ssl.trust", "smtp.gmail.com")
            }

            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication() =
                    PasswordAuthentication(SENDER_EMAIL, SENDER_PASSWORD)
            })

            val roomType = "1 Bedroom + 1 Hall + 1 Bathroom"
            val discountNote = if (discountApplied) "\n   🎉 10% discount applied for booking $numberOfRooms rooms!" else ""

            val body = """
Dear $guestName,

Your room booking at Palm Grove Resort has been confirmed! 🌴

═══════════════════════════════════════
        BOOKING CONFIRMATION
═══════════════════════════════════════

Booking ID     : $bookingId
Guest Name     : $guestName
Room Type      : $roomType
No. of Rooms   : $numberOfRooms
No. of Days    : $numberOfDays
Budget Range   : ${budgetRange.replaceFirstChar { it.uppercase() }}
Check-In       : $checkIn
Check-Out      : $checkOut$discountNote
─────────────────────────────────────
TOTAL AMOUNT   : ₹${String.format("%,.2f", totalCost)}
═══════════════════════════════════════

For any assistance, please contact our helpline:
📞 +91 98765 43210
📧 support@palmgroveresort.com

We look forward to welcoming you!

Warm regards,
Palm Grove Resort Team
            """.trimIndent()

            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(SENDER_EMAIL, "Palm Grove Resort"))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail))
                subject = "✅ Booking Confirmed – Palm Grove Resort | #$bookingId"
                setText(body)
            }

            Transport.send(message)
            Log.d("EmailHelper", "Booking confirmation sent to $toEmail")
        } catch (e: Exception) {
            Log.e("EmailHelper", "Failed to send email: ${e.message}")
        }
    }

    suspend fun sendOrderConfirmation(
        toEmail: String,
        guestName: String,
        orderId: String,
        items: List<com.resort.app.models.OrderItem>,
        totalCost: Double
    ) = withContext(Dispatchers.IO) {
        try {
            val props = Properties().apply {
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.host", "smtp.gmail.com")
                put("mail.smtp.port", "587")
                put("mail.smtp.ssl.trust", "smtp.gmail.com")
            }

            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication() =
                    PasswordAuthentication(SENDER_EMAIL, SENDER_PASSWORD)
            })

            val itemLines = items.joinToString("\n") { item ->
                "   ${item.itemName.padEnd(25)} x${item.quantity}  ₹${String.format("%,.2f", item.subtotal)}"
            }

            val body = """
Dear $guestName,

Your food order at Palm Grove Resort has been placed! 🍽️

═══════════════════════════════════════
        ORDER CONFIRMATION
═══════════════════════════════════════

Order ID : $orderId

ITEMS ORDERED:
$itemLines
─────────────────────────────────────
TOTAL    : ₹${String.format("%,.2f", totalCost)}
═══════════════════════════════════════

Your order is being prepared. 
Estimated delivery: 20–30 minutes.

Enjoy your meal!

Palm Grove Resort Kitchen
            """.trimIndent()

            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(SENDER_EMAIL, "Palm Grove Resort"))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail))
                subject = "🍽️ Order Placed – Palm Grove Resort | #$orderId"
                setText(body)
            }

            Transport.send(message)
        } catch (e: Exception) {
            Log.e("EmailHelper", "Failed to send order email: ${e.message}")
        }
    }
}
