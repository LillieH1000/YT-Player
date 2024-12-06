package h.lillie.ytplayer

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class NotificationService : NotificationListenerService()

class MainService : AccessibilityService() {
    private lateinit var mainHandler: Handler

    override fun onServiceConnected() {
        super.onServiceConnected()

        val component = ComponentName(this@MainService, NotificationService::class.java)
        val listener = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (listener == null || !listener.contains(component.flattenToString())) {
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }

        mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post(mainTask)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
    }

    private val mainTask = object : Runnable {
        override fun run() {
            val component = ComponentName(this@MainService, NotificationService::class.java)
            val listener = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            if (listener != null && listener.contains(component.flattenToString())) {
                val mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
                val activeSessions = mediaSessionManager.getActiveSessions(component)
                activeSessions.forEach {
                    Log.d("Media Playback Info", it.playbackInfo.toString())
                    Log.d("Media Metadata", it.metadata!!.getString(MediaMetadata.METADATA_KEY_TITLE))
                }
            }
            mainHandler.postDelayed(this, 1000)
        }
    }
}