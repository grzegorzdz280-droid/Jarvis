package com.jarvis.mobile.memory

import android.content.Context

class ContactsStore(context: Context) {

    private val prefs = context.getSharedPreferences("jarvis_contacts", Context.MODE_PRIVATE)

    fun put(alias: String, fullName: String) {
        val key = alias.trim().lowercase()
        if (key.isBlank() || fullName.isBlank()) return
        prefs.edit().putString(key, fullName.trim()).apply()
    }

    fun get(alias: String): String? {
        return prefs.getString(alias.trim().lowercase(), null)
    }

    fun remove(alias: String) {
        prefs.edit().remove(alias.trim().lowercase()).apply()
    }

    fun all(): Map<String, String> {
        return prefs.all.mapValues { it.value.toString() }
    }

    fun asPromptText(): String {
        val map = all()
        if (map.isEmpty()) return "brak zapisanych kontaktów"
        return map.entries.joinToString("\n") { "- \"${it.key}\" → ${it.value}" }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
