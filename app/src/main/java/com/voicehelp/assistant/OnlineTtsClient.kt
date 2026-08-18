package com.voicehelp.assistant

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

class OnlineTtsClient {

    private companion object {
        const val TAG = "OnlineTts"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    suspend fun synthesize(voice: String, text: String, language: String): ByteArray? {
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(30_000) {
                val result = CompletableDeferred<ByteArray?>()
                val requestId = UUID.randomUUID().toString()
                val url = "wss://eastus.api.speech.microsoft.com/cognitiveservices/websocket/v1" +
                    "?TricType=AzureDemo&Authorization=bearer%20undefined&X-ConnectionId=$requestId"

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Origin", "https://azure.microsoft.com")
                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 12; M2012K11AC Build/N6F26Q; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/81.0.4044.117 Mobile Safari/537.36")
                    .build()

                Log.i(TAG, "synthesize start voice=$voice lang=$language text=${text.take(40)}")

                val webSocket = client.newWebSocket(request, object : WebSocketListener() {
                    private var frameNo = 0
                    private val audio = ByteArrayOutputStream()
                    private var done = false

                    private fun finish(value: ByteArray?) {
                        if (!done) {
                            done = true
                            result.complete(value)
                        }
                    }

                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        Log.i(TAG, "ws open, code=${response.code}")
                        val ts = isoTime()
                        val config = "Path: speech.config\r\nX-RequestId: $requestId\r\nX-Timestamp: $ts" +
                            "\r\nContent-Type: application/json\r\n\r\n" +
                            "{\"context\":{\"system\":{\"name\":\"SpeechSDK\",\"version\":\"1.19.0\"," +
                            "\"build\":\"JavaScript\",\"lang\":\"JavaScript\",\"os\":{\"platform\":\"Browser/Linux x86_64\"," +
                            "\"name\":\"Mozilla/5.0 (X11; Linux x86_64; rv:78.0) Gecko/20100101 Firefox/78.0\",\"version\":\"5.0 (X11)\"}}}}"
                        val synthesis = "Path: synthesis.context\r\nX-RequestId: $requestId\r\nX-Timestamp: $ts" +
                            "\r\nContent-Type: application/json\r\n\r\n" +
                            "{\"synthesis\":{\"audio\":{\"metadataOptions\":{\"sentenceBoundaryEnabled\":false," +
                            "\"wordBoundaryEnabled\":false},\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}"
                        val ssmlText = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis'" +
                            " xml:lang='$language'><voice name='$voice'>${escapeXml(text)}</voice></speak>"
                        val ssml = "Path: ssml\r\nX-RequestId: $requestId\r\nX-Timestamp: $ts" +
                            "\r\nContent-Type: application/ssml+xml\r\n\r\n$ssmlText"
                        webSocket.send(config)
                        webSocket.send(synthesis)
                        webSocket.send(ssml)
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        if (text.contains("turn.end")) {
                            Log.i(TAG, "turn.end, audio bytes=${audio.size()}")
                            webSocket.close(1000, null)
                            finish(audio.toByteArray())
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        val marker = "Path:audio".encodeUtf8()
                        val data = bytes.toByteArray()
                        val idx = bytes.indexOf(marker)
                        var start = data.size
                        if (idx >= 0) {
                            val headerEnd = String(data, idx, data.size - idx).indexOf("\r\n\r\n")
                            start = if (headerEnd >= 0) idx + headerEnd + 4 else idx + marker.size + 3
                        }
                        frameNo++
                        if (start in 0 until data.size) {
                            audio.write(data, start, data.size - start)
                            Log.d(TAG, "frame#$frameNo size=${data.size} start=$start -> total=${audio.size()}")
                        } else {
                            Log.d(TAG, "frame#$frameNo size=${data.size} marker@$idx skipped")
                        }
                        if (audio.size() > 5_000_000) {
                            finish(null)
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.w(TAG, "ws failure: ${t.message} http=${response?.code}", t)
                        finish(null)
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(1000, null)
                        finish(audio.toByteArray())
                    }
                })

                val audioBytes = result.await()
                if (audioBytes != null && audioBytes.isNotEmpty()) audioBytes else null
            }
        }
    }

    private fun escapeXml(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private fun isoTime(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }

    fun cancel() {
        client.dispatcher.cancelAll()
    }
}
