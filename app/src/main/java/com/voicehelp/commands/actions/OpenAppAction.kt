package com.voicehelp.commands.actions

import android.content.Context
import android.content.Intent
import com.voicehelp.R

class OpenAppAction(private val context: Context) {

    fun execute(appName: String): String {
        val pm = context.packageManager
        val query = appName.trim().lowercase()

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val matches = pm.queryIntentActivities(launcherIntent, 0)

        val match = matches.firstOrNull { info ->
            val label = info.loadLabel(pm).toString().lowercase()
            val pkg = info.activityInfo.packageName.lowercase()
            label.contains(query) || pkg.contains(query)
        }

        if (match == null) {
            return context.getString(R.string.reply_app_not_found, appName)
        }

        val launch = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
            ?: return context.getString(R.string.reply_app_not_found, appName)
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(launch)
        } catch (e: Exception) {
            return context.getString(R.string.reply_app_not_found, appName)
        }
        return context.getString(R.string.reply_opening, match.loadLabel(pm).toString())
    }
}