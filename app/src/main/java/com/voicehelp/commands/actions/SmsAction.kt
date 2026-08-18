package com.voicehelp.commands.actions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.voicehelp.R
import com.voicehelp.commands.ContactsResolver

class SmsAction(private val context: Context) {

    fun execute(target: String, message: String): String {
        if (message.isBlank()) {
            return context.getString(R.string.reply_sms_no_text)
        }
        if (target.isBlank()) {
            return context.getString(R.string.reply_sms_no_target)
        }
        val number = ContactsResolver.resolve(context, target)
            ?: return context.getString(R.string.reply_cant_find_contact, target)

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                SmsManager.getDefault().sendTextMessage(number, null, message, null, null)
                return context.getString(R.string.reply_sms_sent, target)
            } catch (_: Exception) {
            }
        }
        try {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number"))
                .putExtra("sms_body", message)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return context.getString(R.string.reply_sms_sent, target)
        } catch (_: Exception) {
        }
        return context.getString(R.string.reply_no_permission, context.getString(R.string.perm_sms))
    }
}