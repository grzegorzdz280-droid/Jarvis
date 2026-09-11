package com.jarvis.mobile.ai

import android.content.Context
import com.jarvis.mobile.automation.JarvisAccessibilityService
import com.jarvis.mobile.core.NotificationStore
import com.jarvis.mobile.memory.ContactsStore
import com.jarvis.mobile.memory.MemoryStore
import com.jarvis.mobile.security.SecretStore
import com.jarvis.mobile.vision.ScreenCapture
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
    private val contacts = ContactsStore(context)

    fun endpoint() = prefs.getString("endpoint", "https://api.anthropic.com/v1/messages") ?: ""
    fun apiKey() = if (secrets.get("api_key").isNotBlank()) "••••••••••••" else ""
    fun rawApiKey() = secrets.get("api_key")
    fun model() = prefs.getString("model", "claude-sonnet-4-20250514") ?: "claude-sonnet-4-20250514"

    fun saveConfig(endpoint: String, key: String, model: String) {
        prefs.edit()
            .putString("endpoint", endpoint.trim())
            .putString("model", model.trim())
            .apply()
        if (key.isNotBlank() && !key.startsWith("••")) {
            secrets.put("api_key", key.trim())
        }
    }

    suspend fun think(input: String, screen: String = "", useVision: Boolean = false): String = withContext(Dispatchers.IO) {
        val key = rawApiKey()
        if (key.isBlank()) {
            return@withContext JSONObject()
                .put("reply", "Brak klucza API. Wejdź w ustawienia ⚙.")
                .put("steps", JSONArray())
                .put("done", true)
                .toString()
        }

        val apps = try {
            JarvisAccessibilityService.instance?.listInstalledApps() ?: "Accessibility wyłączone"
        } catch (_: Exception) {
            "Nie udało się pobrać listy aplikacji"
        }

        val notif = NotificationStore.latest.ifBlank { "brak nowych powiadomień" }

        var imageBase64: String? = null
        if (useVision) {
            imageBase64 = ScreenCapture.takeScreenshotBase64()
        }

        val system = """
You are JARVIS Mobile — a careful, highly capable autonomous Android agent specialized in personal automation and social media apps.

You operate in an observe → think → act loop.
After returning steps they are executed and you are called again with the new screen (unless "done": true).

Return ONLY valid JSON:
{
  "reply": "short natural spoken reply in Polish",
  "steps": [
    {"action": "...", "value": "...", "confirmation": false}
  ],
  "remember": "optional short fact",
  "done": true
}

==================== AVAILABLE ACTIONS ====================
UI:
- tap_text, long_press_text, tap_xy, long_press_xy, swipe, type
- back, home, recent_apps, notifications, scroll_forward, scroll_backward, scroll_to_text
- open_app, open_url, open_settings, wait, speak

System:
- get_time, get_battery, get_volume, set_volume, get_clipboard, set_clipboard, get_brightness

==================== SAVED CONTACTS ====================
${contacts.asPromptText()}

When user uses alias (mama, szef...), use the full name from the list above.
If user says "Zapamiętaj, że X to Y" → put in remember: CONTACT:X=Y

==================== SOCIAL MEDIA ====================
Excellent at WhatsApp, Telegram, Instagram, Messenger, Facebook, X, TikTok, Discord, Signal.
Always confirmation=true before sending any message, post, comment or like.

==================== VISION ====================
When a screenshot is provided, carefully describe what you see and use it to decide precise taps.
Prefer vision coordinates when text is not reliable (icons, stories, complex UIs).

==================== SAFETY ====================
- confirmation=true for EVERY message, post, comment, like, deletion, payment.
- Never try to bypass login, 2FA, CAPTCHA, OTP or account protection.
- Never invent coordinates.
- Prefer tap_text when text is clearly visible.
- Keep turns short (1-5 steps).

MEMORY:
${memory.recent()}

LATEST NOTIFICATION:
$notif

INSTALLED APPS:
$apps

CURRENT SCREEN (text from Accessibility):
$screen
""".trimIndent()

        val isAnthropic = endpoint().contains("anthropic", ignoreCase = true)

        val answer = if (isAnthropic) {
            callAnthropic(key, system, input, imageBase64)
        } else {
            callOpenAICompatible(key, system, input, imageBase64)
        }

        try {
            val obj = JSONObject(answer)
            val remember = obj.optString("remember", "")
            if (remember.startsWith("CONTACT:", ignoreCase = true)) {
                val part = remember.removePrefix("CONTACT:").trim()
                val parts = part.split("=", limit = 2)
                if (parts.size == 2) {
                    contacts.put(parts[0].trim(), parts[1].trim())
                }
            }
        } catch (_: Exception) {}

        answer
    }

    private fun callAnthropic(key: String, system: String, input: String, imageBase64: String?): String {
        val content = JSONArray()

        if (imageBase64 != null) {
            content.put(JSONObject()
                .put("type", "image")
                .put("source", JSONObject()
                    .put("type", "base64")
                    .put("media_type", "image/jpeg")
                    .put("data", imageBase64)
                )
            )
        }

        content.put(JSONObject().put("type", "text").put("text", input))

        val body = JSONObject()
            .put("model", model())
            .put("max_tokens", 2500)
            .put("system", system)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))

        val conn = URL(endpoint()).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("x-api-key", key)
        conn.setRequestProperty("anthropic-version", "2023-06-01")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 25000
        conn.readTimeout = 90000
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toString().toByteArray()) }

        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().readText()
        if (code !in 200..299) {
            return JSONObject().put("reply", "API error $code").put("steps", JSONArray()).put("done", true).toString()
        }

        val blocks = JSONObject(text).optJSONArray("content") ?: JSONArray()
        val answer = buildString {
            for (i in 0 until blocks.length()) {
                if (blocks.getJSONObject(i).optString("type") == "text") {
                    append(blocks.getJSONObject(i).optString("text"))
                }
            }
        }
        memory.add("USER: $input | AI: ${answer.take(500)}")
        return answer
    }

    private fun callOpenAICompatible(key: String, system: String, input: String, imageBase64: String?): String {
        val userContent = JSONArray()

        if (imageBase64 != null) {
            userContent.put(JSONObject()
                .put("type", "image_url")
                .put("image_url", JSONObject()
                    .put("url", "data:image/jpeg;base64,$imageBase64")
                )
            )
        }
        userContent.put(JSONObject().put("type", "text").put("text", input))

        val body = JSONObject()
            .put("model", model())
            .put("max_tokens", 2500)
            .put("temperature", 0.15)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", userContent))
            )

        val conn = URL(endpoint()).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "Bearer $key")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 25000
        conn.readTimeout = 90000
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toString().toByteArray()) }

        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().readText()
        if (code !in 200..299) {
            return JSONObject().put("reply", "API error $code").put("steps", JSONArray()).put("done", true).toString()
        }

        val choices = JSONObject(text).optJSONArray("choices") ?: JSONArray()
        val answer = if (choices.length() > 0) {
            choices.getJSONObject(0).optJSONObject("message")?.optString("content") ?: ""
        } else ""
        memory.add("USER: $input | AI: ${answer.take(500)}")
        return answer
    }
}
