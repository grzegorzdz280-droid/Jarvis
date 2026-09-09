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
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    override fun onCreate(b:Bundle?){super.onCreate(b); brain=Brain(this);voice=VoiceIO(this); buildUi(); if(android.os.Build.VERSION.SDK_INT>=33) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO,Manifest.permission.POST_NOTIFICATIONS),44)}
    private fun buildUi(){
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(28,24,28,20);setBackgroundColor(Color.rgb(5,7,10))}
        val title=TextView(this).apply{text="J A R V I S";textSize=30f;setTextColor(Color.WHITE);gravity=Gravity.CENTER}
        val status=TextView(this).apply{text="AI MOBILE AGENT • READY";setTextColor(Color.LTGRAY);gravity=Gravity.CENTER;setPadding(0,8,0,18)}
        input=EditText(this).apply{hint="Powiedz Jarvisowi, co ma zrobić…";setTextColor(Color.WHITE);setHintTextColor(Color.GRAY);setSingleLine(false)}
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        val ask=Button(this).apply{text="EXECUTE";setOnClickListener{execute(input.text.toString())}}
        val mic=Button(this).apply{text="🎙";setOnClickListener{listen()}}
        val settings=Button(this).apply{text="⚙";setOnClickListener{configDialog()}}
        row.addView(ask,LinearLayout.LayoutParams(0,60,2f));row.addView(mic,LinearLayout.LayoutParams(0,60,1f));row.addView(settings,LinearLayout.LayoutParams(0,60,1f))
        log=TextView(this).apply{text="System ready.\n";textSize=14f;setTextColor(Color.LTGRAY);setPadding(0,20,0,0)}
        val scroll=ScrollView(this).apply{addView(log)}
        root.addView(title);root.addView(status);root.addView(input,LinearLayout.LayoutParams(-1,120));root.addView(row);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f));setContentView(root)
    }
    private fun execute(goal:String){if(goal.isBlank())return; append("USER: $goal"); scope.launch{val svc=JarvisAccessibilityService.instance;val screen=svc?.screenSummary() ?: "Accessibility disabled"; val raw=brain.think(goal,screen); append("AI: $raw"); runPlan(raw)}}
    private fun runPlan(raw:String){try{val obj=JSONObject(raw);voice.speak(obj.optString("reply","Done"));val arr=obj.optJSONArray("steps")?:return;for(i in 0 until arr.length()){val s=arr.getJSONObject(i);val action=s.optString("action");val value=s.optString("value");if(s.optBoolean("confirmation")){confirm(action,value){perform(action,value)}}else perform(action,value)}}catch(e:Exception){append("Planner output is not valid JSON; no automatic UI action was executed.")}}
    private fun confirm(a:String,v:String,yes:()->Unit){AlertDialog.Builder(this).setTitle("JARVIS confirmation").setMessage("Wykonać: $a $v ?").setPositiveButton("WYKONAJ"){_,_->yes()}.setNegativeButton("ANULUJ",null).show()}
    private fun perform(a:String,v:String){val s=JarvisAccessibilityService.instance;when(a){"tap_text"->s?.tapText(v);"tap_xy"->{val p=v.split(",");if(p.size==2)s?.tap(p[0].toFloat(),p[1].toFloat())};"back"->s?.back();"home"->s?.home();"type"->s?.typeText(v);"open_url"->startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(v)));"wait"->Thread.sleep(v.toLongOrNull()?:500);"speak"->voice.speak(v)};append("ACTION: $a $v")}
    private fun append(x:String){log.append("\n$x")}
    private fun listen(){voice.onResult={input.setText(it);execute(it)};voice.listen()}
    private fun configDialog(){val e=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,8,24,0)};val ep=EditText(this).apply{hint="AI endpoint";setText(brain.endpoint())};val key=EditText(this).apply{hint="API key";setText(brain.apiKey());inputType=129};val model=EditText(this).apply{hint="Model";setText(brain.model())};e.addView(ep);e.addView(key);e.addView(model);AlertDialog.Builder(this).setTitle("JARVIS AI Core").setView(e).setPositiveButton("SAVE"){_,_->brain.saveConfig(ep.text.toString(),key.text.toString(),model.text.toString());append("AI configuration saved")}.setNeutralButton("ACCESSIBILITY"){_,_->startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}.setNegativeButton("CANCEL",null).show()}
    override fun launchSpeech(intent:Intent,callback:(String)->Unit){speechCallback=callback;startActivityForResult(intent,1001)}
    private var speechCallback:((String)->Unit)?=null
    override fun onActivityResult(r:Int,c:Int,d:Intent?){super.onActivityResult(r,c,d);if(r==1001&&c==RESULT_OK){speechCallback?.invoke(d?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: "")}}
    override fun onDestroy(){scope.cancel();voice.speak("");super.onDestroy()}
}
