package com.jarvis.mobile.ai

import android.content.Context
import com.jarvis.mobile.memory.MemoryStore
import com.jarvis.mobile.security.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class Brain(private val context: Context) {
    private val prefs = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
    private val secrets = SecretStore(context)
    private val memory = MemoryStore(context)
    fun endpoint() = prefs.getString("endpoint", "https://api.anthropic.com/v1/messages") ?: ""
    fun apiKey() = if (secrets.get("api_key").isNotBlank()) "••••••••••••" else ""
    fun rawApiKey() = secrets.get("api_key")
    fun model() = prefs.getString("model", "claude-sonnet-5") ?: "claude-sonnet-5"
    fun saveConfig(endpoint: String, key: String, model: String) {
        prefs.edit().putString("endpoint", endpoint).putString("model", model).apply()
        if (key.isNotBlank() && !key.startsWith("••")) secrets.put("api_key", key)
    }

    suspend fun think(input: String, screen: String = ""): String = withContext(Dispatchers.IO) {
        val key = rawApiKey()
        if (key.isBlank()) return@withContext JSONObject().put("reply", "Brak klucza API. Dodaj go w ustawieniach JARVIS.").put("steps", JSONArray()).toString()
        val system = """
You are JARVIS Mobile, a cautious autonomous Android agent operating in an observe-think-act loop. After you return steps, they are executed, the screen is re-captured, and you are called again automatically with the updated screen — unless you set done=true.
Return ONLY valid JSON: {"reply":"...","steps":[{"action":"tap_text|tap_xy|type|back|home|open_url|wait|speak","value":"...","confirmation":false}],"remember":"","done":true}.
Set "done":false only when the goal clearly needs more turns after these steps run (e.g. you tapped into an app and still need to find/act on something once it opens). Set "done":true once the goal is achieved, cannot be achieved, needs user confirmation/input you cannot provide, or you are unsure what to do next — never loop with empty or guessed steps.
Use only actions listed. Never ask for, expose, copy, or infer passwords, OTPs, API keys or other secrets. Do not bypass security, CAPTCHAs, payments, account protections, or app safeguards.
Require confirmation=true for sending messages/emails, purchases, deleting data, changing account/security settings, posting publicly, or any irreversible action.
Prefer tap_text over coordinates. Keep plans short and executable. If the screen does not contain the target and no action can help, explain it in reply, set done=true, and do not guess.
MEMORY:
${memory.recent()}
SCREEN:
$screen
""".trimIndent()
        val body = JSONObject()
            .put("model", model())
            .put("max_tokens", 1800)
            .put("system", system)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", input)))
        val conn = URL(endpoint()).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("x-api-key", key)
        conn.setRequestProperty("anthropic-version", "2023-06-01")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 15000; conn.readTimeout = 45000; conn.doOutput = true
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().readText()
        if (code !in 200..299) return@withContext JSONObject().put("reply", "Anthropic API error $code").put("steps", JSONArray()).toString()
        val blocks = JSONObject(text).optJSONArray("content") ?: JSONArray()
        val answer = buildString { for (i in 0 until blocks.length()) if (blocks.getJSONObject(i).optString("type") == "text") append(blocks.getJSONObject(i).optString("text")) }
        memory.add("USER: $input | RESULT: ${answer.take(350)}")
        answer
    }
}
