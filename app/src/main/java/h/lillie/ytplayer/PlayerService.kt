package h.lillie.ytplayer

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.net.http.HttpEngine
import android.os.Build
import android.os.ext.SdkExtensions
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpEngineDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.MoreExecutors

@OptIn(UnstableApi::class)
@SuppressLint("UnspecifiedRegisterReceiverFlag")
class PlayerService: MediaSessionService(), MediaSession.Callback {
    private lateinit var exoPlayer: ExoPlayer
    private var playerSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val renderersFactory: DefaultRenderersFactory = DefaultRenderersFactory(this)
            .forceEnableMediaCodecAsynchronousQueueing()

        val trackSelector: DefaultTrackSelector = DefaultTrackSelector(this).apply {
            setParameters(buildUponParameters()
                .setForceHighestSupportedBitrate(true)
            )
        }

        exoPlayer = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .build()

        playerSession = MediaSession.Builder(this, exoPlayer)
            .setCallback(this)
            .build()

        if (Build.VERSION.SDK_INT <= 32) {
            registerReceiver(playerBroadcastReceiver, IntentFilter("h.lillie.ytplayer.info"))
        } else {
            registerReceiver(playerBroadcastReceiver, IntentFilter("h.lillie.ytplayer.info"), RECEIVER_NOT_EXPORTED)
        }
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

    private val playerBroadcastReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "h.lillie.ytplayer.info") {
                val playerMediaMetadata: MediaMetadata = MediaMetadata.Builder()
                    .setTitle(Application.title)
                    .setArtist(Application.author)
                    .setArtworkUri(Uri.parse(Application.artwork))
                    .build()

                val playerMediaItem: MediaItem.Builder = MediaItem.Builder()
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .setMediaMetadata(playerMediaMetadata)
                    .setUri(Uri.parse(Application.hlsUrl))

                if (Build.VERSION.SDK_INT >= 34 && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 7) {
                    val httpEngine: HttpEngine = HttpEngine.Builder(this@PlayerService)
                        .setEnableHttp2(true)
                        .build()

                    val httpEngineDataSource: HttpEngineDataSource.Factory = HttpEngineDataSource.Factory(httpEngine, MoreExecutors.directExecutor())
                    val hlsSource: MediaSource = HlsMediaSource.Factory(httpEngineDataSource)
                        .setAllowChunklessPreparation(false)
                        .createMediaSource(playerMediaItem.build())

                    exoPlayer.setMediaSource(hlsSource)
                } else {
                    val defaultDataSource: DefaultDataSource.Factory = DefaultDataSource.Factory(this@PlayerService)
                    val hlsSource: MediaSource = HlsMediaSource.Factory(defaultDataSource)
                        .setAllowChunklessPreparation(false)
                        .createMediaSource(playerMediaItem.build())

                    exoPlayer.setMediaSource(hlsSource)
                }

                exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
                exoPlayer.playbackParameters = PlaybackParameters(1.0f)
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
                exoPlayer.playWhenReady = true
                exoPlayer.prepare()
            }
        }
    }
}