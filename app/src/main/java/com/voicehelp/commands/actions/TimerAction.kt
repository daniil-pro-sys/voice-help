package com.voicehelp.commands.actions

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.voicehelp.R

class TimerAction(private val context: Context) {

    fun execute(secondsParam: String): String {
        val seconds = secondsParam.toIntOrNull()
            ?: return context.getString(R.string.reply_timer_bad_format)
        if (seconds <= 0 || seconds > 24 * 3600) {
            return context.getString(R.string.reply_timer_bad_format)
        }
        val label = formatDuration(context, seconds)
        val triggerAt = System.currentTimeMillis() + seconds * 1000L

        val intent = Intent(context, TimerReceiver::class.java).putExtra("label", label)
        val pending = PendingIntent.getBroadcast(
            context,
            (triggerAt / 1000).toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        try {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
        return context.getString(R.string.reply_timer_set, label)
    }

    companion object {
        fun formatDuration(context: Context, seconds: Int): String {
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            val s = seconds % 60
            return when {
                h > 0 && m > 0 -> "${h} ч ${m} мин"
                h > 0 -> "${h} ч"
                m > 0 -> "$m мин"
                else -> "$s сек"
            }
        }
    }
}