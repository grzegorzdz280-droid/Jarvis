package com.jarvis.mobile.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.AudioManager
import android.os.BatteryManager
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.*

class SystemTools(private val context: Context) {

    fun getTime(): String {
        val sdf = SimpleDateFormat("HH:mm, EEEE d MMMM yyyy", Locale("pl", "PL"))
        return sdf.format(Date())
    }

    fun getBattery(): String {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return "Bateria: $level%"
    }

    fun getVolume(): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return "Głośność mediów: $current / $max"
    }

    fun setVolume(percent: Int): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (max * (percent.coerceIn(0, 100) / 100f)).toInt()
        am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        return "Ustawiono głośność na $percent%"
    }

    fun getClipboard(): String {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return cm.primaryClip?.getItemAt(0)?.text?.toString() ?: "Schowek pusty"
    }

    fun setClipboard(text: String): String {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("jarvis", text))
        return "Skopiowano do schowka"
    }

    fun getBrightness(): String {
        return try {
            val b = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            "Jasność: $b / 255"
        } catch (e: Exception) {
            "Nie udało się odczytać jasności"
        }
    }
}
