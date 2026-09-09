package com.jarvis.mobile.core
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
object NotificationStore { @Volatile var latest="" }
class JarvisNotificationListener: NotificationListenerService(){ override fun onNotificationPosted(sbn:StatusBarNotification){ NotificationStore.latest="${sbn.packageName}: ${sbn.notification.extras.getCharSequence("android.title") ?: ""} — ${sbn.notification.extras.getCharSequence("android.text") ?: ""}".take(1000) } }
