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

@OptIn(UnstableApi::class)
class PlayerService : MediaSessionService() {
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var castPlayer: CastPlayer
    private var playerSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        exoPlayer = ExoPlayer.Builder(this).build()
        playerSession = MediaSession.Builder(this, exoPlayer).build()

        registerReceiver(playerBroadcastReceiver, IntentFilter("h.lillie.ytplayer.info"), RECEIVER_NOT_EXPORTED)

        castPlayer = CastPlayer(CastContext.getSharedInstance(this, MoreExecutors.directExecutor()).result)
        castPlayer.setSessionAvailabilityListener(object : SessionAvailabilityListener {
            override fun onCastSessionAvailable() {
                exoPlayer.stop()
                playerSession?.player = castPlayer

                val playerMediaMetadata: MediaMetadata = MediaMetadata.Builder()
                    .setTitle(Application.title)
                    .setArtist(Application.author)
                    .setArtworkUri(Uri.parse(Application.artwork))
                    .build()

                val playerMediaItem: MediaItem = MediaItem.Builder()
                    .setMimeType(MimeTypes.AUDIO_MP4)
                    .setMediaMetadata(playerMediaMetadata)
                    .setUri(Uri.parse(Application.audioUrl))
                    .build()

                castPlayer.setMediaItem(playerMediaItem, exoPlayer.currentPosition)
                castPlayer.playWhenReady = true
                castPlayer.prepare()
            }

            override fun onCastSessionUnavailable() {
                castPlayer.stop()
                playerSession?.player = exoPlayer
                exoPlayer.seekTo(castPlayer.currentPosition)
                exoPlayer.playWhenReady = true
                exoPlayer.prepare()
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
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "h.lillie.ytplayer.info") {
                if (playerSession?.player == castPlayer) {
                    castPlayer.stop()
                    playerSession?.player = exoPlayer
                }

                val playerMediaMetadata: MediaMetadata = MediaMetadata.Builder()
                    .setTitle(Application.title)
                    .setArtist(Application.author)
                    .setArtworkUri(Uri.parse(Application.artwork))
                    .build()

                val playerMediaItem: MediaItem = MediaItem.Builder()
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .setMediaMetadata(playerMediaMetadata)
                    .setUri(Uri.parse(Application.hlsUrl))
                    .build()

                val dataSourceFactory: DataSource.Factory = OkHttpDataSource.Factory(OkHttpClient.Builder().build())
                val videoSource: MediaSource = HlsMediaSource.Factory(dataSourceFactory).createMediaSource(playerMediaItem)

                exoPlayer.setMediaSource(videoSource)
                exoPlayer.playWhenReady = true
                exoPlayer.prepare()
            }
        }
    }
}