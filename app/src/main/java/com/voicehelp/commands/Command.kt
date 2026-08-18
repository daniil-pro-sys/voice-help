package com.voicehelp.commands

enum class CommandAction {
    CALL, SMS, TIMER, ALARM, FLASHLIGHT, PHOTO, OPEN_APP, SEARCH, TIME, HELP, GREETING, THANKS, REPLY, NONE
}

data class Command(
    val action: CommandAction,
    val param: String = "",
    val reply: String = "",
    val source: String = "local"
)