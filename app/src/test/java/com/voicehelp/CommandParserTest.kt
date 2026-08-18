package com.voicehelp

import com.voicehelp.commands.CommandAction
import com.voicehelp.commands.CommandParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CommandParserTest {

    @Test
    fun parseCall() {
        val cmd = CommandParser.parse("Позвони маме")
        assertNotNull(cmd)
        assertEquals(CommandAction.CALL, cmd!!.action)
        assertEquals("маме", cmd.param)
    }

    @Test
    fun parseCallEnglish() {
        val cmd = CommandParser.parse("call mom")
        assertNotNull(cmd)
        assertEquals(CommandAction.CALL, cmd!!.action)
        assertEquals("mom", cmd.param)
    }

    @Test
    fun parseSms() {
        val cmd = CommandParser.parse("отправь сообщение маме приду домой в семь")
        assertNotNull(cmd)
        assertEquals(CommandAction.SMS, cmd!!.action)
        val parts = cmd.param.split('\u0000')
        assertEquals("маме", parts[0])
        assertEquals("приду домой в семь", parts[1])
    }

    @Test
    fun parseTimer() {
        val cmd = CommandParser.parse("поставь таймер на 5 минут")
        assertNotNull(cmd)
        assertEquals(CommandAction.TIMER, cmd!!.action)
        assertEquals("300", cmd.param)
    }

    @Test
    fun parseTimerHoursAndMinutes() {
        val cmd = CommandParser.parse("таймер на 1 час 30 минут")
        assertNotNull(cmd)
        assertEquals(CommandAction.TIMER, cmd!!.action)
        assertEquals("5400", cmd.param)
    }

    @Test
    fun parseAlarm() {
        val cmd = CommandParser.parse("будильник на 7:30")
        assertNotNull(cmd)
        assertEquals(CommandAction.ALARM, cmd!!.action)
        assertEquals("07:30", cmd.param)
    }

    @Test
    fun parseAlarmMorning() {
        val cmd = CommandParser.parse("будильник на 8 утра")
        assertNotNull(cmd)
        assertEquals(CommandAction.ALARM, cmd!!.action)
        assertEquals("08:00", cmd.param)
    }

    @Test
    fun parseAlarmEvening() {
        val cmd = CommandParser.parse("будильник на 9 вечера")
        assertNotNull(cmd)
        assertEquals(CommandAction.ALARM, cmd!!.action)
        assertEquals("21:00", cmd.param)
    }

    @Test
    fun parseFlashlight() {
        val cmd = CommandParser.parse("включи фонарик")
        assertNotNull(cmd)
        assertEquals(CommandAction.FLASHLIGHT, cmd!!.action)
    }

    @Test
    fun parsePhoto() {
        val cmd = CommandParser.parse("сделай фото")
        assertNotNull(cmd)
        assertEquals(CommandAction.PHOTO, cmd!!.action)
    }

    @Test
    fun parseOpenApp() {
        val cmd = CommandParser.parse("открой телеграм")
        assertNotNull(cmd)
        assertEquals(CommandAction.OPEN_APP, cmd!!.action)
        assertEquals("телеграм", cmd.param)
    }

    @Test
    fun parseSearch() {
        val cmd = CommandParser.parse("найди в интернете погоду в москве")
        assertNotNull(cmd)
        assertEquals(CommandAction.SEARCH, cmd!!.action)
        assertEquals("погоду в москве", cmd.param)
    }

    @Test
    fun parseTime() {
        val cmd = CommandParser.parse("который час")
        assertNotNull(cmd)
        assertEquals(CommandAction.TIME, cmd!!.action)
    }

    @Test
    fun parseGreeting() {
        val cmd = CommandParser.parse("привет")
        assertNotNull(cmd)
        assertEquals(CommandAction.GREETING, cmd!!.action)
    }

    @Test
    fun parseHelp() {
        val cmd = CommandParser.parse("что ты умеешь")
        assertNotNull(cmd)
        assertEquals(CommandAction.HELP, cmd!!.action)
    }

    @Test
    fun parseGarbage() {
        assertNull(CommandParser.parse("абвгд"))
    }

    @Test
    fun normalizeLowercasesAndStripsPunctuation() {
        assertEquals("позвони маме", CommandParser.normalize("Позвони, маме!!!"))
    }

    @Test
    fun parseDurationEnglish() {
        val cmd = CommandParser.parse("set a timer for 10 minutes")
        assertNotNull(cmd)
        assertEquals(CommandAction.TIMER, cmd!!.action)
        assertEquals("600", cmd.param)
    }
}