package h.lillie.ytplayer

import android.content.ClipboardManager
import android.net.Uri
import android.net.http.HttpEngine
import android.os.Build
import android.os.Bundle
import android.os.ext.SdkExtensions
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpEngineDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class Player: ComponentActivity() {
    private var isFirstLaunch: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !isFirstLaunch) {
            isFirstLaunch = true
            val clipManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = clipManager.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val url: String = clipData.getItemAt(0).text.toString()
                val youtubeRegex = Regex("^.*(?:(?:youtu\\.be\\/|v\\/|vi\\/|u\\/\\w\\/|embed\\/|shorts\\/|live\\/)|(?:(?:watch)?\\?v(?:i)?=|\\&v(?:i)?=))([^#\\&\\?]*).*")
                if (youtubeRegex.containsMatchIn(url)) {
                    val id = youtubeRegex.findAll(url).map { it.groupValues[1] }.joinToString()
                    CoroutineScope(Dispatchers.Main).launch {
                        val request = Requests()
                        request.ytdlp(id)
                        request.sponsorBlock(id)
                        setContent {
                            CreatePlayer()
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun CreatePlayer() {
        if (Build.VERSION.SDK_INT >= 34 && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 7) {
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

            val httpEngine: HttpEngine = HttpEngine.Builder(this)
                .setEnableHttp2(true)
                .build()

            val httpEngineDataSource: HttpEngineDataSource.Factory = HttpEngineDataSource.Factory(httpEngine, MoreExecutors.directExecutor())
            val hlsSource: MediaSource = HlsMediaSource.Factory(httpEngineDataSource)
                .setAllowChunklessPreparation(false)
                .createMediaSource(playerMediaItem)

            val exoPlayer: ExoPlayer = ExoPlayer.Builder(this)
                .setSeekBackIncrementMs(10000)
                .setSeekForwardIncrementMs(10000)
                .build()

            exoPlayer.setMediaSource(hlsSource)

            exoPlayer.playWhenReady = true
            exoPlayer.prepare()

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    PlayerView(context).apply {
                        player = exoPlayer
                        useController = true
                    }
                },
            )
        }
    }
}