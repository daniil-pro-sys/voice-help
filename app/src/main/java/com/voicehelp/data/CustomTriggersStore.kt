package com.voicehelp.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.random.Random

@Serializable
data class CustomTrigger(
    val id: String,
    val name: String,
    val phrases: List<String>,
    val action: String,
    val param: String = "",
    val reply: String = ""
)

object CustomTriggersStore {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private fun file(context: Context): File = File(context.filesDir, "triggers.json")

    fun load(context: Context): List<CustomTrigger> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return try {
            json.decodeFromString<List<CustomTrigger>>(f.readText())
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, triggers: List<CustomTrigger>) {
        file(context).writeText(json.encodeToString(triggers))
    }

    fun add(context: Context, trigger: CustomTrigger): List<CustomTrigger> {
        val list = load(context) + trigger
        save(context, list)
        return list
    }

    fun update(context: Context, trigger: CustomTrigger): List<CustomTrigger> {
        val list = load(context).map { if (it.id == trigger.id) trigger else it }
        save(context, list)
        return list
    }

    fun remove(context: Context, id: String): List<CustomTrigger> {
        val list = load(context).filterNot { it.id == id }
        save(context, list)
        return list
    }

    fun newId(): String = Random.nextLong().toString(16)
}