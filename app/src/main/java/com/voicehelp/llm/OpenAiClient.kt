package com.voicehelp.llm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

@Serializable
data class ChatMessage(val role: String, val content: String)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    val max_tokens: Int = 300
)

@Serializable
data class ChatResponse(val choices: List<Choice> = emptyList())

@Serializable
data class Choice(val message: ChatMessage = ChatMessage("", ""))

@Serializable
data class ModelsResponse(val data: List<ModelInfo> = emptyList())

@Serializable
data class ModelInfo(val id: String = "")

interface OpenAiApi {
    @GET("models")
    suspend fun models(): ModelsResponse

    @POST("chat/completions")
    suspend fun chat(@Body body: ChatRequest): ChatResponse
}

class AuthInterceptor(private val apiKey: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = if (apiKey.isNotBlank()) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $apiKey")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}

class OpenAiClient(baseUrl: String, apiKey: String) {

    private val okHttp: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(apiKey))
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private val api: OpenAiApi = Retrofit.Builder()
        .baseUrl(normalizeBaseUrl(baseUrl))
        .client(okHttp)
        .addConverterFactory(
            Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType())
        )
        .build()
        .create(OpenAiApi::class.java)

    suspend fun listModels(): List<String> {
        return try {
            api.models().data.map { it.id }.sorted()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun chat(model: String, messages: List<ChatMessage>): String? {
        return try {
            api.chat(ChatRequest(model, messages)).choices.firstOrNull()?.message?.content
        } catch (e: Exception) {
            null
        }
    }
}

fun normalizeBaseUrl(url: String): String {
    val u = url.trim().trimEnd('/')
    return u + "/"
}