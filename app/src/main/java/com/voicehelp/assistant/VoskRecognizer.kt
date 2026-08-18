package com.voicehelp.assistant

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

class VoskRecognizer(private val context: Context) {

    companion object {
        private const val TAG = "VoskRecognizer"
        private const val SAMPLE_RATE = 16000
        private const val SILENCE_MS = 1400L
        private const val MAX_SPEECH_MS = 30_000L
        private const val ENERGY_THRESHOLD = 900.0
        private const val MIN_SPEECH_MS = 250L
    }

    @Volatile
    private var running = false
    private var thread: Thread? = null
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var audioSource: AudioSource? = null

    fun isRunning(): Boolean = running

    fun start(
        lang: String,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        if (running) return
        val modelDir = VoskModelManager.modelDir(context, lang)
        if (!VoskModelManager.isDownloaded(context, lang)) {
            onError(IllegalStateException("Модель $lang не скачана"))
            return
        }
        running = true
        thread = Thread({
            try {
                val m = Model(modelDir.absolutePath)
                model = m
                val r = Recognizer(m, SAMPLE_RATE.toFloat())
                recognizer = r
                val source = AudioSource(SAMPLE_RATE)
                audioSource = source
                source.start()

                var heardSpeech = false
                var silentMs = 0L
                var speechMs = 0L
                var totalMs = 0L
                var lastPartial = ""

                while (running) {
                    val data = source.read()
                    if (data.isEmpty()) continue
                    val frameMs = data.size / 2L / (SAMPLE_RATE / 1000L)
                    totalMs += frameMs

                    val energy = rms(data)
                    if (energy > ENERGY_THRESHOLD) {
                        heardSpeech = true
                        speechMs += frameMs
                        silentMs = 0L
                    } else {
                        silentMs += frameMs
                    }

                    if (r.acceptWaveForm(data, data.size)) {
                        break
                    } else {
                        val partial = parseText(r.partialResult)
                        if (partial.isNotEmpty() && partial != lastPartial) {
                            lastPartial = partial
                            onPartial(partial)
                        }
                    }

                    if (heardSpeech && speechMs >= MIN_SPEECH_MS && silentMs >= SILENCE_MS) break
                    if (totalMs >= MAX_SPEECH_MS) break
                }

                val text = parseText(r.finalResult)
                if (running) {
                    onFinal(text)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка распознавания", e)
                if (running) {
                    onError(e)
                }
            } finally {
                cleanup()
            }
        }, "vosk-recognizer").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        try {
            thread?.join(1500)
        } catch (_: InterruptedException) {
        }
    }

    private fun cleanup() {
        try {
            audioSource?.stop()
        } catch (_: Exception) {
        }
        audioSource = null
        try {
            recognizer?.close()
        } catch (_: Exception) {
        }
        recognizer = null
        model?.close()
        model = null
        running = false
        thread = null
    }

    private fun parseText(json: String): String {
        return try {
            JSONObject(json).optString("text").trim()
        } catch (e: Exception) {
            ""
        }
    }

    private fun rms(data: ByteArray): Double {
        var sum = 0.0
        var i = 0
        while (i + 1 < data.size) {
            val sample = (data[i].toInt() and 0xFF) or (data[i + 1].toInt() shl 8)
            sum += sample.toDouble() * sample
            i += 2
        }
        val n = data.size / 2
        return if (n == 0) 0.0 else Math.sqrt(sum / n)
    }
}