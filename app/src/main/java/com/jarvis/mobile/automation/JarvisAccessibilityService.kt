package com.jarvis.mobile.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*

class JarvisAccessibilityService : AccessibilityService() {
    companion object { var instance: JarvisAccessibilityService? = null }
    override fun onServiceConnected() { instance=this }
    override fun onInterrupt() {}
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    fun screenSummary(): String {
        val root=rootInActiveWindow ?: return "No active window"
        val out=StringBuilder(); walk(root,out,0); return out.toString().take(6000)
    }
    private fun walk(n: AccessibilityNodeInfo, out:StringBuilder, depth:Int) { if(depth>8)return; val text=(n.text ?: n.contentDescription)?.toString()?.trim(); if(!text.isNullOrBlank()) out.append("[${n.className}] $text\n"); for(i in 0 until n.childCount) n.getChild(i)?.let{walk(it,out,depth+1)} }
    fun tapText(text:String):Boolean = find(rootInActiveWindow,text)?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true
    private fun find(n:AccessibilityNodeInfo?, q:String):AccessibilityNodeInfo? { if(n==null)return null; if(n.text?.toString()?.contains(q,true)==true || n.contentDescription?.toString()?.contains(q,true)==true)return n; for(i in 0 until n.childCount){val r=find(n.getChild(i),q);if(r!=null)return r};return null }
    fun tap(x:Float,y:Float):Boolean { val p=Path();p.moveTo(x,y);return dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p,0,80)).build(),null,null) }
    fun typeText(text:String):Boolean { val node=rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false; val b=android.os.Bundle(); b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text); return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,b) }
    fun back()=performGlobalAction(GLOBAL_ACTION_BACK)
    fun home()=performGlobalAction(GLOBAL_ACTION_HOME)
}
