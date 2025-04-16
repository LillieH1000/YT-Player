package h.lillie.ytplayer

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import com.google.common.util.concurrent.MoreExecutors
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.DecimalFormat
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@OptIn(UnstableApi::class)
@SuppressLint("UnspecifiedRegisterReceiverFlag")
class PlayerService: MediaLibraryService(), MediaLibraryService.MediaLibrarySession.Callback, Player.Listener {
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var playerCache: SimpleCache
    private lateinit var playerHandler: Handler
    private val backCommand = SessionCommand("back", Bundle.EMPTY)
    private val forwardCommand = SessionCommand("forward", Bundle.EMPTY)
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

        val trackSelector: DefaultTrackSelector = DefaultTrackSelector(this).apply {
            setParameters(buildUponParameters()
                .setForceHighestSupportedBitrate(true)
            )
        }

        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
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
        playerTimer?.cancel()
        playerTimer = null
        sponsorBlock = null
        playerHandler.removeCallbacksAndMessages(null)
        unregisterReceiver(playerBroadcastReceiver)
        playerCache.release()
        exoPlayer.release()
        playerSession?.release()
        playerSession = null
        super.onDestroy()
    }

    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
        val connectionResult: MediaSession.ConnectionResult = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailablePlayerCommands(MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .remove(Player.COMMAND_SEEK_TO_NEXT)
                .build()
            )
            .setAvailableSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
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
        } else {
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
    }

    override fun onSetMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val searchQuery: String? = mediaItems[0].requestMetadata.searchQuery
        if (searchQuery != null) {
            val broadcastIntent = Intent("h.lillie.ytplayer.service.info")
            broadcastIntent.setPackage(this.packageName)
            broadcastIntent.putExtra("searchQuery", searchQuery)
            sendBroadcast(broadcastIntent)
        }
        return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
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

    private fun BroadcastReceiver.async(coroutineContext: CoroutineContext = EmptyCoroutineContext, block: suspend CoroutineScope.() -> Unit) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch(coroutineContext) {
            block()
        }.invokeOnCompletion {
            pendingResult.finish()
        }
    }

    private val playerBroadcastReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = async {
            if (intent?.action == "h.lillie.ytplayer.service.info") {
                val request = Requests()
                val info = request.ytdlp(intent.extras!!.getString("videoID"), intent.extras!!.getString("searchQuery"))
                if (info == null) {
                    return@async
                }
                sponsorBlock = request.sponsorBlock(info.id)

                val playerExtraInfo = Bundle()
                playerExtraInfo.putString("id", info.id)
                playerExtraInfo.putBoolean("live", info.live)

                val playerMediaMetadata: MediaMetadata = MediaMetadata.Builder()
                    .setTitle(info.title)
                    .setArtist(info.author)
                    .setArtworkUri(info.artwork.toUri())
                    .setExtras(playerExtraInfo)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()

                val playerMediaItem: MediaItem.Builder = MediaItem.Builder()
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .setMediaId("root")
                    .setMediaMetadata(playerMediaMetadata)
                    .setUri(info.url.toUri())

                val gson = Gson()
                if (info.subtitles != null) {
                    val subtitles = JSONArray(gson.toJson(info.subtitles))
                    val subtitlesList = mutableListOf<MediaItem.SubtitleConfiguration>()

                    for (i in 0 until subtitles.length()) {
                        val playerCaptions: MediaItem.SubtitleConfiguration = MediaItem.SubtitleConfiguration.Builder(optString(subtitles.getJSONObject(i), "url")!!.toUri())
                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                            .setMimeType(MimeTypes.TEXT_VTT)
                            .setLanguage(optString(subtitles.getJSONObject(i), "id"))
                            .build()

                        subtitlesList.add(playerCaptions)
                    }

                    playerMediaItem.setSubtitleConfigurations(subtitlesList)

                    val broadcastIntent = Intent("h.lillie.ytplayer.activity.subtitles")
                    broadcastIntent.setPackage(this@PlayerService.packageName)
                    broadcastIntent.putExtra("subtitles", subtitles.toString())
                    sendBroadcast(broadcastIntent)
                } else {
                    val broadcastIntent = Intent("h.lillie.ytplayer.activity.subtitles")
                    broadcastIntent.setPackage(this@PlayerService.packageName)
                    broadcastIntent.putExtra("null", "")
                    sendBroadcast(broadcastIntent)
                }

                if (this@PlayerService::playerCache.isInitialized) {
                    playerCache.release()
                }
                playerCache = SimpleCache(File(cacheDir, "media"), LeastRecentlyUsedCacheEvictor(200 * 1024 * 1024), StandaloneDatabaseProvider(this@PlayerService))
                val cacheDataSource: CacheDataSource.Factory = CacheDataSource.Factory().setCache(playerCache)
                val hlsMediaSource: HlsMediaSource

                if (Build.VERSION.SDK_INT >= 34 && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 7) {
                    val httpEngine: HttpEngine = HttpEngine.Builder(this@PlayerService)
                        .setEnableHttp2(true)
                        .build()

                    val httpEngineDataSource: HttpEngineDataSource.Factory = HttpEngineDataSource.Factory(httpEngine, MoreExecutors.directExecutor())
                    if (!info.live) {
                        cacheDataSource.setUpstreamDataSourceFactory(httpEngineDataSource)

                        hlsMediaSource = HlsMediaSource.Factory(cacheDataSource)
                            .setAllowChunklessPreparation(false)
                            .createMediaSource(playerMediaItem.build())
                    } else {
                        hlsMediaSource = HlsMediaSource.Factory(httpEngineDataSource)
                            .setAllowChunklessPreparation(false)
                            .createMediaSource(playerMediaItem.build())
                    }
                } else {
                    val defaultDataSource: DefaultDataSource.Factory = DefaultDataSource.Factory(this@PlayerService)
                    if (!info.live) {
                        cacheDataSource.setUpstreamDataSourceFactory(defaultDataSource)

                        hlsMediaSource = HlsMediaSource.Factory(cacheDataSource)
                            .setAllowChunklessPreparation(false)
                            .createMediaSource(playerMediaItem.build())
                    } else {
                        hlsMediaSource = HlsMediaSource.Factory(defaultDataSource)
                            .setAllowChunklessPreparation(false)
                            .createMediaSource(playerMediaItem.build())
                    }
                }

                withContext(Dispatchers.Main) {
                    exoPlayer.setMediaSource(hlsMediaSource)
                    exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
                    exoPlayer.playbackParameters = PlaybackParameters(1.0f)
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                    exoPlayer.playWhenReady = true
                    exoPlayer.prepare()

                    return@withContext
                }

                return@async
            }

            if (intent?.action == "h.lillie.ytplayer.service.timer") {
                val enable: Boolean = intent.extras!!.getBoolean("enable")
                if (!enable) {
                    playerTimer?.cancel()
                    playerTimer = null
                } else {
                    playerTimer?.cancel()
                    playerTimer = null

                    playerTimer = object : CountDownTimer(intent.extras!!.getLong("time"), 1000) {
                        override fun onTick(millisUntilFinished: Long) {
                        }
                        override fun onFinish() {
                            exoPlayer.pause()
                        }
                    }.start()
                }
                return@async
            }
        }
    }

    private val playerTask = object: Runnable {
        override fun run() {
            val sponsorBlock: JSONArray? = sponsorBlock
            if (sponsorBlock != null && exoPlayer.mediaMetadata.extras?.getBoolean("live") != true) {
                for (i in 0 until sponsorBlock.length()) {
                    val decimalFormat = DecimalFormat("#.###")

                    val segment: JSONArray = sponsorBlock.getJSONObject(i).getJSONArray("segment")
                    val position: Double = decimalFormat.format(exoPlayer.currentPosition / 1000.0).toDouble()
                    val segment0: Double = decimalFormat.format(segment[0]).toDouble()
                    val segment1: Double = decimalFormat.format(segment[1]).toDouble()

                    if (position >= segment0 && position < segment1) {
                        exoPlayer.seekTo(decimalFormat.format(segment1 * 1000.0).toLong())
                        Toast.makeText(this@PlayerService, "Sponsor Skipped", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            playerHandler.postDelayed(this, 1000)
        }
    }
}