package com.voicehelp.commands.actions

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.voicehelp.R
import java.util.Calendar

class AlarmAction(private val context: Context) {

    fun execute(timeParam: String): String {
        val parts = timeParam.split(":")
        if (parts.size != 2) return context.getString(R.string.reply_alarm_bad_format)
        val hour = parts[0].toIntOrNull() ?: return context.getString(R.string.reply_alarm_bad_format)
        val minute = parts[1].toIntOrNull() ?: return context.getString(R.string.reply_alarm_bad_format)
        if (hour !in 0..23 || minute !in 0..59) return context.getString(R.string.reply_alarm_bad_format)

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        val label = "%02d:%02d".format(hour, minute)
        val intent = Intent(context, AlarmReceiver::class.java).putExtra("label", label)
        val pending = PendingIntent.getBroadcast(
            context,
            (calendar.timeInMillis / 60000).toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        try {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pending)
            }
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pending)
        }
        return context.getString(R.string.reply_alarm_set, label)
    }
}