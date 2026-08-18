package com.voicehelp.commands.actions

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeAction {
    fun execute(): String {
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        return "Сейчас $time"
    }
}