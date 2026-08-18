package com.voicehelp.commands.actions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.voicehelp.R
import com.voicehelp.commands.ContactsResolver

class CallAction(private val context: Context) {

    fun execute(target: String): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return context.getString(R.string.reply_no_permission, context.getString(R.string.perm_call))
        }
        val number = ContactsResolver.resolve(context, target)
            ?: return context.getString(R.string.reply_cant_find_contact, target)
        try {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            return context.getString(R.string.reply_cant_find_contact, target)
        }
        return context.getString(R.string.reply_calling, target)
    }
}