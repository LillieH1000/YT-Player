package h.lillie.ytplayer

import android.os.Handler
import android.os.Looper
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlayerService : MediaSessionService() {
    private lateinit var playerHandler: Handler
    private var playerSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val exoPlayer = ExoPlayer.Builder(this).build()
        playerSession = MediaSession.Builder(this, exoPlayer).build()

        playerHandler = Handler(Looper.getMainLooper())
        playerHandler.post(playerTask)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return playerSession
    }

    override fun onDestroy() {
        playerSession?.run {
            player.release()
            release()
            playerSession = null
        }
        super.onDestroy()
    }

    private val playerTask = object : Runnable {
        override fun run() {
            playerHandler.postDelayed(this, 1000)
        }
    }
}