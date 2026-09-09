package com.jarvis.mobile.voice
import android.content.*
import android.os.Bundle
import android.speech.*
import android.speech.tts.TextToSpeech
import java.util.*
class VoiceIO(private val context:Context): TextToSpeech.OnInitListener {
    private var tts=TextToSpeech(context,this); var onResult:((String)->Unit)?=null
    override fun onInit(status:Int){ if(status==TextToSpeech.SUCCESS) tts.language=Locale.getDefault() }
    fun speak(text:String){ if(text.isBlank())return; tts.speak(text,TextToSpeech.QUEUE_FLUSH,Bundle(),"jarvis") }
    fun listen(){ val i=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);putExtra(RecognizerIntent.EXTRA_PROMPT,"JARVIS listening")}; val launcher=context as? ActivityLauncher ?: return; launcher.launchSpeech(i){ onResult?.invoke(it) } }
    interface ActivityLauncher { fun launchSpeech(intent:Intent, callback:(String)->Unit) }
}
