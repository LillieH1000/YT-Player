package h.lillie.ytplayer

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.http.HttpEngine
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ext.SdkExtensions
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpEngineDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.DecimalFormat

@OptIn(UnstableApi::class)
@SuppressLint("UnspecifiedRegisterReceiverFlag")
class PlayerService: MediaSessionService(), MediaSession.Callback {
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var playerHandler: Handler
    private lateinit var playerCache: SimpleCache
    private val backCommand = SessionCommand("back", Bundle.EMPTY)
    private val forwardCommand = SessionCommand("forward", Bundle.EMPTY)
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

        val backButton: CommandButton = CommandButton.Builder()
                .setDisplayName("Seek Back")
                .setIconResId(androidx.media3.session.R.drawable.media3_icon_skip_back_10)
                .setSessionCommand(backCommand)
                .build()

        val forwardButton: CommandButton = CommandButton.Builder()
            .setDisplayName("Seek Forward")
            .setIconResId(androidx.media3.session.R.drawable.media3_icon_skip_forward_10)
            .setSessionCommand(forwardCommand)
            .build()

        playerSession = MediaSession.Builder(this, exoPlayer)
            .setCallback(this)
            .setCustomLayout(ImmutableList.of(backButton, forwardButton))
            .build()

        if (Build.VERSION.SDK_INT <= 32) {
            registerReceiver(playerBroadcastReceiver, IntentFilter("h.lillie.ytplayer.info"))
        } else {
            registerReceiver(playerBroadcastReceiver, IntentFilter("h.lillie.ytplayer.info"), RECEIVER_NOT_EXPORTED)
        }

        playerHandler = Handler(Looper.getMainLooper())
        playerHandler.post(playerTask)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return playerSession
    }

    override fun onDestroy() {
        playerSession?.run {
            playerHandler.removeCallbacksAndMessages(null)
            unregisterReceiver(playerBroadcastReceiver)
            playerCache.release()
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

    private fun optString(info: JSONObject, key: String): String? {
        if (info.isNull(key)) {
            return null
        }

        return info.optString(key)
    }

    private val playerBroadcastReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "h.lillie.ytplayer.info") {
                val playerMediaMetadata: MediaMetadata = MediaMetadata.Builder()
                    .setTitle(Application.title.value)
                    .setArtist(Application.author)
                    .setArtworkUri(Application.artwork?.toUri())
                    .build()

                val playerMediaItem: MediaItem.Builder = MediaItem.Builder()
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .setMediaMetadata(playerMediaMetadata)
                    .setUri(Application.url?.toUri())

                if (Application.subtitles != null) {
                    val subtitlesList = mutableListOf<MediaItem.SubtitleConfiguration>()

                    // English
                    val en = optString(Application.subtitles!!, "en")
                    if (en != null) {
                        val playerCaptions: MediaItem.SubtitleConfiguration = MediaItem.SubtitleConfiguration.Builder(en.toUri())
                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                            .setMimeType(MimeTypes.TEXT_VTT)
                            .setLanguage("en")
                            .build()

                        subtitlesList.add(playerCaptions)
                    }
                    // Japanese
                    val ja = optString(Application.subtitles!!, "ja")
                    if (ja != null) {
                        val playerCaptions: MediaItem.SubtitleConfiguration = MediaItem.SubtitleConfiguration.Builder(ja.toUri())
                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                            .setMimeType(MimeTypes.TEXT_VTT)
                            .setLanguage("ja")
                            .build()

                        subtitlesList.add(playerCaptions)
                    }

                    if (subtitlesList.isNotEmpty()) {
                        playerMediaItem.setSubtitleConfigurations(subtitlesList)
                    }
                }

                if (this@PlayerService::playerCache.isInitialized) {
                    playerCache.release()
                }
                playerCache = SimpleCache(File(cacheDir, "media"), LeastRecentlyUsedCacheEvictor(200 * 1024 * 1024), StandaloneDatabaseProvider(this@PlayerService))
                val cacheDataSource: CacheDataSource.Factory = CacheDataSource.Factory().setCache(playerCache)

                if (Build.VERSION.SDK_INT >= 34 && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 7) {
                    val httpEngine: HttpEngine = HttpEngine.Builder(this@PlayerService)
                        .setEnableHttp2(true)
                        .build()

                    val httpEngineDataSource: HttpEngineDataSource.Factory = HttpEngineDataSource.Factory(httpEngine, MoreExecutors.directExecutor())
                    if (!Application.live) {
                        cacheDataSource.setUpstreamDataSourceFactory(httpEngineDataSource)

                        val hlsSource: MediaSource = HlsMediaSource.Factory(cacheDataSource)
                            .setAllowChunklessPreparation(false)
                            .createMediaSource(playerMediaItem.build())

                        exoPlayer.setMediaSource(hlsSource)
                    } else {
                        val hlsSource: MediaSource = HlsMediaSource.Factory(httpEngineDataSource)
                            .setAllowChunklessPreparation(false)
                            .createMediaSource(playerMediaItem.build())

                        exoPlayer.setMediaSource(hlsSource)
                    }
                } else {
                    val defaultDataSource: DefaultDataSource.Factory = DefaultDataSource.Factory(this@PlayerService)
                    if (!Application.live) {
                        cacheDataSource.setUpstreamDataSourceFactory(defaultDataSource)

                        val hlsSource: MediaSource = HlsMediaSource.Factory(cacheDataSource)
                            .setAllowChunklessPreparation(false)
                            .createMediaSource(playerMediaItem.build())

                        exoPlayer.setMediaSource(hlsSource)
                    } else {
                        val hlsSource: MediaSource = HlsMediaSource.Factory(defaultDataSource)
                            .setAllowChunklessPreparation(false)
                            .createMediaSource(playerMediaItem.build())

                        exoPlayer.setMediaSource(hlsSource)
                    }
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

    private val playerTask = object: Runnable {
        override fun run() {
            val sponsorBlock: JSONArray? = Application.sponsorBlock
            if (sponsorBlock != null && !Application.live) {
                for (i in 0 until sponsorBlock.length()) {
                    val decimalFormat = DecimalFormat("#.###")

                    val segment: JSONArray = sponsorBlock.getJSONObject(i).getJSONArray("segment")
                    val position: Double = decimalFormat.format(playerSession?.player!!.currentPosition / 1000.0).toDouble()
                    val segment0: Double = decimalFormat.format(segment[0]).toDouble()
                    val segment1: Double = decimalFormat.format(segment[1]).toDouble()

                    if (position >= segment0 && position < segment1) {
                        playerSession?.player!!.seekTo(decimalFormat.format(segment1 * 1000.0).toLong())
                        Toast.makeText(this@PlayerService, "Sponsor Skipped", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            playerHandler.postDelayed(this, 1000)
        }
    }
}