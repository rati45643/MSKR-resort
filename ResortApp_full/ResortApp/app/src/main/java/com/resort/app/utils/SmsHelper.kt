package com.resort.app.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import android.os.Build

object SmsHelper {

    const val HELPLINE_NUMBER = "+919019542275"

    /**
     * Sends an SMS to the resort helpline with the issue details.
     * Falls back to opening the SMS app if direct send fails.
     */
    fun sendIssueToHelpline(
        context: Context,
        guestName: String,
        roomNumber: String,
        issueType: String,
        description: String
    ) {
        val message = """
[Palm Grove Resort - Guest Issue]
Guest   : $guestName
Room    : $roomNumber
Type    : ${issueType.replaceFirstChar { it.uppercase() }}
Details : $description
Time    : ${java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())}
        """.trimIndent()

        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(HELPLINE_NUMBER, null, parts, null, null)
        } catch (e: Exception) {
            // Fallback: open SMS app pre-filled
            openSmsApp(context, message)
        }
    }

    private fun openSmsApp(context: Context, body: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:$HELPLINE_NUMBER")).apply {
            putExtra("sms_body", body)
        }
        context.startActivity(intent)
    }
}
