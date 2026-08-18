package com.voicehelp.commands.actions

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.voicehelp.R

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val label = intent.getStringExtra("label") ?: context.getString(R.string.notif_timer_title)
        Notifications.show(context, context.getString(R.string.notif_timer_title), label)
    }
}