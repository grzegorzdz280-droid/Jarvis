package com.jarvis.mobile

import android.Manifest
import android.app.AlertDialog
import android.content.*
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.jarvis.mobile.ai.Brain
import com.jarvis.mobile.automation.JarvisAccessibilityService
import com.jarvis.mobile.memory.ContactsStore
import com.jarvis.mobile.overlay.HudOverlayService
import com.jarvis.mobile.service.JarvisForegroundService
import com.jarvis.mobile.tools.SystemTools
import com.jarvis.mobile.voice.VoiceIO
import kotlinx.coroutines.*
import org.json.JSONObject

class MainActivity : AppCompatActivity(), VoiceIO.ActivityLauncher {

    private lateinit var brain: Brain
    private lateinit var voice: VoiceIO
    private lateinit var tools: SystemTools
    private lateinit var log: TextView
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var askButton: Button

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loopJob: Job? = null
    private var speechCallback: ((String) -> Unit)? = null
    private var conversationMode = true
    private var alwaysVision = false

    private val listenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.jarvis.mobile.ACTION_LISTEN") {
                listen()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        brain = Brain(this)
        voice = VoiceIO(this)
        tools = SystemTools(this)
        buildUi()

        val svc = Intent(this, JarvisForegroundService::class.java)
        ContextCompat.startForegroundService(this, svc)

        if (Settings.canDrawOverlays(this)) {
            startService(Intent(this, HudOverlayService::class.java))
        }

        registerReceiver(
            listenReceiver,
            IntentFilter("com.jarvis.mobile.ACTION_LISTEN"),
            RECEIVER_NOT_EXPORTED
        )

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS),
                44
            )
        }

        if (intent?.action == Intent.ACTION_ASSIST) {
            listen()
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 24, 28, 20)
            setBackgroundColor(Color.rgb(5, 7, 10))
        }

        val title = TextView(this).apply {
            text = "J A R V I S"
            textSize = 32f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        status = TextView(this).apply {
            text = "AI MOBILE AGENT • READY"
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 18)
        }

        input = EditText(this).apply {
            hint = "Powiedz Jarvisowi, co ma zrobić…"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            minLines = 2
        }

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        fun btn(t: String) = Button(this).apply {
            text = t
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(38, 42, 52))
            textSize = 13f
        }

        val ask = btn("EXECUTE").apply { setOnClickListener { execute(input.text.toString()) } }
        val mic = btn("🎙").apply { setOnClickListener { listen() } }
        val settings = btn("⚙").apply { setOnClickListener { configDialog() } }

        askButton = ask
        row.addView(ask, LinearLayout.LayoutParams(0, 110, 2f))
        row.addView(mic, LinearLayout.LayoutParams(0, 110, 1f))
        row.addView(settings, LinearLayout.LayoutParams(0, 110, 1f))

        log = TextView(this).apply {
            text = "System ready.\n"
            textSize = 13.5f
            setTextColor(Color.LTGRAY)
            setPadding(0, 16, 0, 0)
        }

        val scroll = ScrollView(this).apply { addView(log) }

        root.addView(title)
        root.addView(status)
        root.addView(input, LinearLayout.LayoutParams(-1, 130))
        root.addView(row)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun execute(goal: String) {
        if (loopJob?.isActive == true) {
            loopJob?.cancel()
            finishLoop("ZATRZYMANO")
            return
        }
        if (goal.isBlank()) return

        append("USER: $goal")
        askButton.text = "STOP"
        input.setText("")

        loopJob = scope.launch {
            var currentInput = goal
            var iterations = 0
            val maxIterations = 14

            while (iterations < maxIterations) {
                iterations++
                status.text = "AI MOBILE AGENT • MYŚLĘ ($iterations/$maxIterations)"

                val screen = JarvisAccessibilityService.instance?.screenSummary()
                    ?: "Accessibility disabled"

                val useVision = alwaysVision ||
                        currentInput.contains("instagram", true) ||
                        currentInput.contains("tiktok", true) ||
                        currentInput.contains("story", true) ||
                        currentInput.contains("ekran", true) ||
                        currentInput.contains("zrzut", true) ||
                        currentInput.contains("co widzisz", true) ||
                        iterations > 2

                val raw = brain.think(currentInput, screen, useVision)
                append("AI: $raw")

                status.text = "AI MOBILE AGENT • WYKONUJĘ"
                val done = runPlan(raw)
                if (done) break

                currentInput = "Kontynuuj cel: \"$goal\". Sprawdź nowy stan ekranu i zrób następny krok."
                delay(1000)
            }
            finishLoop("READY")

            if (conversationMode) {
                delay(700)
                status.text = "AI MOBILE AGENT • SŁUCHAM…"
                listen()
            }
        }
    }

    private fun finishLoop(label: String) {
        status.text = "AI MOBILE AGENT • $label"
        askButton.text = "EXECUTE"
    }

    private fun runPlan(raw: String): Boolean {
        return try {
            val obj = JSONObject(raw)
            val reply = obj.optString("reply", "")
            if (reply.isNotBlank()) voice.speak(reply)

            val arr = obj.optJSONArray("steps")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val s = arr.getJSONObject(i)
                    val action = s.optString("action")
                    val value = s.optString("value")
                    val needConfirm = s.optBoolean("confirmation", false)

                    if (needConfirm) {
                        confirm(action, value) { perform(action, value) }
                    } else {
                        perform(action, value)
                    }
                }
            }
            obj.optBoolean("done", true) || arr == null || arr.length() == 0
        } catch (e: Exception) {
            append("Błąd: ${e.message}")
            true
        }
    }

    private fun confirm(a: String, v: String, yes: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("JARVIS – potwierdzenie")
            .setMessage("$a\n$v")
            .setPositiveButton("WYKONAJ") { _, _ -> yes() }
            .setNegativeButton("ANULUJ", null)
            .show()
    }

    private fun perform(a: String, v: String) {
        try {
            val s = JarvisAccessibilityService.instance
            when (a) {
                "tap_text" -> s?.tapText(v)
                "long_press_text" -> s?.longPressText(v)
                "tap_xy" -> {
                    val nums = Regex("-?\\d+(?:\\.\\d+)?").findAll(v).map { it.value.toFloat() }.toList()
                    if (nums.size >= 2) s?.tap(nums[0], nums[1])
                }
                "long_press_xy" -> {
                    val nums = Regex("-?\\d+(?:\\.\\d+)?").findAll(v).map { it.value.toFloat() }.toList()
                    if (nums.size >= 2) s?.longPress(nums[0], nums[1])
                }
                "swipe" -> {
                    val nums = Regex("-?\\d+(?:\\.\\d+)?").findAll(v).map { it.value.toFloat() }.toList()
                    if (nums.size >= 4) {
                        val duration = if (nums.size >= 5) nums[4].toLong() else 400L
                        s?.swipe(nums[0], nums[1], nums[2], nums[3], duration)
                    }
                }
                "type" -> s?.typeText(v)
                "back" -> s?.back()
                "home" -> s?.home()
                "recent_apps" -> s?.recentApps()
                "notifications" -> s?.notifications()
                "open_app" -> s?.openApp(v)
                "open_url" -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(v)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "open_settings" -> startActivity(Intent(settingsAction(v)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "scroll_forward" -> s?.scrollForward()
                "scroll_backward" -> s?.scrollBackward()
                "scroll_to_text" -> s?.scrollToText(v)
                "wait" -> Thread.sleep(v.toLongOrNull() ?: 600)
                "speak" -> voice.speak(v)

                "get_time" -> voice.speak(tools.getTime())
                "get_battery" -> voice.speak(tools.getBattery())
                "get_volume" -> voice.speak(tools.getVolume())
                "set_volume" -> voice.speak(tools.setVolume(v.toIntOrNull() ?: 50))
                "get_clipboard" -> voice.speak(tools.getClipboard())
                "set_clipboard" -> voice.speak(tools.setClipboard(v))
                "get_brightness" -> voice.speak(tools.getBrightness())

                else -> append("UNKNOWN: $a")
            }
            append("ACTION: $a → $v")
        } catch (e: Exception) {
            append("FAILED: $a — ${e.message}")
        }
    }

    private fun settingsAction(key: String): String = when (key.lowercase()) {
        "wifi" -> Settings.ACTION_WIFI_SETTINGS
        "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
        "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
        "sound" -> Settings.ACTION_SOUND_SETTINGS
        "display" -> Settings.ACTION_DISPLAY_SETTINGS
        "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
        "apps" -> Settings.ACTION_APPLICATION_SETTINGS
        "date_time" -> Settings.ACTION_DATE_SETTINGS
        "security" -> Settings.ACTION_SECURITY_SETTINGS
        "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
        "notifications" -> Settings.ACTION_APP_NOTIFICATION_SETTINGS
        "airplane_mode" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
        else -> Settings.ACTION_SETTINGS
    }

    private fun append(x: String) {
        log.append("\n$x")
    }

    private fun listen() {
        voice.onResult = {
            if (it.isNotBlank()) {
                input.setText(it)
                execute(it)
            }
        }
        voice.listen()
    }

    private fun configDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 8)
        }

        val ep = EditText(this).apply {
            hint = "Endpoint (Anthropic / xAI / OpenRouter / OpenAI)"
            setText(brain.endpoint())
        }
        val key = EditText(this).apply {
            hint = "API key"
            setText(brain.apiKey())
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val model = EditText(this).apply {
            hint = "Model (claude-sonnet-4 / gpt-4o / grok-2-vision...)"
            setText(brain.model())
        }

        val convCheck = CheckBox(this).apply {
            text = "Tryb rozmowy (auto-nasłuchiwanie)"
            isChecked = conversationMode
            setTextColor(Color.WHITE)
        }

        val visionCheck = CheckBox(this).apply {
            text = "Zawsze używaj Vision (screenshot)"
            isChecked = alwaysVision
            setTextColor(Color.WHITE)
        }

        layout.addView(ep)
        layout.addView(key)
        layout.addView(model)
        layout.addView(convCheck)
        layout.addView(visionCheck)

        AlertDialog.Builder(this)
            .setTitle("JARVIS AI Core")
            .setView(layout)
            .setPositiveButton("SAVE") { _, _ ->
                brain.saveConfig(ep.text.toString(), key.text.toString(), model.text.toString())
                conversationMode = convCheck.isChecked
                alwaysVision = visionCheck.isChecked
                append("Konfiguracja zapisana")
            }
            .setNeutralButton("KONTAKTY") { _, _ ->
                showContactsDialog()
            }
            .setNegativeButton("ACCESSIBILITY") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .show()
    }

    private fun showContactsDialog() {
        val contacts = ContactsStore(this)
        val current = contacts.asPromptText()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 8)
        }

        val info = TextView(this).apply {
            text = "Zapisane kontakty:\n$current"
            setTextColor(Color.LTGRAY)
            setPadding(0, 0, 0, 20)
        }

        val alias = EditText(this).apply { hint = "Alias (np. mama, szef, żona)" }
        val fullName = EditText(this).apply { hint = "Pełna nazwa w aplikacji (np. Anna Kowalska)" }

        layout.addView(info)
        layout.addView(alias)
        layout.addView(fullName)

        AlertDialog.Builder(this)
            .setTitle("Kontakty JARVIS")
            .setView(layout)
            .setPositiveButton("DODAJ / ZMIEŃ") { _, _ ->
                val a = alias.text.toString().trim()
                val n = fullName.text.toString().trim()
                if (a.isNotBlank() && n.isNotBlank()) {
                    contacts.put(a, n)
                    append("Zapisano kontakt: $a → $n")
                    voice.speak("Zapamiętałem. $a to $n")
                }
            }
            .setNeutralButton("USUŃ") { _, _ ->
                val a = alias.text.toString().trim()
                if (a.isNotBlank()) {
                    contacts.remove(a)
                    append("Usunięto kontakt: $a")
                }
            }
            .setNegativeButton("ZAMKNIJ", null)
            .show()
    }

    override fun launchSpeech(intent: Intent, callback: (String) -> Unit) {
        speechCallback = callback
        @Suppress("DEPRECATION")
        startActivityForResult(intent, 1001)
    }

    @Deprecated("Deprecated")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            val text = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: ""
            speechCallback?.invoke(text)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        voice.shutdown()
        try { unregisterReceiver(listenReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }
}
