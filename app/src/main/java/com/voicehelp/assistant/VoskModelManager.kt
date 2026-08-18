package com.voicehelp.assistant

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.zip.ZipInputStream

object VoskModelManager {

    const val MODEL_BASE_URL = "https://alphacephei.com/vosk/models"

    val MODELS: Map<String, String> = mapOf(
        "ru" to "vosk-model-small-ru-0.22",
        "en" to "vosk-model-small-en-us-0.15"
    )

    private val client = OkHttpClient.Builder().build()

    fun modelDir(context: Context, lang: String): File {
        val name = MODELS[lang] ?: MODELS.getValue("ru")
        return File(context.filesDir, "models/$name")
    }

    fun isDownloaded(context: Context, lang: String): Boolean {
        val dir = modelDir(context, lang)
        return dir.exists() && File(dir, "conf").exists() && File(dir, "am").exists()
    }

    suspend fun download(
        context: Context,
        lang: String,
        onProgress: (Int) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val modelName = MODELS[lang] ?: MODELS.getValue("ru")
        val url = "$MODEL_BASE_URL/$modelName.zip"
        val dir = File(context.filesDir, "models")
        dir.mkdirs()
        val zipFile = File(dir, "$modelName.zip")

        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}")
            }
            val total = response.body?.contentLength() ?: -1L
            val sink = response.body?.byteStream() ?: throw IllegalStateException("No body")
            zipFile.outputStream().use { out ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var downloaded = 0L
                var lastReported = -1
                while (true) {
                    val read = sink.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    downloaded += read
                    if (total > 0) {
                        val percent = ((downloaded * 100) / total).toInt()
                        if (percent != lastReported) {
                            lastReported = percent
                            onProgress(percent)
                        }
                    }
                }
            }
        }

        val targetDir = File(dir, modelName)
        if (targetDir.exists()) targetDir.deleteRecursively()
        unzip(zipFile, dir)
        zipFile.delete()

        if (!isDownloaded(context, lang)) {
            throw IllegalStateException("Модель повреждена")
        }
    }

    private fun unzip(zipFile: File, destDir: File) {
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { out -> zis.copyTo(out) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    fun delete(context: Context, lang: String) {
        modelDir(context, lang).deleteRecursively()
    }
}