package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

/**
 * BroadcastReceiver for incoming SMS messages.
 * Automatically forwards payment messages (e.g. bKash, Nagad) to the WebView.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (!Config.ENABLE_SMS_INTEGRATION) return
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) return

            val fullBodyBuilder = StringBuilder()
            var sender = ""

            for (sms in messages) {
                if (sms != null) {
                    sender = sms.displayOriginatingAddress ?: ""
                    fullBodyBuilder.append(sms.displayMessageBody ?: "")
                }
            }

            val body = fullBodyBuilder.toString()
            if (body.isBlank()) return

            // Check if filtering is active (only bKash and Nagad payment SMS are accepted)
            val lowerSender = sender.lowercase()
            val lowerBody = body.lowercase()
            val isFromProvider = lowerSender.contains("bkash") || lowerSender.contains("nagad")
            val matchesKeyword = Config.SMS_FILTER_KEYWORDS.isEmpty() || Config.SMS_FILTER_KEYWORDS.any { keyword ->
                lowerBody.contains(keyword.lowercase())
            }

            if (!isFromProvider && !matchesKeyword) {
                if (Config.DEBUG_MODE) {
                    Log.d("SmsReceiver", "SMS discarded (not a bKash/Nagad payment SMS from $sender)")
                }
                return
            }

            if (Config.DEBUG_MODE) {
                Log.d("SmsReceiver", "Payment SMS received from $sender: $body")
            }

            // Dispatch to active MainActivity instance
            listener?.invoke(sender, body)

        } catch (e: Exception) {
            Log.e("SmsReceiver", "Error processing incoming SMS: ${e.message}")
        }
    }

    companion object {
        var listener: ((sender: String, message: String) -> Unit)? = null
    }
}
