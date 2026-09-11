package com.jarvis.mobile.core

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

object NotificationStore {
    @Volatile
    var latest: String = ""
}

class JarvisNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""

        val content = when {
            bigText.isNotBlank() -> bigText
            text.isNotBlank() -> text
            else -> ""
        }

        NotificationStore.latest = "${sbn.packageName}: $title — $content".take(1200)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {}
}
