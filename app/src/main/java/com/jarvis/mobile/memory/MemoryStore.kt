package com.jarvis.mobile.memory

import android.content.Context
import org.json.JSONArray

class MemoryStore(context: Context) {
    private val p = context.getSharedPreferences("jarvis_memory", Context.MODE_PRIVATE)
    fun add(text: String) {
        val a = JSONArray(p.getString("items", "[]"))
        a.put(text.take(500))
        while (a.length() > 30) a.remove(0)
        p.edit().putString("items", a.toString()).apply()
    }
    fun recent(): String {
        val a = JSONArray(p.getString("items", "[]"))
        val out = StringBuilder()
        for (i in maxOf(0, a.length()-8) until a.length()) out.append("- ").append(a.optString(i)).append('\n')
        return out.toString()
    }
}
