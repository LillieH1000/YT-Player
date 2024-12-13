package h.lillie.ytplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.android.gms.cast.framework.CastContext
import com.google.common.util.concurrent.MoreExecutors
import okhttp3.OkHttpClient

@UnstableApi
class PlayerService : MediaSessionService() {
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var castPlayer: CastPlayer
    private var playerSession: MediaSession? = null

    // Experimental Cast Variables
    private var audioUrl: String? = null
    private var packageName: String? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        exoPlayer = ExoPlayer.Builder(this).build()
        playerSession = MediaSession.Builder(this, exoPlayer).build()

        val intentFilter = IntentFilter()
        intentFilter.addAction("h.lillie.ytplayer.info")
        intentFilter.addAction("h.lillie.ytplayer.cast")
        registerReceiver(playerBroadcastReceiver, intentFilter, RECEIVER_NOT_EXPORTED)

        // Experimental Cast Player
        castPlayer = CastPlayer(CastContext.getSharedInstance(this, MoreExecutors.directExecutor()).result)
        castPlayer.setSessionAvailabilityListener(object : SessionAvailabilityListener {
            override fun onCastSessionAvailable() {
                exoPlayer.stop()
                playerSession?.player = castPlayer

                val broadcastIntent = Intent("h.lillie.ytplayer.cast")
                broadcastIntent.setPackage(packageName)
                sendBroadcast(broadcastIntent)
            }

            override fun onCastSessionUnavailable() {
                castPlayer.stop()
                playerSession?.player = exoPlayer
                exoPlayer.play()
            }
        })
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return playerSession
    }

    override fun onDestroy() {
        playerSession?.run {
            unregisterReceiver(playerBroadcastReceiver)
            player.release()
            release()
            playerSession = null
        }
        super.onDestroy()
    }

    private val playerBroadcastReceiver = object : BroadcastReceiver() {
        @OptIn(UnstableApi::class)
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "h.lillie.ytplayer.info") {
                // Experimental Cast Variables
                audioUrl = intent.getStringExtra("audioUrl")
                packageName = intent.getStringExtra("package")

                val playerMediaMetadata: MediaMetadata = MediaMetadata.Builder()
                    .setTitle(intent.getStringExtra("title"))
                    .setArtist(intent.getStringExtra("author"))
                    .setArtworkUri(Uri.parse(intent.getStringExtra("artwork")))
                    .build()

                val playerMediaItem: MediaItem = MediaItem.Builder()
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .setMediaMetadata(playerMediaMetadata)
                    .setUri(Uri.parse(intent.getStringExtra("hlsUrl")))
                    .build()

                val dataSourceFactory: DataSource.Factory = OkHttpDataSource.Factory(OkHttpClient.Builder().build())
                val videoSource: MediaSource = HlsMediaSource.Factory(dataSourceFactory).createMediaSource(playerMediaItem)

                exoPlayer.setMediaSource(videoSource)
                exoPlayer.playWhenReady = true
                exoPlayer.prepare()
                return
            }

            // Experimental Cast Receiver
            if (intent?.action == "h.lillie.ytplayer.cast") {
                val playerMediaItem: MediaItem = MediaItem.Builder()
                    .setMimeType(MimeTypes.AUDIO_MP4)
                    .setUri(Uri.parse(audioUrl))
                    .build()

                castPlayer.setMediaItem(playerMediaItem)
                castPlayer.playWhenReady = true
                castPlayer.prepare()
                return
            }
        }
    }
}