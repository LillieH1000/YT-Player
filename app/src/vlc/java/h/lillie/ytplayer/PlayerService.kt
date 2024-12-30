package h.lillie.ytplayer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import java.net.URL

class PlayerService : Service() {
    private lateinit var libVLCPlayer: MediaPlayer
    private lateinit var libVLCMediaSession: MediaSessionCompat
    private lateinit var libVLCHandler: Handler
    private lateinit var libVLC: LibVLC

    inner class LibVLCBinder : Binder() {
        fun getPlayer(): MediaPlayer {
            return libVLCPlayer
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        libVLCMediaSession = MediaSessionCompat(this, "VLCPlayerService")
        libVLCMediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                super.onPlay()
                libVLCPlayer.play()
            }

            override fun onPause() {
                super.onPause()
                libVLCPlayer.pause()
            }

            override fun onStop() {
                super.onStop()
                libVLCPlayer.stop()
            }
        })
        libVLCMediaSession.isActive = true

        notificationUpdate()
        return LibVLCBinder()
    }

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel("h.lillie.ytplayer.channel", "Media Playback", NotificationManager.IMPORTANCE_LOW)
        channel.description = "Media Playback Controls"
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        val intentFilter = IntentFilter()
        intentFilter.addAction("h.lillie.ytplayer.info")
        intentFilter.addAction("h.lillie.ytplayer.pause")
        intentFilter.addAction("h.lillie.ytplayer.play")
        registerReceiver(playerBroadcastReceiver, intentFilter, RECEIVER_EXPORTED)

        libVLCHandler = Handler(Looper.getMainLooper())
        libVLCHandler.post(libVLCTask)
    }

    override fun onDestroy() {
        libVLCHandler.removeCallbacksAndMessages(null)
        unregisterReceiver(playerBroadcastReceiver)
        libVLCPlayer.stop()
        libVLCPlayer.release()
        libVLC.release()
        stopForeground(true)
        super.onDestroy()
    }

    private fun notificationUpdate() {
        val backIntent = PendingIntent.getBroadcast(this, 0, Intent("h.lillie.ytplayer.back"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val pauseIntent = PendingIntent.getBroadcast(this, 0, Intent("h.lillie.ytplayer.pause"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val playIntent = PendingIntent.getBroadcast(this, 0, Intent("h.lillie.ytplayer.play"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val forwardIntent = PendingIntent.getBroadcast(this, 0, Intent("h.lillie.ytplayer.forward"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, "h.lillie.ytplayer.channel")
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(libVLCMediaSession.sessionToken).setShowActionsInCompactView(0))
            .setContentTitle(Application.title)
            .setContentText(Application.author)
            .setLargeIcon(BitmapFactory.decodeStream(URL(Application.artwork).openStream()))
            .setSmallIcon(androidx.media3.session.R.drawable.media3_icon_circular_play)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (this::libVLCPlayer.isInitialized) {
            notification.addAction(androidx.media3.cast.R.drawable.cast_ic_notification_rewind10, "Seek Back", backIntent)
            if (libVLCPlayer.isPlaying) {
                notification.addAction(androidx.media3.session.R.drawable.media3_icon_pause, "Pause", pauseIntent)
            } else {
                notification.addAction(androidx.media3.session.R.drawable.media3_icon_play, "Play", playIntent)
            }
            notification.addAction(androidx.media3.cast.R.drawable.cast_ic_notification_forward10, "Seek Forward", forwardIntent)
        }

        stopForeground(false)
        startForeground(1, notification.build())
    }

    private val playerBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "h.lillie.ytplayer.info") {
                val args = mutableListOf<String>()
                args.add("--aout=opensles")
                args.add("--avcodec-hw=any")
                args.add("-vvv")
                libVLC = LibVLC(this@PlayerService, args)
                libVLCPlayer = MediaPlayer(libVLC)
                libVLCPlayer.setEventListener(object : MediaPlayer.EventListener {
                    override fun onEvent(event: MediaPlayer.Event?) {
                        if (event?.type == MediaPlayer.Event.EndReached) {
                            val media = Media(libVLC, Uri.parse(Application.hlsUrl))
                            media.setHWDecoderEnabled(true, true)
                            libVLCPlayer.media = media
                            media.release()
                        }
                    }
                })

                val broadcastIntent = Intent("h.lillie.ytplayer.register")
                broadcastIntent.setPackage(this@PlayerService.packageName)
                sendBroadcast(broadcastIntent)

                val media = Media(libVLC, Uri.parse(Application.hlsUrl))
                media.setHWDecoderEnabled(true, true)
                libVLCPlayer.media = media
                media.release()
                libVLCPlayer.play()
                return
            }
            if (intent?.action == "h.lillie.ytplayer.pause") {
                libVLCPlayer.pause()
                return
            }
            if (intent?.action == "h.lillie.ytplayer.play") {
                libVLCPlayer.play()
                return
            }
        }
    }

    private val libVLCTask = object : Runnable {
        override fun run() {
            notificationUpdate()
            libVLCHandler.postDelayed(this, 1000)
        }
    }
}