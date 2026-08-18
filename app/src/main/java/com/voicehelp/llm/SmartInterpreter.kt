package com.voicehelp.llm

import com.voicehelp.commands.Command
import com.voicehelp.commands.CommandAction
import org.json.JSONObject

class SmartInterpreter(
    private val client: OpenAiClient,
    private val model: String,
    private val language: String = "ru"
) {

    companion object {
        const val SYSTEM_PROMPT = """Ты — «Voice Help», голосовой ассистент на Android. Отвечай кратко (1–2 предложения), на языке пользователя.

Если пользователь просит выполнить действие на телефоне, ответь СТРОГО одним JSON без пояснений и без markdown:
{"action":"call","params":{"target":"имя или номер"}}

Доступные действия:
- call: позвонить. params.target = имя или номер
- sms: отправить сообщение. params.target и params.text
- timer: поставить таймер. params.minutes = число минут
- alarm: будильник. params.time = "HH:MM"
- flashlight: включить или выключить фонарик
- photo: сделать фото
- open_app: открыть приложение. params.app = имя приложения
- search: найти в интернете. params.query = запрос
- time: который час
- help: список возможностей
- none: обычный разговор. params.reply = короткий текст ответа

Пример разговора: {"action":"none","params":{"reply":"Конечно! Расскажу."}}"""
    }

    private val systemPrompt: String
        get() = SYSTEM_PROMPT + "\n\nЯзык пользователя: $language. Отвечай на этом языке."

    suspend fun interpret(userText: String, history: List<ChatMessage>): Command? {
        val messages = buildList {
            add(ChatMessage("system", systemPrompt))
            addAll(history.takeLast(8))
            add(ChatMessage("user", userText))
        }
        val content = client.chat(model, messages) ?: return null
        return parseResponse(content)
    }

    suspend fun phraseSearch(query: String, results: String): String? {
        val messages = listOf(
            ChatMessage("system", systemPrompt),
            ChatMessage("user", "Пользователь спросил: $query\n\nРезультаты поиска:\n$results\n\nДай краткий голосовой ответ (1-2 предложения), используя результаты поиска. Отвечай текстом, без JSON.")
        )
        return client.chat(model, messages)?.trim()
    }

    private fun parseResponse(content: String): Command {
        val text = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val json = try {
            JSONObject(text)
        } catch (e: Exception) {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start >= 0 && end > start) {
                try {
                    JSONObject(text.substring(start, end + 1))
                } catch (e2: Exception) {
                    return Command(CommandAction.REPLY, reply = text, source = "llm")
                }
            } else {
                return Command(CommandAction.REPLY, reply = text, source = "llm")
            }
        }

        val action = json.optString("action").lowercase()
        val params = json.optJSONObject("params") ?: JSONObject()
        val reply = params.optString("reply").ifEmpty {
            if (json.has("reply")) json.optString("reply") else ""
        }

        return when (action) {
            "call" -> Command(CommandAction.CALL, param = params.optString("target"), source = "llm")
            "sms" -> Command(
                CommandAction.SMS,
                param = "${params.optString("target")}\u0000${params.optString("text")}",
                source = "llm"
            )
            "timer" -> Command(
                CommandAction.TIMER,
                param = params.optString("minutes"),
                source = "llm"
            )
            "alarm" -> Command(CommandAction.ALARM, param = params.optString("time"), source = "llm")
            "flashlight" -> Command(CommandAction.FLASHLIGHT, source = "llm")
            "photo" -> Command(CommandAction.PHOTO, source = "llm")
            "open_app" -> Command(CommandAction.OPEN_APP, param = params.optString("app"), source = "llm")
            "search" -> Command(CommandAction.SEARCH, param = params.optString("query"), source = "llm")
            "time" -> Command(CommandAction.TIME, source = "llm")
            "help" -> Command(CommandAction.HELP, source = "llm")
            "none" -> Command(CommandAction.NONE, reply = reply, source = "llm")
            else -> Command(CommandAction.NONE, reply = reply.ifEmpty { text }, source = "llm")
        }
    }
}