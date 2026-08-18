package com.voicehelp.commands

object CommandParser {

    private val NUMBERS =
        "\\d+|пятнадцать|пятьдесят|двадцать|тридцать|четыре|восемь|девять|десять|один|одна|одно|два|две|три|пять|шесть|семь|fifteen|twenty|thirty|forty|fifty|one|two|three|four|five|six|seven|eight|nine|ten"

    private val greetingRegex = Regex(
        "(привет|здравствуй|здравствуйте|добрый день|добрый вечер|доброе утро|здорово|hello|hi|hey)"
    )
    private val howAreYouRegex = Regex("(как дела|как ты|как жизнь|how are you|how is it going)")
    private val thanksRegex = Regex("(спасибо|благодарю|спасибки|thanks|thank you|thank)")
    private val helpRegex = Regex(
        "(помощь|помоги|подскажи|что ты умеешь|что ты можешь|команды|список команд|help|what can you do)"
    )
    private val timeRegex = Regex("(который час|сколько времени|какое время|what time|который сейчас час)")

    private val callRegex = Regex("(позвони|позвонить|позвоню|набери|набрать|сделай звонок|звонок|звони|call)")
    private val smsRegex = Regex(
        "(отправь|отправить|напиши|написать|пошли|пошлю|send|сообщение|смс|sms|эсэмэс)"
    )
    private val timerRegex = Regex("(таймер|timer|засеки|засечь|отсчёт|отсчет)")
    private val alarmRegex = Regex("(будильник|alarm|разбуди)")
    private val flashlightRegex = Regex("(фонарик|фонарь|фонарик|flashlight|torch|flash)")
    private val photoRegex = Regex(
        "(сделай фото|сделать фото|сфотографируй|сфотографировать|сфоткай|снимок|фотографию|фотка|фото|take a photo|take photo|take a picture|take picture|photo)"
    )
    private val openAppRegex = Regex("(открой|открыть|запусти|запустить|open|открой приложение|открой апп)")
    private val searchRegex = Regex(
        "(найди|найти|поищи|поискать|поиск|найди в интернете|поищи в интернете|search|google|нагугли)"
    )

    private val verbPrefixes = listOf(
        "отправь сообщение", "отправить сообщение", "напиши сообщение", "написать сообщение",
        "отправь смс", "отправить смс", "отправь sms", "отправить sms", "напиши смс",
        "пошли сообщение", "отправь", "отправить", "напиши", "написать", "пошли", "send",
        "сделай звонок", "позвони", "позвонить", "позвоню", "набери", "набрать", "звони", "call",
        "открой приложение", "открыть приложение", "открой", "открыть", "запусти", "запустить", "open",
        "найди в интернете", "поищи в интернете", "найди", "найти", "поищи", "поискать", "поиск", "search",
        "поставь таймер", "заведи таймер", "запусти таймер", "таймер",
        "поставь будильник", "заведи будильник", "включи будильник", "будильник",
        "сделай фото", "сделать фото", "сфотографируй", "сфотографировать", "сфоткай", "фото",
        "включи фонарик", "выключи фонарик", "фонарик",
        "который час", "сколько времени",
        "привет", "здравствуй", "здравствуйте", "спасибо", "благодарю", "помоги", "помощь", "help"
    )

    fun parse(rawText: String): Command? {
        val text = normalize(rawText)
        if (text.isEmpty()) return null

        when {
            greetingRegex.containsMatchIn(text) -> return Command(CommandAction.GREETING, source = "local")
            howAreYouRegex.containsMatchIn(text) -> return Command(CommandAction.THANKS, reply = "Отлично, всё работает!", source = "local")
            thanksRegex.containsMatchIn(text) -> return Command(CommandAction.THANKS, source = "local")
            helpRegex.containsMatchIn(text) -> return Command(CommandAction.HELP, source = "local")
            timeRegex.containsMatchIn(text) -> return Command(CommandAction.TIME, source = "local")
        }

        if (timerRegex.containsMatchIn(text)) {
            val duration = parseDuration(text)
            if (duration != null) {
                return Command(CommandAction.TIMER, param = duration.toString(), source = "local")
            }
        }

        if (alarmRegex.containsMatchIn(text)) {
            val time = parseTime(text)
            if (time != null) {
                return Command(CommandAction.ALARM, param = time, source = "local")
            }
        }

        if (flashlightRegex.containsMatchIn(text)) {
            return Command(CommandAction.FLASHLIGHT, source = "local")
        }

        if (photoRegex.containsMatchIn(text)) {
            return Command(CommandAction.PHOTO, source = "local")
        }

        if (callRegex.containsMatchIn(text)) {
            val target = stripVerbs(text, setOf(
                "сделай звонок", "позвони", "позвонить", "позвоню", "набери", "набрать", "звони", "call", "на номер", "номеру"
            ))
            if (target.isNotEmpty()) {
                return Command(CommandAction.CALL, param = target, source = "local")
            }
        }

        if (searchRegex.containsMatchIn(text)) {
            val query = stripVerbs(text, setOf("найди в интернете", "поищи в интернете", "найди", "найти", "поищи", "поискать", "поиск", "search", "google", "нагугли", "в интернете"))
            if (query.isNotEmpty()) {
                return Command(CommandAction.SEARCH, param = query, source = "local")
            }
        }

        if (smsRegex.containsMatchIn(text)) {
            val rest = stripVerbs(text, setOf(
                "отправь сообщение", "отправить сообщение", "напиши сообщение", "написать сообщение",
                "отправь смс", "отправить смс", "отправь sms", "отправить sms", "напиши смс",
                "пошли сообщение", "отправь", "отправить", "напиши", "написать", "пошли", "send",
                "сообщение", "смс", "sms", "эсэмэс", "текст", "в сообщении"
            ))
            val parts = rest.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (parts.isNotEmpty()) {
                val target = parts.first()
                val message = parts.drop(1).joinToString(" ")
                if (message.isNotEmpty()) {
                    return Command(CommandAction.SMS, param = "$target\u0000$message", source = "local")
                }
            }
        }

        if (openAppRegex.containsMatchIn(text)) {
            val app = stripVerbs(text, setOf("открой приложение", "открыть приложение", "открой", "открыть", "запусти", "запустить", "open", "приложение", "апп", "app"))
            if (app.isNotEmpty()) {
                return Command(CommandAction.OPEN_APP, param = app, source = "local")
            }
        }

        return null
    }

    fun normalize(raw: String): String {
        return raw.lowercase()
            .replace(Regex("[«»\"„“'']"), "")
            .replace(Regex("[.,!?;:()\\[\\]{}—–-]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun stripVerbs(text: String, verbs: Set<String>): String {
        var result = text
        for (verb in verbs.sortedByDescending { it.length }) {
            if (result.startsWith(verb)) {
                result = result.removePrefix(verb).trimStart(' ').removePrefix("на ").removePrefix("к ").trim()
                break
            }
        }
        return result
    }

    fun parseDuration(text: String): Int? {
        val patterns = listOf(
            Regex("($NUMBERS)\\s*(?:минут[а-я]*|мин\\.?|min(?:ute)?s?)"),
            Regex("($NUMBERS)\\s*(?:часов?|часа|час\\.?|hrs?|hours?)"),
            Regex("($NUMBERS)\\s*(?:секунд[а-я]*|сек\\.?|sec(?:onds?)?)")
        )
        var totalSeconds = 0
        var found = false
        for (pattern in patterns) {
            pattern.findAll(text).forEach { match ->
                found = true
                val value = wordToNumber(match.groupValues[1])
                val unit = match.groupValues[0].substringAfterLast(match.groupValues[1]).lowercase().trim()
                totalSeconds += when {
                    unit.startsWith("час") || unit.startsWith("hr") || unit.startsWith("hour") -> value * 3600
                    unit.startsWith("сек") || unit.startsWith("sec") -> value
                    else -> value * 60
                }
            }
        }
        return if (found && totalSeconds > 0) totalSeconds else null
    }

    private fun wordToNumber(word: String): Int {
        val digits = word.toIntOrNull()
        if (digits != null) return digits
        return when (word.lowercase()) {
            "один", "одна", "одно", "one" -> 1
            "два", "две", "двух", "two" -> 2
            "три", "трёх", "three" -> 3
            "четыре", "четырёх", "four" -> 4
            "пять", "пяти", "five" -> 5
            "шесть", "шести", "six" -> 6
            "семь", "семи", "seven" -> 7
            "восемь", "восьми", "eight" -> 8
            "девять", "девяти", "nine" -> 9
            "десять", "десяти", "ten" -> 10
            "пятнадцать", "fifteen" -> 15
            "двадцать", "twenty" -> 20
            "тридцать", "thirty" -> 30
            "сорок", "forty" -> 40
            "пятьдесят", "fifty" -> 50
            else -> 0
        }
    }

    fun parseTime(text: String): String? {
        val hhmm = Regex("(\\d{1,2})[:.](\\d{2})").find(text)
        if (hhmm != null) {
            val h = hhmm.groupValues[1].toInt().coerceIn(0, 23)
            val m = hhmm.groupValues[2].toInt().coerceIn(0, 59)
            return "%02d:%02d".format(h, m)
        }

        val words = Regex("(\\d{1,2})\\s*(часов?|часа|час|ч|утра|дня|вечера|ночи|am|pm|рм|ам|утра|утро)?").findAll(text).toList()
        if (words.isNotEmpty()) {
            val first = words.first()
            val hour = first.groupValues[1].toInt()
            val marker = first.groupValues[2].lowercase()
            if (hour in 0..23) {
                var h = hour
                when {
                    (marker == "дня" || marker == "вечера") && h < 12 -> h += 12
                    marker == "pm" && h < 12 -> h += 12
                    marker == "am" && h == 12 -> h = 0
                }
                val minutes = words.drop(1).firstOrNull()?.groupValues?.get(1)?.toIntOrNull()
                return "%02d:%02d".format(h, minutes ?: 0)
            }
        }

        val bare = Regex("на\\s+(\\d{1,2})").find(text) ?: Regex("в\\s+(\\d{1,2})").find(text)
        if (bare != null) {
            val h = bare.groupValues[1].toInt().coerceIn(0, 23)
            return "%02d:00".format(h)
        }

        return null
    }
}