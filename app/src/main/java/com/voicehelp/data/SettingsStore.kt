package com.voicehelp.data

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale

object SettingsStore {
    const val PREFS_NAME = "voicehelp_settings"

    private const val KEY_STT_LANGUAGE = "stt_language"
    private const val KEY_LLM_ENABLED = "llm_enabled"
    private const val KEY_LLM_BASE_URL = "llm_base_url"
    private const val KEY_LLM_API_KEY = "llm_api_key"
    private const val KEY_LLM_MODEL = "llm_model"
    private const val KEY_LLM_MODELS_CACHE = "llm_models_cache"
    private const val KEY_TTS_ENABLED = "tts_enabled"
    private const val KEY_TTS_ENGINE = "tts_engine"
    private const val KEY_TTS_VOICE = "tts_voice"
    private const val KEY_CONVERSATION_MODE = "conversation_mode"
    private const val KEY_STOP_WORDS = "stop_words"
    private const val KEY_CONTACT_ALIASES = "contact_aliases"
    private const val KEY_SOUNDS_ENABLED = "sounds_enabled"
    private const val KEY_SEARCH_ENABLED = "search_enabled"
    private const val KEY_ONLINE_TTS_ENABLED = "online_tts_enabled"
    private const val KEY_ONLINE_TTS_VOICE = "online_tts_voice"

    const val DEFAULT_STOP_WORDS = "стоп, хватит, достаточно, stop, exit, quit"
    const val DEFAULT_ONLINE_TTS_VOICE = "en-US-Phoebe:DragonHDLatestNeural"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val Context.settings: SharedPreferences get() = prefs(this)

    fun systemLanguage(context: Context): String {
        val lang = Locale.getDefault().language.lowercase()
        return if (lang.startsWith("ru")) "ru" else "en"
    }

    fun sttLanguage(context: Context): String =
        prefs(context).getString(KEY_STT_LANGUAGE, systemLanguage(context)) ?: systemLanguage(context)

    fun saveSttLanguage(context: Context, lang: String) {
        prefs(context).edit().putString(KEY_STT_LANGUAGE, lang).apply()
    }

    fun llmEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LLM_ENABLED, true)

    fun llmBaseUrl(context: Context): String =
        prefs(context).getString(KEY_LLM_BASE_URL, "") ?: ""

    fun llmApiKey(context: Context): String =
        prefs(context).getString(KEY_LLM_API_KEY, "") ?: ""

    fun llmModel(context: Context): String =
        prefs(context).getString(KEY_LLM_MODEL, "") ?: ""

    fun llmModelsCache(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_LLM_MODELS_CACHE, "") ?: ""
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun saveLlModels(context: Context, models: List<String>) {
        prefs(context).edit().putString(KEY_LLM_MODELS_CACHE, models.joinToString(",")).apply()
    }

    fun ttsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TTS_ENABLED, true)

    fun ttsEngine(context: Context): String? =
        prefs(context).getString(KEY_TTS_ENGINE, null)

    fun ttsVoice(context: Context): String? =
        prefs(context).getString(KEY_TTS_VOICE, null)

    fun conversationMode(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CONVERSATION_MODE, true)

    fun stopWords(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_STOP_WORDS, DEFAULT_STOP_WORDS) ?: DEFAULT_STOP_WORDS
        return raw.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
    }

    fun contactAliases(context: Context): Map<String, String> {
        val raw = prefs(context).getString(KEY_CONTACT_ALIASES, "") ?: ""
        return raw.lineSequence()
            .map { it.trim() }
            .filter { it.contains("=") }
            .mapNotNull { line ->
                val idx = line.indexOf('=')
                val name = line.substring(0, idx).trim().lowercase()
                val number = line.substring(idx + 1).trim()
                if (name.isNotEmpty() && number.isNotEmpty()) name to number else null
            }
            .toMap()
    }

    fun soundsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SOUNDS_ENABLED, true)

    fun searchEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SEARCH_ENABLED, true)

    fun onlineTtsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ONLINE_TTS_ENABLED, false)

    fun onlineTtsVoice(context: Context): String =
        prefs(context).getString(KEY_ONLINE_TTS_VOICE, DEFAULT_ONLINE_TTS_VOICE) ?: DEFAULT_ONLINE_TTS_VOICE
}