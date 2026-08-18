package com.voicehelp.assistant

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.voicehelp.R
import com.voicehelp.audio.SoundPlayer
import com.voicehelp.commands.ActionExecutor
import com.voicehelp.commands.Command
import com.voicehelp.commands.CommandAction
import com.voicehelp.commands.CommandParser
import com.voicehelp.commands.actions.SearchAction
import com.voicehelp.data.CustomTriggersStore
import com.voicehelp.data.SettingsStore
import com.voicehelp.llm.ChatMessage
import com.voicehelp.llm.OpenAiClient
import com.voicehelp.llm.SmartInterpreter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AssistantEngine(
    private val context: Context,
    private val ui: UiCallbacks
) {
    enum class Status { IDLE, LISTENING, RECOGNIZING, THINKING, SPEAKING, ERROR }

    companion object {
        private const val TAG = "AssistantEngine"
    }

    interface UiCallbacks {
        fun onStatus(status: Status)
        fun onPartial(text: String)
        fun onFinalText(text: String)
        fun onReply(text: String)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val recognizer = VoskRecognizer(context)
    private val sounds = SoundPlayer(context)
    private val tts = TtsManager(context)
    private val executor = ActionExecutor(context)

    private val history = mutableListOf<ChatMessage>()
    private var smartInterpreter: SmartInterpreter? = null
    private var ttsInitialized = false
    private var busy = false

    fun startSession() {
        if (busy) return
        busy = true
        history.clear()
        updateLlmClient()
        ensureTts {
            mainHandler.post {
                if (busy) startListening()
            }
        }
    }

    fun endSession() {
        if (!busy) return
        busy = false
        recognizer.stop()
        sounds.play(SoundPlayer.Kind.DIALOG_END)
        ui.onStatus(Status.IDLE)
    }

    fun cancel() {
        busy = false
        scope.cancel()
        recognizer.stop()
        tts.shutdown()
        sounds.release()
        ui.onStatus(Status.IDLE)
    }

    private fun updateLlmClient() {
        val enabled = SettingsStore.llmEnabled(context) &&
            SettingsStore.llmBaseUrl(context).isNotBlank()
        if (enabled) {
            smartInterpreter = SmartInterpreter(
                OpenAiClient(SettingsStore.llmBaseUrl(context), SettingsStore.llmApiKey(context)),
                SettingsStore.llmModel(context),
                SettingsStore.sttLanguage(context)
            )
        } else {
            smartInterpreter = null
        }
    }

    private fun ensureTts(onReady: () -> Unit) {
        if (ttsInitialized) {
            onReady()
            return
        }
        tts.init(SettingsStore.ttsEngine(context)) { ready ->
            ttsInitialized = true
            if (ready) {
                tts.setVoice(SettingsStore.ttsVoice(context))
            }
            onReady()
        }
    }

    private fun startListening() {
        ui.onStatus(Status.LISTENING)
        sounds.play(SoundPlayer.Kind.MIC_ON)
        val lang = SettingsStore.sttLanguage(context)
        recognizer.start(
            lang = lang,
            onPartial = { text ->
                mainHandler.post { if (busy) ui.onPartial(text) }
            },
            onFinal = { text ->
                mainHandler.post { if (busy) handleFinal(text) }
            },
            onError = { error ->
                mainHandler.post { if (busy) handleError(error) }
            }
        )
    }

    private fun handleFinal(text: String) {
        Log.i(TAG, "final text: $text")
        ui.onStatus(Status.RECOGNIZING)
        sounds.play(SoundPlayer.Kind.RECOGNIZED)
        ui.onFinalText(text)

        val normalized = CommandParser.normalize(text)
        if (normalized.isEmpty()) {
            speakReply(context.getString(R.string.reply_heard_nothing))
            return
        }

        val spokenLang = TtsManager.detectLanguage(text)
        if (spokenLang != SettingsStore.sttLanguage(context)) {
            SettingsStore.saveSttLanguage(context, spokenLang)
        }

        if (normalized in SettingsStore.stopWords(context)) {
            endSession()
            return
        }

        scope.launch {
            interpretAndRespond(normalized, text)
        }
    }

    private suspend fun interpretAndRespond(normalized: String, original: String) {
        ui.onStatus(Status.THINKING)
        history.add(ChatMessage("user", original))

        val command = customTriggerMatch(normalized)
            ?: CommandParser.parse(normalized)
            ?: llmInterpret(normalized)
            ?: Command(CommandAction.REPLY, reply = context.getString(R.string.reply_not_understood), source = "fallback")

        if (command.action == CommandAction.REPLY && command.reply.isBlank()) {
            speakReply(context.getString(R.string.reply_not_understood))
            history.add(ChatMessage("assistant", context.getString(R.string.reply_not_understood)))
            return
        }

        val reply = try {
            executeSmart(command)
        } catch (e: Exception) {
            context.getString(R.string.reply_not_understood)
        }

        if (reply.isNotBlank()) {
            history.add(ChatMessage("assistant", reply))
        }
        speakReply(reply)
    }

    private suspend fun llmInterpret(text: String): Command? {
        val interpreter = smartInterpreter ?: return null
        return try {
            interpreter.interpret(text, history)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun executeSmart(command: Command): String {
        if (command.action == CommandAction.SEARCH && smartInterpreter != null && SettingsStore.searchEnabled(context)) {
            val raw = SearchAction(context).search(command.param)
            if (!raw.isNullOrEmpty()) {
                val phrased = try {
                    smartInterpreter?.phraseSearch(command.param, raw)
                } catch (e: Exception) {
                    null
                }
                if (!phrased.isNullOrEmpty()) return phrased
                return raw
            }
        }
        return executor.execute(command)
    }

    private fun speakReply(text: String) {
        if (text.isBlank()) {
            afterSpeak()
            return
        }
        Log.i(TAG, "speakReply: $text")
        ui.onReply(text)
        if (!SettingsStore.ttsEnabled(context)) {
            afterSpeak()
            return
        }
        val online = SettingsStore.onlineTtsEnabled(context)
        if (!online && !tts.isReady()) {
            afterSpeak()
            return
        }
        ui.onStatus(Status.SPEAKING)
        tts.speak(text, TtsManager.detectLanguage(text)) {
            mainHandler.post { afterSpeak() }
        }
    }

    private fun afterSpeak() {
        if (!busy) return
        if (SettingsStore.conversationMode(context)) {
            startListening()
        } else {
            endSession()
        }
    }

    private fun handleError(error: Throwable) {
        sounds.play(SoundPlayer.Kind.ERROR)
        ui.onStatus(Status.ERROR)
        val message = error.message ?: ""
        if (message.contains("не скачана")) {
            ui.onReply(context.getString(R.string.assistant_model_missing))
        } else {
            ui.onReply(context.getString(R.string.reply_not_understood))
        }
        endSession()
    }

    private fun customTriggerMatch(normalized: String): Command? {
        val triggers = CustomTriggersStore.load(context)
        for (trigger in triggers) {
            val matched = trigger.phrases.any {
                val p = CommandParser.normalize(it)
                p.isNotEmpty() && normalized.contains(p)
            }
            if (!matched) continue
            val action = triggerAction(trigger.action) ?: continue
            return Command(
                action = action,
                param = trigger.param,
                reply = trigger.reply,
                source = "custom"
            )
        }
        return null
    }

    private fun triggerAction(name: String): CommandAction? = when (name) {
        "reply" -> CommandAction.REPLY
        "call" -> CommandAction.CALL
        "sms" -> CommandAction.SMS
        "timer" -> CommandAction.TIMER
        "alarm" -> CommandAction.ALARM
        "flashlight" -> CommandAction.FLASHLIGHT
        "photo" -> CommandAction.PHOTO
        "open_app" -> CommandAction.OPEN_APP
        "search" -> CommandAction.SEARCH
        "time" -> CommandAction.TIME
        else -> null
    }
}