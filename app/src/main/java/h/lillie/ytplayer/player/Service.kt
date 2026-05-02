package h.lillie.ytplayer.player

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
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
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import h.lillie.ytplayer.requests.Requests
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import java.io.File
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
class Service: MediaLibraryService(), MediaLibraryService.MediaLibrarySession.Callback, Player.Listener {
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var playerCache: SimpleCache
    private lateinit var playerHandler: Handler
    private val backCommand = SessionCommand("back", Bundle.EMPTY)
    private val forwardCommand = SessionCommand("forward", Bundle.EMPTY)
    private val subtitlesList = mutableListOf<MediaItem.SubtitleConfiguration>()
    private var playerBufferingTimer: CountDownTimer? = null
    private var playerSession: MediaLibrarySession? = null
    private var playerTimer: CountDownTimer? = null
    private var sponsorBlock: JSONArray? = null

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()

        val audioAttributes: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .build()

        val renderersFactory: DefaultRenderersFactory = DefaultRenderersFactory(this)
            .forceEnableMediaCodecAsynchronousQueueing()

        val trackSelector: DefaultTrackSelector = DefaultTrackSelector(this).apply {
            setParameters(buildUponParameters()
                .setForceHighestSupportedBitrate(true)
            )
        }

        val httpLoggingInterceptor = HttpLoggingInterceptor()
            .setLevel(HttpLoggingInterceptor.Level.HEADERS)

        val client: OkHttpClient.Builder = OkHttpClient.Builder()
            .addInterceptor(httpLoggingInterceptor)

        val okhttpDataSource: OkHttpDataSource.Factory = OkHttpDataSource.Factory(client.build())

        if (this@Service::playerCache.isInitialized) {
            playerCache.release()
        }
        playerCache = SimpleCache(File(cacheDir, "media"), LeastRecentlyUsedCacheEvictor(256 * 1024 * 1024), StandaloneDatabaseProvider(this@Service))

        val cacheDataSource: CacheDataSource.Factory = CacheDataSource.Factory()
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .setUpstreamDataSourceFactory(okhttpDataSource)
            .setCache(playerCache)

        val dualDataSource = DataSource.Factory {
            val mediaMetadata: MediaMetadata
            runBlocking(Dispatchers.Main) {
                mediaMetadata = exoPlayer.mediaMetadata
            }

            okhttpDataSource.setUserAgent(mediaMetadata.extras?.getString("agent"))
            if (mediaMetadata.extras?.getBoolean("live") != true) {
                cacheDataSource.createDataSource()
            } else {
                okhttpDataSource.createDataSource()
            }
        }

        val hlsMediaSource: HlsMediaSource.Factory = HlsMediaSource.Factory(dualDataSource)
            .setAllowChunklessPreparation(false)

        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            // .setMediaSourceFactory(hlsMediaSource)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .build()

        exoPlayer.addListener(this)

        val backButton: CommandButton = CommandButton.Builder(CommandButton.ICON_SKIP_BACK_10)
            .setDisplayName("Seek Back")
            .setSessionCommand(backCommand)
            .build()

        val forwardButton: CommandButton = CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_10)
            .setDisplayName("Seek Forward")
            .setSessionCommand(forwardCommand)
            .build()

        playerSession = MediaLibrarySession.Builder(this, exoPlayer, this)
            .setCustomLayout(ImmutableList.of(backButton, forwardButton))
            .build()

        val intentFilter = IntentFilter()
        intentFilter.addAction("h.lillie.ytplayer.service.info")
        intentFilter.addAction("h.lillie.ytplayer.service.timer")
        if (Build.VERSION.SDK_INT <= 32) {
            registerReceiver(playerBroadcastReceiver, intentFilter)
        } else {
            registerReceiver(playerBroadcastReceiver, intentFilter, RECEIVER_NOT_EXPORTED)
        }

        playerHandler = Handler(Looper.getMainLooper())
        playerHandler.post(playerTask)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return playerSession
    }

    override fun onDestroy() {
        playerBufferingTimer?.cancel()
        playerTimer?.cancel()
        playerBufferingTimer = null
        playerTimer = null
        sponsorBlock = null
        if (this::playerHandler.isInitialized) {
            playerHandler.removeCallbacksAndMessages(null)
        }
        unregisterReceiver(playerBroadcastReceiver)
        if (this::playerCache.isInitialized) {
            playerCache.release()
        }
        if (this::exoPlayer.isInitialized) {
            exoPlayer.release()
        }
        playerSession?.release()
        playerSession = null
        super.onDestroy()
    }

    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
        val connectionResult: MediaSession.ConnectionResult = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
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
                .add(backCommand)
                .add(forwardCommand)
                .build()
            ).build()

        return connectionResult
    }

    override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> {
        val currentMediaItem: MediaItem? = exoPlayer.currentMediaItem
        if (currentMediaItem != null) {
            return Futures.immediateFuture(LibraryResult.ofItem(currentMediaItem, params))
        }

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

    override fun onSetMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val searchQuery: String? = mediaItems[0].requestMetadata.searchQuery
        if (searchQuery != null) {
            val broadcastIntent = Intent("h.lillie.ytplayer.service.info")
            broadcastIntent.setPackage(this.packageName)
            broadcastIntent.putExtra("videoID", null as String?)
            broadcastIntent.putExtra("searchQuery", searchQuery)
            sendBroadcast(broadcastIntent)
        }
        return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
    }

    override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
        if (customCommand.customAction == "back") {
            playerSession?.player?.seekBack()
        }
        if (customCommand.customAction == "forward") {
            playerSession?.player?.seekForward()
        }
        return super.onCustomCommand(session, controller, customCommand, args)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        super.onPlaybackStateChanged(playbackState)
        when (playbackState) {
            Player.STATE_BUFFERING -> {
                playerBufferingTimer?.cancel()
                if (exoPlayer.currentPosition == 0L) {
                    playerBufferingTimer = object: CountDownTimer(TimeUnit.SECONDS.toMillis(10), 1000) {
                        override fun onTick(millisUntilFinished: Long) {
                        }
                        override fun onFinish() {
                            val playerMediaItem: MediaItem = MediaItem.Builder()
                                .setMimeType(MimeTypes.APPLICATION_M3U8)
                                .setMediaId("root")
                                .setMediaMetadata(exoPlayer.mediaMetadata)
                                .setSubtitleConfigurations(subtitlesList)
                                .setUri(exoPlayer.mediaMetadata.extras?.getString("safariurl"))
                                .build()

                            exoPlayer.setMediaItem(playerMediaItem)
                            exoPlayer.playWhenReady = true
                            exoPlayer.prepare()
                        }
                    }.start()
                }
            }
            Player.STATE_ENDED, Player.STATE_IDLE, Player.STATE_READY -> playerBufferingTimer?.cancel()
        }
    }

    @SuppressLint("SwitchIntDef")
    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)
        when ((error as ExoPlaybackException).type) {
            ExoPlaybackException.TYPE_SOURCE -> {
                if (exoPlayer.mediaMetadata.extras?.getBoolean("live") == true && exoPlayer.mediaMetadata.extras?.getString("expiration")!!.toLong() < System.currentTimeMillis()) {
                    val broadcastIntent = Intent("h.lillie.ytplayer.service.info")
                    broadcastIntent.setPackage(this.packageName)
                    broadcastIntent.putExtra("videoID", exoPlayer.mediaMetadata.extras?.getString("id"))
                    broadcastIntent.putExtra("searchQuery", null as String?)
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

    private val playerBroadcastReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = coroutineScope {
            if (intent?.action == "h.lillie.ytplayer.service.info") {
                val request = Requests()
                val info = request.ytdlp(intent.extras!!.getString("videoID"), intent.extras!!.getString("searchQuery")) ?: return@coroutineScope
                val dislikes = request.returnYouTubeDislike(this@Service, info.id)
                sponsorBlock = request.sponsorBlock(this@Service, info.id)
                playerTimer?.cancel()
                playerTimer = null

                val playerExtraInfo = Bundle()
                playerExtraInfo.putString("id", info.id)
                playerExtraInfo.putString("type", info.type)
                playerExtraInfo.putBoolean("live", info.live)
                playerExtraInfo.putString("agent", info.agent)
                playerExtraInfo.putString("safariurl", info.safariurl)
                playerExtraInfo.putString("expiration", info.expiration)
                playerExtraInfo.putInt("views", info.views)
                playerExtraInfo.putInt("likes", info.likes)
                if (dislikes != null) {
                    playerExtraInfo.putInt("dislikes", dislikes)
                }

                val playerMediaMetadata: MediaMetadata = MediaMetadata.Builder()
                    .setTitle(info.title)
                    .setArtist(info.author)
                    .setArtworkUri(info.artwork.toUri())
                    .setExtras(playerExtraInfo)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()

                val playerMediaItem: MediaItem.Builder = MediaItem.Builder()
                    // .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .setMediaId("root")
                    .setMediaMetadata(playerMediaMetadata)

                if (info.iosurl != null) {
                    playerMediaItem.setUri(info.iosurl.toUri())
                } else {
                    playerMediaItem.setUri(info.safariurl.toUri())
                }

                if (info.subtitles != null) {
                    val subtitles = JSONArray(Json.encodeToString(info.subtitles))

                    for (i in 0 until subtitles.length()) {
                        val playerCaptions: MediaItem.SubtitleConfiguration = MediaItem.SubtitleConfiguration.Builder(subtitles.getJSONObject(i).optString("url").toUri())
                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                            .setMimeType(MimeTypes.TEXT_VTT)
                            .setLanguage(subtitles.getJSONObject(i).optString("id"))
                            .build()

                        subtitlesList.add(playerCaptions)
                    }

                    playerMediaItem.setSubtitleConfigurations(subtitlesList)

                    val broadcastIntent = Intent("h.lillie.ytplayer.activity.subtitles")
                    broadcastIntent.setPackage(this@Service.packageName)
                    broadcastIntent.putExtra("subtitles", subtitles.toString())
                    sendBroadcast(broadcastIntent)
                } else {
                    val broadcastIntent = Intent("h.lillie.ytplayer.activity.subtitles")
                    broadcastIntent.setPackage(this@Service.packageName)
                    broadcastIntent.putExtra("subtitles", null as String?)
                    sendBroadcast(broadcastIntent)
                }

                withContext(Dispatchers.Main) {
                    exoPlayer.setMediaItem(playerMediaItem.build())
                    exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
                    exoPlayer.playbackParameters = PlaybackParameters(1.0f)
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                    exoPlayer.playWhenReady = true
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
            if (sponsorBlock != null && this@Service::exoPlayer.isInitialized && exoPlayer.mediaMetadata.extras?.getBoolean("live") != true) {
                for (i in 0 until sponsorBlock.length()) {
                    val decimalFormat = DecimalFormat("#.###")

                    val segment: JSONArray = sponsorBlock.getJSONObject(i).getJSONArray("segment")
                    val position: Double = decimalFormat.format(exoPlayer.currentPosition / 1000.0).toDouble()
                    val segment0: Double = decimalFormat.format(segment[0]).toDouble()
                    val segment1: Double = decimalFormat.format(segment[1]).toDouble()

                    if (position >= segment0 && position < segment1) {
                        exoPlayer.seekTo(decimalFormat.format(segment1 * 1000.0).toLong())
                        Toast.makeText(this@Service, "Sponsor skipped", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            playerHandler.postDelayed(this, 1000)
        }
    }
}