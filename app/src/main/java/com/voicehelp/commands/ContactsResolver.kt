package com.voicehelp.commands

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import com.voicehelp.data.SettingsStore

object ContactsResolver {

    fun resolve(context: Context, rawTarget: String): String? {
        val target = rawTarget.trim().lowercase().removeSuffix(".").removeSuffix(",").trim()
        if (target.isEmpty()) return null

        if (target.startsWith("+") || target.all { it.isDigit() || it == ' ' || it == '-' || it == '(' || it == ')' }) {
            return target.replace(Regex("[^+\\d]"), "")
        }

        val aliases = SettingsStore.contactAliases(context)
        val aliasNumber = aliases[target]
        if (aliasNumber != null) return aliasNumber

        for ((alias, number) in aliases) {
            if (wordMatches(target, alias)) return number
        }

        return findInContacts(context, target)
    }

    private fun findInContacts(context: Context, target: String): String? {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$target%")

        var best: Pair<Int, String>? = null
        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection, selection, selectionArgs, null
            )?.use { cursor: Cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0) ?: continue
                    val number = cursor.getString(1) ?: continue
                    val score = matchScore(name.lowercase(), target)
                    if (score > 0 && (best == null || score > best!!.first)) {
                        best = score to number
                    }
                }
            }
        } catch (_: SecurityException) {
            return null
        }
        return best?.second?.replace(Regex("[^+\\d]"), "")
    }

    private fun matchScore(name: String, target: String): Int {
        if (name == target) return 100
        if (name.startsWith(target)) return 90
        if (name.contains(target)) return 70
        val stem = target.dropLast(1)
        if (stem.length >= 3 && name.startsWith(stem)) return 60
        return 0
    }

    private fun wordMatches(text: String, alias: String): Boolean {
        if (text == alias) return true
        val stem = alias.dropLast(1)
        if (stem.length < 3) return text.contains(alias)
        return text.startsWith(stem) || text.contains(" $stem") || text.contains("$stem ")
    }
}