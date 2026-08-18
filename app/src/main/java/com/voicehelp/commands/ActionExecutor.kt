package com.voicehelp.commands

import android.content.Context
import com.voicehelp.commands.actions.AlarmAction
import com.voicehelp.commands.actions.CallAction
import com.voicehelp.commands.actions.FlashlightAction
import com.voicehelp.commands.actions.OpenAppAction
import com.voicehelp.commands.actions.PhotoAction
import com.voicehelp.commands.actions.SearchAction
import com.voicehelp.commands.actions.SmsAction
import com.voicehelp.commands.actions.TimeAction
import com.voicehelp.commands.actions.TimerAction

class ActionExecutor(private val context: Context) {

    suspend fun execute(command: Command): String = when (command.action) {
        CommandAction.CALL -> CallAction(context).execute(command.param)
        CommandAction.SMS -> {
            val parts = command.param.split('\u0000', limit = 2)
            val target = parts.getOrElse(0) { "" }
            val message = parts.getOrElse(1) { "" }
            SmsAction(context).execute(target, message)
        }
        CommandAction.TIMER -> {
            val seconds = command.param.toIntOrNull()
            val secondsParam = when {
                seconds == null -> command.param
                command.source == "llm" -> (seconds * 60).toString()
                else -> seconds.toString()
            }
            TimerAction(context).execute(secondsParam)
        }
        CommandAction.ALARM -> AlarmAction(context).execute(command.param)
        CommandAction.FLASHLIGHT -> FlashlightAction(context).execute()
        CommandAction.PHOTO -> PhotoAction(context).execute()
        CommandAction.OPEN_APP -> OpenAppAction(context).execute(command.param)
        CommandAction.SEARCH -> SearchAction(context).search(command.param)
            ?: context.getString(com.voicehelp.R.string.reply_search_nothing, command.param)
        CommandAction.TIME -> TimeAction.execute()
        CommandAction.GREETING -> context.getString(com.voicehelp.R.string.reply_hello)
        CommandAction.THANKS -> context.getString(com.voicehelp.R.string.reply_thanks)
        CommandAction.HELP -> context.getString(com.voicehelp.R.string.reply_help)
        CommandAction.REPLY, CommandAction.NONE -> command.reply
    }
}