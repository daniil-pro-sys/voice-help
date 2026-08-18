package com.voicehelp.assistant

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.voicehelp.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.UUID

class TtsManager(private val context: Context) {

    fun interface Listener {
        fun onInitDone(ready: Boolean)
    }

    companion object {
        private const val TAG = "TtsManager"
        private val cyrillic = Regex("[а-яёА-ЯЁ]")

        fun detectLanguage(text: String): String =
            if (cyrillic.containsMatchIn(text)) "ru" else "en"
    }

    private var tts: TextToSpeech? = null
    private var ready = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null

    fun init(engine: String? = null, listener: Listener?) {
        val instance = TextToSpeech(context.applicationContext, { status ->
            ready = status == TextToSpeech.SUCCESS
            Log.i(TAG, "init ready=$ready engine=$engine")
            listener?.onInitDone(ready)
        }, engine)
        tts = instance
    }

    fun isReady(): Boolean = ready

    fun listEngines(): List<String> {
        val instance = tts ?: return emptyList()
        return instance.engines.map { it.name }.sorted()
    }

    fun listVoices(): List<Voice> {
        val instance = tts ?: return emptyList()
        return instance.voices?.toList() ?: emptyList()
    }

    fun setVoice(voiceName: String?): Boolean {
        val instance = tts ?: return false
        if (voiceName.isNullOrEmpty()) return true
        val voice = instance.voices?.firstOrNull { it.name == voiceName } ?: return false
        return instance.setVoice(voice) == TextToSpeech.SUCCESS
    }

    fun speak(text: String, language: String, onDone: () -> Unit) {
        if (SettingsStore.onlineTtsEnabled(context)) {
            speakOnline(text, language, onDone)
        } else {
            speakSystem(text, language, onDone)
        }
    }

    private fun speakOnline(text: String, language: String, onDone: () -> Unit) {
        currentJob?.cancel()
        currentJob = scope.launch {
            val voice = SettingsStore.onlineTtsVoice(context)
            Log.i(TAG, "speakOnline start text=${text.take(40)} lang=$language voice=$voice")
            val audio = try {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    OnlineTtsClient().synthesize(voice, text, language)
                }
            } catch (e: Exception) {
                Log.w(TAG, "speakOnline synthesize exception: ${e.message}", e)
                null
            }
            if (audio == null || !isActive) {
                Log.w(TAG, "speakOnline no audio, fallback to system TTS")
                if (isActive) speakSystem(text, language, onDone)
                else onDone()
                return@launch
            }
            Log.i(TAG, "speakOnline audio ok bytes=${audio.size}")
            val file = File(context.cacheDir, "tts_online.mp3")
            try {
                file.writeBytes(audio)
                playMp3(file, onDone)
            } catch (e: Exception) {
                Log.w(TAG, "speakOnline write/play failed: ${e.message}", e)
                speakSystem(text, language, onDone)
            }
        }
    }

    private fun playMp3(file: File, onDone: () -> Unit) {
        releasePlayer()
        mediaPlayer = MediaPlayer()
        try {
            mediaPlayer?.setDataSource(file.absolutePath)
            mediaPlayer?.setOnCompletionListener {
                Log.i(TAG, "mp3 playback done")
                releasePlayer()
                onDone()
            }
            mediaPlayer?.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "mp3 playback error what=$what extra=$extra")
                releasePlayer()
                onDone()
                true
            }
            mediaPlayer?.prepareAsync()
            mediaPlayer?.setOnPreparedListener { it.start() }
        } catch (e: Exception) {
            Log.w(TAG, "mp3 prepare exception: ${e.message}", e)
            releasePlayer()
            onDone()
        }
    }

    private fun releasePlayer() {
        mediaPlayer?.let {
            it.setOnCompletionListener(null)
            it.setOnErrorListener(null)
            it.setOnPreparedListener(null)
            try {
                it.stop()
            } catch (ignored: Exception) {
            }
            it.release()
        }
        mediaPlayer = null
    }

    private fun speakSystem(text: String, language: String, onDone: () -> Unit) {
        val instance = tts ?: run { Log.w(TAG, "speakSystem: tts null"); onDone(); return }
        if (!ready) {
            Log.w(TAG, "speakSystem: tts not ready")
            onDone()
            return
        }
        Log.i(TAG, "speakSystem speak lang=$language")
        val locale = if (language == "ru") Locale("ru", "RU") else Locale.US
        if (instance.voice == null) {
            instance.setLanguage(locale)
        }
        val utteranceId = UUID.randomUUID().toString()
        instance.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                onDone()
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                onDone()
            }
            override fun onError(utteranceId: String?, errorCode: Int) {
                onDone()
            }
        })
        instance.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        currentJob?.cancel()
        currentJob = null
        releasePlayer()
        tts?.stop()
    }

    fun shutdown() {
        currentJob?.cancel()
        currentJob = null
        releasePlayer()
        scope.cancel()
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
