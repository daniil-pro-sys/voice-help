package com.voicehelp.commands.actions

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.voicehelp.R

object Notifications {
    const val CHANNEL_ID = "voicehelp_alarms"

    fun show(context: Context, title: String, text: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_timer),
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify((System.currentTimeMillis() / 1000).toInt(), notification)
    }
}