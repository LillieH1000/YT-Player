package h.lillie.ytplayer

import android.net.Uri
import androidx.annotation.OptIn
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
import okhttp3.OkHttpClient

@OptIn(UnstableApi::class)
class PlayerService : MediaSessionService() {
    private var playerSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val exoPlayer = ExoPlayer.Builder(this).build()
        playerSession = MediaSession.Builder(this, exoPlayer).build()

        val playerMediaMetadata: MediaMetadata = MediaMetadata.Builder()
            .setTitle(Application.title)
            .setArtist(Application.author)
            .setArtworkUri(Uri.parse(Application.artwork))
            .build()

        val playerMediaItem: MediaItem = MediaItem.Builder()
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .setMediaMetadata(playerMediaMetadata)
            .setUri(Uri.parse(Application.url))
            .build()

        val dataSourceFactory: DataSource.Factory = OkHttpDataSource.Factory(OkHttpClient.Builder().build())
        val videoSource: MediaSource = HlsMediaSource.Factory(dataSourceFactory).createMediaSource(playerMediaItem)

        exoPlayer.setMediaSource(videoSource)
        exoPlayer.playWhenReady = true
        exoPlayer.prepare()
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
}