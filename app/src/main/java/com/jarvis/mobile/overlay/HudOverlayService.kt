package com.jarvis.mobile.overlay

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class HudOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showOverlay()
    }

    private fun showOverlay() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#CC0A0C10"))
            setPadding(24, 14, 24, 14)
            elevation = 12f
        }

        val status = TextView(this).apply {
            text = "JARVIS"
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(0, 0, 20, 0)
        }

        val mic = TextView(this).apply {
            text = "🎙"
            textSize = 18f
            setOnClickListener {
                val i = Intent("com.jarvis.mobile.ACTION_LISTEN")
                sendBroadcast(i)
                Toast.makeText(this@HudOverlayService, "Słucham…", Toast.LENGTH_SHORT).show()
            }
        }

        layout.addView(status)
        layout.addView(mic)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 30
            y = 120
        }

        overlayView = layout
        windowManager?.addView(layout, params)
    }

    override fun onDestroy() {
        overlayView?.let { windowManager?.removeView(it) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
