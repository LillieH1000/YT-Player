package h.lillie.ytplayer

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.android.gms.cast.framework.CastContext
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import okhttp3.OkHttpClient
import org.json.JSONArray

@OptIn(UnstableApi::class)
@SuppressLint("DefaultLocale")
class PlayerService : MediaSessionService(), MediaSession.Callback {
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var playerHandler: Handler
    private val backCommand = SessionCommand("back", Bundle.EMPTY)
    private val forwardCommand = SessionCommand("forward", Bundle.EMPTY)
    private var playerSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        exoPlayer = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .build()

        val backButton = CommandButton.Builder()
                .setDisplayName("Seek Back")
                .setIconResId(androidx.media3.session.R.drawable.media3_icon_skip_back_10)
                .setSessionCommand(backCommand)
                .build()

        val forwardButton = CommandButton.Builder()
            .setDisplayName("Seek Forward")
            .setIconResId(androidx.media3.session.R.drawable.media3_icon_skip_forward_10)
            .setSessionCommand(forwardCommand)
            .build()

        playerSession = MediaSession.Builder(this, exoPlayer)
            .setCallback(this)
            .setCustomLayout(ImmutableList.of(backButton, forwardButton))
            .build()

        registerReceiver(playerBroadcastReceiver, IntentFilter("h.lillie.ytplayer.info"), RECEIVER_NOT_EXPORTED)

        val castPlayer = CastPlayer(CastContext.getSharedInstance(this, MoreExecutors.directExecutor()).result, DefaultMediaItemConverter(), 10000, 10000)
        castPlayer.setSessionAvailabilityListener(object : SessionAvailabilityListener {
            override fun onCastSessionAvailable() {
                exoPlayer.stop()
                playerSession?.player = castPlayer

                val playerMediaMetadata: MediaMetadata = MediaMetadata.Builder()
                    .setTitle(Application.title)
                    .setArtist(Application.author)
                    .setArtworkUri(Uri.parse(Application.artwork))
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
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

        playerHandler = Handler(Looper.getMainLooper())
        // playerHandler.post(playerTask)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return playerSession
    }

    override fun onDestroy() {
        playerSession?.run {
            playerHandler.removeCallbacksAndMessages(null)
            unregisterReceiver(playerBroadcastReceiver)
            player.release()
            release()
            playerSession = null
        }
        super.onDestroy()
    }

    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailablePlayerCommands(MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .remove(Player.COMMAND_SEEK_TO_NEXT)
                .build()
            )
            .setAvailableSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(backCommand)
                .add(forwardCommand)
                .build()
            ).build()
    }

    override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
        if (customCommand.customAction == "back") {
            playerSession?.player?.seekBack()
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
        if (customCommand.customAction == "forward") {
            playerSession?.player?.seekForward()
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
        return super.onCustomCommand(session, controller, customCommand, args)
    }

    private val playerBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "h.lillie.ytplayer.info") {
                val playerMediaMetadata: MediaMetadata = MediaMetadata.Builder()
                    .setTitle(Application.title)
                    .setArtist(Application.author)
                    .setArtworkUri(Uri.parse(Application.artwork))
                    .setMediaType(MediaMetadata.MEDIA_TYPE_VIDEO)
                    .build()

                val playerMediaItem: MediaItem = MediaItem.Builder()
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .setMediaMetadata(playerMediaMetadata)
                    .setUri(Uri.parse(Application.hlsUrl))
                    .build()

                val dataSourceFactory: DataSource.Factory = OkHttpDataSource.Factory(OkHttpClient.Builder().build())
                val videoSource: MediaSource = HlsMediaSource.Factory(dataSourceFactory).createMediaSource(playerMediaItem)

                exoPlayer.setMediaSource(videoSource)
                exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
                exoPlayer.playWhenReady = true
                exoPlayer.prepare()
            }
        }
    }

    private val playerTask = object : Runnable {
        override fun run() {
            val sponsorBlock: JSONArray? = Application.sponsorBlock
            if (sponsorBlock != null && playerSession?.player == exoPlayer && !Application.live) {
                for (i in 0 until sponsorBlock.length()) {
                    val category: String = sponsorBlock.getJSONObject(i).optString("category")
                    val segment: JSONArray = sponsorBlock.getJSONObject(i).getJSONArray("segment")
                    val segment0: Float = String.format("%.3f", segment[0].toString().toDouble()).replace(".", "").toFloat()
                    val segment1: Float = String.format("%.3f", segment[1].toString().toDouble()).replace(".", "").toFloat()
                    if (category.contains("sponsor") && exoPlayer.currentPosition >= segment0 && exoPlayer.currentPosition < segment1) {
                        exoPlayer.seekTo(segment1.toLong())
                        Toast.makeText(this@PlayerService, "Sponsor Skipped", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            playerHandler.postDelayed(this, 1000)
        }
    }
}