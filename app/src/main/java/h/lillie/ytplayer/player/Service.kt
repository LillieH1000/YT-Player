package h.lillie.ytplayer.player

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.net.http.HttpEngine
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.ext.SdkExtensions
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpEngineDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import h.lillie.ytplayer.requests.Requests
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import java.io.File
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

@OptIn(UnstableApi::class)
class Service: MediaLibraryService(), MediaLibraryService.MediaLibrarySession.Callback, Player.Listener {
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var playerCache: SimpleCache
    private lateinit var playerDataSource: DataSource.Factory
    private var playerHandler: Handler = Handler(Looper.getMainLooper())
    private var playerReady: Boolean = false
    private var playerSession: MediaLibrarySession? = null
    private var playerTimer: CountDownTimer? = null
    private var sponsorBlock: JSONArray? = null

    override fun onCreate() {
        super.onCreate()

        val audioAttributes: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .build()

        val renderersFactory: DefaultRenderersFactory = DefaultRenderersFactory(this)
            .forceEnableMediaCodecAsynchronousQueueing()

        val trackSelector = DefaultTrackSelector(this, AdaptiveTrackSelection.Factory())
        trackSelector.setParameters(trackSelector.buildUponParameters()
            .setForceHighestSupportedBitrate(true)
            .build()
        )

        val networkDataSource: DataSource.Factory = if (SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 7) {
            val httpEngine: HttpEngine = HttpEngine.Builder(this)
                .setEnableHttp2(true)
                .setEnableQuic(true)
                .build()

            HttpEngineDataSource.Factory(httpEngine, Dispatchers.IO.asExecutor())
        } else {
            val okhttpClient: OkHttpClient = OkHttpClient.Builder().build()
            OkHttpDataSource.Factory(okhttpClient)
        }

        if (this@Service::playerCache.isInitialized) playerCache.release()
        playerCache = SimpleCache(File(cacheDir, "media"), LeastRecentlyUsedCacheEvictor(256 * 1024 * 1024), StandaloneDatabaseProvider(this@Service))

        val cacheDataSource: CacheDataSource.Factory = CacheDataSource.Factory()
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .setUpstreamDataSourceFactory(networkDataSource)
            .setCache(playerCache)

        playerDataSource = DataSource.Factory {
            val mediaMetadata: MediaMetadata
            runBlocking(Dispatchers.Main) {
                mediaMetadata = exoPlayer.mediaMetadata
            }

            if (mediaMetadata.extras?.getBoolean("live") != true) {
                cacheDataSource.createDataSource()
            } else {
                networkDataSource.createDataSource()
            }
        }

        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .build()

        exoPlayer.addListener(this)
        playerSession = MediaLibrarySession.Builder(this, exoPlayer, this).build()

        val intentFilter = IntentFilter()
        intentFilter.addAction("h.lillie.ytplayer.service.info")
        intentFilter.addAction("h.lillie.ytplayer.service.timer")
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(playerBroadcastReceiver, intentFilter, RECEIVER_NOT_EXPORTED)
        } else {
            @SuppressLint("UnspecifiedRegisterReceiverFlag")
            registerReceiver(playerBroadcastReceiver, intentFilter)
        }
        playerHandler.post(playerTask)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return playerSession
    }

    override fun onDestroy() {
        playerTimer?.cancel()
        playerTimer = null
        sponsorBlock = null
        playerHandler.removeCallbacksAndMessages(null)
        unregisterReceiver(playerBroadcastReceiver)
        if (this::playerCache.isInitialized) playerCache.release()
        if (this::exoPlayer.isInitialized) exoPlayer.release()
        playerSession?.release()
        playerSession = null
        super.onDestroy()
    }

    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
        val connectionResult: MediaSession.ConnectionResult = MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
            .setAvailablePlayerCommands(
                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                    .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .remove(Player.COMMAND_SEEK_TO_NEXT)
                    .build()
            )
            .setAvailableSessionCommands(
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(SessionCommand.COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT)
                    .build()
            ).build()

        return connectionResult
    }

    override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> {
        val currentMediaItem: MediaItem? = exoPlayer.currentMediaItem
        if (currentMediaItem != null) return Futures.immediateFuture(LibraryResult.ofItem(currentMediaItem, params))

        val playerMediaMetadata: MediaMetadata = MediaMetadata.Builder()
            .setIsBrowsable(false)
            .setIsPlayable(false)
            .build()

        val playerMediaItem: MediaItem = MediaItem.Builder()
            .setMediaId("root")
            .setMediaMetadata(playerMediaMetadata)
            .build()

        return Futures.immediateFuture(LibraryResult.ofItem(playerMediaItem, params))
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        super.onPlaybackStateChanged(playbackState)
        @SuppressLint("SwitchIntDef")
        when (playbackState) {
            Player.STATE_READY -> {
                playerReady = true
                exoPlayer.play()
            }
        }
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        super.onRepeatModeChanged(repeatMode)
        playerMediaButtons(repeatMode)
    }

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)
        @SuppressLint("SwitchIntDef")
        when ((error as ExoPlaybackException).type) {
            ExoPlaybackException.TYPE_SOURCE -> {
                if (exoPlayer.mediaMetadata.extras?.getBoolean("live") == false && exoPlayer.currentPosition == 0L) {
                    if (exoPlayer.mediaMetadata.extras?.getString("hlsUrl") != null) {
                        val playerMediaItem: MediaItem = MediaItem.Builder()
                            .setMediaId("root")
                            .setMediaMetadata(exoPlayer.currentMediaItem!!.mediaMetadata)
                            .setMimeType(MimeTypes.APPLICATION_M3U8)
                            .setSubtitleConfigurations(exoPlayer.currentMediaItem!!.localConfiguration!!.subtitleConfigurations)
                            .setUri(exoPlayer.mediaMetadata.extras?.getString("hlsUrl")!!.toUri())
                            .build()

                        val hlsMediaSource: HlsMediaSource = HlsMediaSource.Factory(playerDataSource)
                            .setAllowChunklessPreparation(false)
                            .createMediaSource(playerMediaItem)

                        exoPlayer.setMediaSource(hlsMediaSource)
                        exoPlayer.playWhenReady = false
                        exoPlayer.prepare()
                    } else {
                        Toast.makeText(this@Service, "Source playback error", Toast.LENGTH_SHORT).show()
                    }
                }
                if (exoPlayer.mediaMetadata.extras?.getBoolean("live") == true && exoPlayer.mediaMetadata.extras?.getLong("expiration")!! <= TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())) {
                    val broadcastIntent = Intent("h.lillie.ytplayer.service.info")
                    broadcastIntent.setPackage(this.packageName)
                    broadcastIntent.putExtra("videoID", exoPlayer.mediaMetadata.extras?.getString("id"))
                    sendBroadcast(broadcastIntent)
                }
            }
        }
    }

    private fun BroadcastReceiver.coroutineScope(onReceive: suspend () -> Unit) {
        val pendingResult: BroadcastReceiver.PendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            onReceive()
        }.invokeOnCompletion {
            pendingResult.finish()
        }
    }

    private fun playerMediaButtons(repeatMode: Int) {
        if (exoPlayer.mediaMetadata.extras?.getBoolean("live") == true) {
            playerSession?.setMediaButtonPreferences(emptyList())
            return
        }

        val mediaButtons: MutableList<CommandButton> = mutableListOf(
            CommandButton.Builder(CommandButton.ICON_SKIP_BACK_10)
                .setDisplayName("Seek Back")
                .setPlayerCommand(Player.COMMAND_SEEK_BACK)
                .build(),
            CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_10)
                .setDisplayName("Seek Forward")
                .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
                .build()
        )

        if (repeatMode == Player.REPEAT_MODE_OFF) {
            mediaButtons.add(CommandButton.Builder(CommandButton.ICON_REPEAT_ALL)
                .setDisplayName("Loop Off")
                .setPlayerCommand(Player.COMMAND_SET_REPEAT_MODE, Player.REPEAT_MODE_ONE)
                .build())
        } else {
            mediaButtons.add(CommandButton.Builder(CommandButton.ICON_REPEAT_ONE)
                .setDisplayName("Loop On")
                .setPlayerCommand(Player.COMMAND_SET_REPEAT_MODE, Player.REPEAT_MODE_OFF)
                .build())
        }

        playerSession?.setMediaButtonPreferences(mediaButtons)
    }

    private val playerBroadcastReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = coroutineScope {
            if (intent?.action == "h.lillie.ytplayer.service.info") {
                playerReady = false
                playerTimer?.cancel()
                playerTimer = null

                val request = Requests()
                val info = request.extractor(this@Service, intent.extras!!.getString("videoID")!!) ?: return@coroutineScope
                val dislikes = request.returnYouTubeDislike(this@Service, info.id)
                sponsorBlock = request.sponsorBlock(this@Service, info.id)

                val playerExtraInfo = Bundle()
                playerExtraInfo.putString("id", info.id)
                playerExtraInfo.putString("type", info.type)
                playerExtraInfo.putBoolean("live", info.live)
                playerExtraInfo.putLong("views", info.views)
                playerExtraInfo.putLong("likes", info.likes)
                playerExtraInfo.putString("hlsUrl", info.hlsUrl)
                if (info.expiration != null) playerExtraInfo.putLong("expiration", info.expiration)
                if (dislikes != null) playerExtraInfo.putLong("dislikes", dislikes)

                val playerMediaMetadata: MediaMetadata = MediaMetadata.Builder()
                    .setTitle(info.title)
                    .setArtist(info.author)
                    .setArtworkUri(info.thumbnail.toUri())
                    .setExtras(playerExtraInfo)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()

                val playerMediaItem: MediaItem.Builder = MediaItem.Builder()
                    .setMediaId("root")
                    .setMediaMetadata(playerMediaMetadata)

                if (info.live && info.hlsUrl != null) {
                    playerMediaItem.setMimeType(MimeTypes.APPLICATION_M3U8)
                    playerMediaItem.setUri(info.hlsUrl.toUri())
                } else {
                    playerMediaItem.setMimeType(MimeTypes.APPLICATION_MPD)
                    playerMediaItem.setUri(Uri.fromFile(File(info.manifestPath!!)))
                }

                val subtitles = mutableListOf<MediaItem.SubtitleConfiguration>()
                info.subtitles?.forEach { subtitle ->
                    val playerCaptions: MediaItem.SubtitleConfiguration = MediaItem.SubtitleConfiguration.Builder(subtitle.url.toUri())
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .setMimeType(MimeTypes.TEXT_VTT)
                        .setLanguage(subtitle.id)
                        .build()

                    subtitles.add(playerCaptions)
                }
                if (subtitles.isNotEmpty()) playerMediaItem.setSubtitleConfigurations(subtitles)

                val broadcastIntent = Intent("h.lillie.ytplayer.activity.subtitles")
                broadcastIntent.setPackage(this@Service.packageName)
                broadcastIntent.putParcelableArrayListExtra("subtitles", info.subtitles)
                sendBroadcast(broadcastIntent)

                val defaultDataSource: DefaultDataSource.Factory = DefaultDataSource.Factory(this@Service, playerDataSource)
                val dashMediaSource: MediaSource = DefaultMediaSourceFactory(defaultDataSource)
                    .createMediaSource(playerMediaItem.build())

                val hlsMediaSource: HlsMediaSource = HlsMediaSource.Factory(playerDataSource)
                    .setAllowChunklessPreparation(false)
                    .createMediaSource(playerMediaItem.build())

                while (TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) < info.availability) {
                    delay(1000L.milliseconds)
                }

                withContext(Dispatchers.Main) {
                    if (info.live && info.hlsUrl != null) {
                        exoPlayer.setMediaSource(hlsMediaSource)
                    } else {
                        exoPlayer.setMediaSource(dashMediaSource)
                    }
                    playerMediaButtons(exoPlayer.repeatMode)
                    exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
                    exoPlayer.playbackParameters = PlaybackParameters(1.0f)
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                    exoPlayer.playWhenReady = false
                    exoPlayer.prepare()

                    return@withContext
                }

                return@coroutineScope
            }

            if (intent?.action == "h.lillie.ytplayer.service.timer") {
                val time: Long = intent.extras!!.getLong("time")
                playerTimer?.cancel()
                playerTimer = null
                if (time != 0L) {
                    withContext(Dispatchers.Main) {
                        playerTimer = object: CountDownTimer(time, 1000) {
                            override fun onTick(millisUntilFinished: Long) {
                            }
                            override fun onFinish() {
                                exoPlayer.pause()
                            }
                        }.start()
                        return@withContext
                    }
                }
                return@coroutineScope
            }
        }
    }

    private val playerTask = object: Runnable {
        override fun run() {
            val sponsorBlock: JSONArray? = sponsorBlock
            if (sponsorBlock != null && playerReady && this@Service::exoPlayer.isInitialized && exoPlayer.mediaMetadata.extras?.getBoolean("live") != true) {
                for (i in 0 until sponsorBlock.length()) {
                    val decimalFormat = DecimalFormat("#.###")

                    val segment: JSONArray = sponsorBlock.getJSONObject(i).getJSONArray("segment")
                    val position: Double = decimalFormat.format(exoPlayer.currentPosition / 1000.0).toDouble()
                    val segment0: Double = decimalFormat.format(segment[0]).toDouble()
                    val segment1: Double = decimalFormat.format(segment[1]).toDouble()

                    if (position in segment0..<segment1) {
                        exoPlayer.seekTo(decimalFormat.format(segment1 * 1000.0).toLong())
                        Toast.makeText(this@Service, "Sponsor skipped", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            playerHandler.postDelayed(this, 1000)
        }
    }
}