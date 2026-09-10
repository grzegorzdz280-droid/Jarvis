package com.jarvis.mobile

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.mobile.ai.Brain
import com.jarvis.mobile.automation.JarvisAccessibilityService
import com.jarvis.mobile.core.NotificationStore
import com.jarvis.mobile.voice.VoiceIO
import kotlinx.coroutines.*
import org.json.JSONObject

class MainActivity: AppCompatActivity(), VoiceIO.ActivityLauncher {
    private lateinit var brain:Brain; private lateinit var voice:VoiceIO; private lateinit var log:TextView; private lateinit var input:EditText
    private lateinit var status:TextView; private lateinit var askButton:Button
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    private var loopJob:Job?=null
    override fun onCreate(b:Bundle?){super.onCreate(b); brain=Brain(this);voice=VoiceIO(this); buildUi(); if(android.os.Build.VERSION.SDK_INT>=33) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO,Manifest.permission.POST_NOTIFICATIONS),44)}
    private fun buildUi(){
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(28,24,28,20);setBackgroundColor(Color.rgb(5,7,10))}
        val title=TextView(this).apply{text="J A R V I S";textSize=30f;setTextColor(Color.WHITE);gravity=Gravity.CENTER}
        status=TextView(this).apply{text="AI MOBILE AGENT • READY";setTextColor(Color.LTGRAY);gravity=Gravity.CENTER;setPadding(0,8,0,18)}
        input=EditText(this).apply{hint="Powiedz Jarvisowi, co ma zrobić…";setTextColor(Color.WHITE);setHintTextColor(Color.GRAY);setSingleLine(false)}
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        fun styledButton(t:String)=Button(this).apply{text=t;setTextColor(Color.WHITE);setBackgroundColor(Color.rgb(38,42,52));textSize=15f}
        val ask=styledButton("EXECUTE").apply{setOnClickListener{execute(input.text.toString())}}
        val mic=styledButton("🎙").apply{setOnClickListener{listen()}}
        val settings=styledButton("⚙").apply{setOnClickListener{configDialog()}}
        askButton=ask
        row.addView(ask,LinearLayout.LayoutParams(0,110,2f));row.addView(mic,LinearLayout.LayoutParams(0,110,1f));row.addView(settings,LinearLayout.LayoutParams(0,110,1f))
        log=TextView(this).apply{text="System ready.\n";textSize=14f;setTextColor(Color.LTGRAY);setPadding(0,20,0,0)}
        val scroll=ScrollView(this).apply{addView(log)}
        root.addView(title);root.addView(status);root.addView(input,LinearLayout.LayoutParams(-1,120));root.addView(row);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f));setContentView(root)
    }
    private fun execute(goal:String){
        if(loopJob?.isActive==true){loopJob?.cancel();finishLoop("ZATRZYMANO");return}
        if(goal.isBlank())return
        append("USER: $goal")
        askButton.text="STOP"; input.setText("")
        loopJob=scope.launch{
            var currentInput=goal; var iterations=0; val maxIterations=8
            while(iterations<maxIterations){
                iterations++
                status.text="AI MOBILE AGENT • MYŚLĘ ($iterations/$maxIterations)"
                val svc=JarvisAccessibilityService.instance
                val screen=svc?.screenSummary() ?: "Accessibility disabled"
                val raw=brain.think(currentInput,screen)
                append("AI: $raw")
                status.text="AI MOBILE AGENT • WYKONUJĘ"
                val done=runPlan(raw)
                if(done)break
                currentInput="Kontynuuj realizację celu: \"$goal\". To kolejna tura — sprawdź nowy stan ekranu i zrób następny krok."
                delay(1200)
            }
            finishLoop("READY")
        }
    }
    private fun finishLoop(label:String){status.text="AI MOBILE AGENT • $label";askButton.text="EXECUTE"}
    private fun runPlan(raw:String):Boolean{try{val obj=JSONObject(raw);voice.speak(obj.optString("reply","Done"));val arr=obj.optJSONArray("steps");if(arr!=null)for(i in 0 until arr.length()){val s=arr.getJSONObject(i);val action=s.optString("action");val value=s.optString("value");if(s.optBoolean("confirmation")){confirm(action,value){perform(action,value)}}else perform(action,value)};return obj.optBoolean("done",true)||arr==null||arr.length()==0}catch(e:Exception){append("Błąd wykonania: ${e.javaClass.simpleName}: ${e.message}");return true}}
    private fun confirm(a:String,v:String,yes:()->Unit){AlertDialog.Builder(this).setTitle("JARVIS confirmation").setMessage("Wykonać: $a $v ?").setPositiveButton("WYKONAJ"){_,_->yes()}.setNegativeButton("ANULUJ",null).show()}
    private fun perform(a:String,v:String){try{val s=JarvisAccessibilityService.instance;when(a){"tap_text"->s?.tapText(v);"tap_xy"->{val nums=Regex("-?\\d+(?:\\.\\d+)?").findAll(v).map{it.value.toFloat()}.toList();if(nums.size>=2)s?.tap(nums[0],nums[1])};"back"->s?.back();"home"->s?.home();"type"->s?.typeText(v);"open_url"->startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(v)));"open_settings"->startActivity(Intent(settingsAction(v)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));"wait"->Thread.sleep(v.toLongOrNull()?:500);"speak"->voice.speak(v)};append("ACTION: $a $v")}catch(e:Exception){append("ACTION FAILED: $a $v — ${e.javaClass.simpleName}: ${e.message}")}}
    private fun settingsAction(key:String):String=when(key.lowercase()){"wifi"->Settings.ACTION_WIFI_SETTINGS;"bluetooth"->Settings.ACTION_BLUETOOTH_SETTINGS;"location"->Settings.ACTION_LOCATION_SOURCE_SETTINGS;"sound"->Settings.ACTION_SOUND_SETTINGS;"display"->Settings.ACTION_DISPLAY_SETTINGS;"battery"->Settings.ACTION_BATTERY_SAVER_SETTINGS;"apps"->Settings.ACTION_APPLICATION_SETTINGS;"date_time"->Settings.ACTION_DATE_SETTINGS;"security"->Settings.ACTION_SECURITY_SETTINGS;"accessibility"->Settings.ACTION_ACCESSIBILITY_SETTINGS;"notifications"->Settings.ACTION_APP_NOTIFICATION_SETTINGS;"airplane_mode"->Settings.ACTION_AIRPLANE_MODE_SETTINGS;else->Settings.ACTION_SETTINGS}
    private fun append(x:String){log.append("\n$x")}
    private fun listen(){voice.onResult={input.setText(it);execute(it)};voice.listen()}
    private fun configDialog(){val e=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,8,24,0)};val ep=EditText(this).apply{hint="AI endpoint";setText(brain.endpoint())};val key=EditText(this).apply{hint="API key";setText(brain.apiKey());inputType=129};val model=EditText(this).apply{hint="Model";setText(brain.model())};e.addView(ep);e.addView(key);e.addView(model);AlertDialog.Builder(this).setTitle("JARVIS AI Core").setView(e).setPositiveButton("SAVE"){_,_->brain.saveConfig(ep.text.toString(),key.text.toString(),model.text.toString());append("AI configuration saved")}.setNeutralButton("ACCESSIBILITY"){_,_->startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}.setNegativeButton("CANCEL",null).show()}
    override fun launchSpeech(intent:Intent,callback:(String)->Unit){speechCallback=callback;startActivityForResult(intent,1001)}
    private var speechCallback:((String)->Unit)?=null
    override fun onActivityResult(r:Int,c:Int,d:Intent?){super.onActivityResult(r,c,d);if(r==1001&&c==RESULT_OK){speechCallback?.invoke(d?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: "")}}
    override fun onDestroy(){scope.cancel();voice.speak("");super.onDestroy()}
}
