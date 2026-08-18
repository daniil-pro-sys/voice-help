package com.voicehelp.commands.actions

import android.content.Context
import com.voicehelp.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class SearchAction(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    suspend fun search(query: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.duckduckgo.com/".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("format", "json")
                .addQueryParameter("no_html", "1")
                .addQueryParameter("skip_disambig", "1")
                .build()

            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val abstractText = json.optString("AbstractText").trim()
                val answer = json.optString("Answer").trim()
                val definition = json.optString("Definition").trim()
                when {
                    abstractText.isNotEmpty() -> abstractText
                    answer.isNotEmpty() -> answer
                    definition.isNotEmpty() -> definition
                    else -> {
                        val related = json.optJSONArray("RelatedTopics")
                        if (related != null && related.length() > 0) {
                            val first = related.optJSONObject(0)
                            if (first != null) first.optString("Text").trim().ifEmpty { null }
                            else null
                        } else null
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}