package com.voicehelp.settings

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.voicehelp.R
import com.voicehelp.assistant.VoskModelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ModelDownloadService : Service() {

    companion object {
        const val EXTRA_LANG = "lang"
        const val ACTION_MODEL_PROGRESS = "com.voicehelp.intent.MODEL_PROGRESS"
        const val ACTION_MODEL_FINISHED = "com.voicehelp.intent.MODEL_FINISHED"

        private const val CHANNEL_ID = "model_download"
        private const val NOTIF_ID = 42

        private val jobs = java.util.Collections.synchronizedMap(mutableMapOf<String, Job>())

        fun isDownloading(lang: String): Boolean = jobs.containsKey(lang)

        fun start(context: Context, lang: String) {
            val intent = Intent(context, ModelDownloadService::class.java)
                .putExtra(EXTRA_LANG, lang)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val lang = intent?.getStringExtra(EXTRA_LANG)
        if (lang != null && !isDownloading(lang)) {
            val ctx = this
            val job = scope.launch {
                try {
                    VoskModelManager.download(ctx, lang) { percent ->
                        updateNotification(lang, percent)
                        sendProgress(lang, percent)
                    }
                } catch (e: Exception) {
                    sendFinished(lang, false, e.message)
                } finally {
                    jobs.remove(lang)
                    stopIfDone()
                }
            }
            jobs[lang] = job
            startForegroundCompat(0)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundCompat(percent: Int) {
        val notification = buildNotification(percent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(percent: Int): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, ModelActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.model_download_notif_title))
            .setContentText(getString(R.string.model_status_downloading, percent))
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun updateNotification(lang: String, percent: Int) {
        if (isDownloading(lang)) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIF_ID, buildNotification(percent))
        }
    }

    private fun sendProgress(lang: String, percent: Int) {
        sendBroadcast(
            Intent(ACTION_MODEL_PROGRESS)
                .setPackage(packageName)
                .putExtra(EXTRA_LANG, lang)
                .putExtra("percent", percent)
        )
    }

    private fun sendFinished(lang: String, ok: Boolean, error: String?) {
        sendBroadcast(
            Intent(ACTION_MODEL_FINISHED)
                .setPackage(packageName)
                .putExtra(EXTRA_LANG, lang)
                .putExtra("ok", ok)
                .putExtra("error", error)
        )
    }

    private fun stopIfDone() {
        if (jobs.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.model_download_notif_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }
}