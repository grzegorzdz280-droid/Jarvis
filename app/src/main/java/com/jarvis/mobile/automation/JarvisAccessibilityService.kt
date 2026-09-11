package com.jarvis.mobile.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: JarvisAccessibilityService? = null
    }

    override fun onServiceConnected() { instance = this }
    override fun onInterrupt() {}
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    fun screenSummary(): String {
        val root = rootInActiveWindow ?: return "No active window"
        val out = StringBuilder()
        walk(root, out, 0)
        return out.toString().take(10000)
    }

    private fun walk(n: AccessibilityNodeInfo, out: StringBuilder, depth: Int) {
        if (depth > 14) return
        val text = (n.text ?: n.contentDescription)?.toString()?.trim()
        val rect = Rect()
        n.getBoundsInScreen(rect)
        val at = "@(\( {rect.centerX()}, \){rect.centerY()})"
        val checkInfo = if (n.isCheckable) " checked=${n.isChecked}" else ""
        val clickable = if (n.isClickable) " clickable" else ""

        if (!text.isNullOrBlank()) {
            out.append("[${simpleClass(n)}] \"$text\" $at$checkInfo$clickable\n")
        } else if ((n.isCheckable || n.isClickable) && rect.width() > 10 && rect.height() > 10) {
            out.append("[${simpleClass(n)}] (no label) $at$checkInfo$clickable\n")
        }

        for (i in 0 until n.childCount) {
            n.getChild(i)?.let { walk(it, out, depth + 1) }
        }
    }

    private fun simpleClass(n: AccessibilityNodeInfo) =
        n.className?.toString()?.substringAfterLast('.') ?: "?"

    fun tapText(text: String): Boolean {
        val node = find(rootInActiveWindow, text) ?: return false
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    fun longPressText(text: String): Boolean {
        val node = find(rootInActiveWindow, text) ?: return false
        return node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
    }

    fun tap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        return dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 70))
                .build(), null, null
        )
    }

    fun longPress(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        return dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 650))
                .build(), null, null
        )
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 420): Boolean {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        return dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
                .build(), null, null
        )
    }

    fun typeText(text: String): Boolean {
        val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun scrollForward() = rootInActiveWindow?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true
    fun scrollBackward() = rootInActiveWindow?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) == true

    fun back() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun home() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun recentApps() = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun notifications() = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    fun scrollToText(text: String, maxScrolls: Int = 8): Boolean {
        repeat(maxScrolls) {
            if (find(rootInActiveWindow, text) != null) return true
            scrollForward()
            Thread.sleep(400)
        }
        return find(rootInActiveWindow, text) != null
    }

    fun openApp(packageOrName: String): Boolean {
        return try {
            val pm = packageManager
            var intent = pm.getLaunchIntentForPackage(packageOrName)

            if (intent == null) {
                val aliases = mapOf(
                    "whatsapp" to "com.whatsapp",
                    "telegram" to "org.telegram.messenger",
                    "instagram" to "com.instagram.android",
                    "facebook" to "com.facebook.katana",
                    "messenger" to "com.facebook.orca",
                    "twitter" to "com.twitter.android",
                    "x" to "com.twitter.android",
                    "tiktok" to "com.zhiliaoapp.musically",
                    "youtube" to "com.google.android.youtube",
                    "discord" to "com.discord",
                    "signal" to "org.thoughtcrime.securesms"
                )
                val lower = packageOrName.lowercase()
                aliases[lower]?.let {
                    intent = pm.getLaunchIntentForPackage(it)
                }
            }

            if (intent == null) {
                val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                for (app in apps) {
                    val label = pm.getApplicationLabel(app).toString()
                    if (label.contains(packageOrName, ignoreCase = true)) {
                        intent = pm.getLaunchIntentForPackage(app.packageName)
                        break
                    }
                }
            }

            if (intent != null) {
                intent!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    fun listInstalledApps(): String {
        return try {
            val pm = packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val list = mutableListOf<String>()
            for (app in apps) {
                if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                    val label = pm.getApplicationLabel(app).toString()
                    list.add("\( label ( \){app.packageName})")
                }
            }
            list.sorted().take(160).joinToString("\n")
        } catch (e: Exception) {
            "Cannot list apps: ${e.message}"
        }
    }

    private fun find(n: AccessibilityNodeInfo?, q: String): AccessibilityNodeInfo? {
        if (n == null) return null
        val t = n.text?.toString()
        val d = n.contentDescription?.toString()
        if (t?.contains(q, ignoreCase = true) == true || d?.contains(q, ignoreCase = true) == true) {
            return n
        }
        for (i in 0 until n.childCount) {
            val r = find(n.getChild(i), q)
            if (r != null) return r
        }
        return null
    }
}
