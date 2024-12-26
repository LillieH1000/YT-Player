package h.lillie.ytplayer

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

class VLCPlayerService : Service() {
    private lateinit var libVLCVideoLayout: VLCVideoLayout
    private lateinit var libVLCPlayer: MediaPlayer

    inner class LibVLCBinder : Binder() {
        fun setView(libVLCVideoLayout: VLCVideoLayout) {
            this@VLCPlayerService.libVLCVideoLayout = libVLCVideoLayout
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return LibVLCBinder()
    }

    override fun onCreate() {
        super.onCreate()
        val intentFilter = IntentFilter()
        intentFilter.addAction("h.lillie.ytplayer.vlc.info")
        registerReceiver(playerBroadcastReceiver, intentFilter, RECEIVER_NOT_EXPORTED)
    }

    private val playerBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "h.lillie.ytplayer.vlc.info") {
                val libVLC = LibVLC(this@VLCPlayerService)
                libVLCPlayer = MediaPlayer(libVLC)
                libVLCPlayer.attachViews(libVLCVideoLayout, null, false, false)
                libVLCPlayer.media = Media(libVLC, Uri.parse(Application.hlsUrl))
                libVLCPlayer.play()
                return
            }
        }
    }
}