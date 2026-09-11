package com.jarvis.mobile.memory

import android.content.Context
import org.json.JSONArray

class MemoryStore(context: Context) {

    private val prefs = context.getSharedPreferences("jarvis_memory", Context.MODE_PRIVATE)

    fun add(text: String) {
        val arr = JSONArray(prefs.getString("items", "[]"))
        arr.put(text.take(600))
        while (arr.length() > 40) {
            arr.remove(0)
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }

    fun recent(): String {
        val arr = JSONArray(prefs.getString("items", "[]"))
        if (arr.length() == 0) return "brak"
        val out = StringBuilder()
        val start = maxOf(0, arr.length() - 10)
        for (i in start until arr.length()) {
            out.append("- ").append(arr.optString(i)).append('\n')
        }
        return out.toString()
    }

    fun clear() {
        prefs.edit().remove("items").apply()
    }
}
