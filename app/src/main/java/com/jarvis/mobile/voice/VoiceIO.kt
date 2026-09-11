package com.jarvis.mobile.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceIO(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    var onResult: ((String) -> Unit)? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
            tts?.setSpeechRate(1.05f)
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), "jarvis")
    }

    fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "JARVIS listening…")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        val launcher = context as? ActivityLauncher ?: return
        launcher.launchSpeech(intent) { result ->
            onResult?.invoke(result)
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    interface ActivityLauncher {
        fun launchSpeech(intent: Intent, callback: (String) -> Unit)
    }
}
