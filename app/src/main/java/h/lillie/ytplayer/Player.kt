package h.lillie.ytplayer

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.StrictMode
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

@Suppress("Deprecation")
class Player : AppCompatActivity() {
    private lateinit var playerControllerFuture: ListenableFuture<MediaController>
    private lateinit var playerController: MediaController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.player)

        val policy = StrictMode.ThreadPolicy.Builder().permitNetwork().build()
        StrictMode.setThreadPolicy(policy)

        val url = getInfo()

        val sessionToken = SessionToken(this, ComponentName(this, PlayerService::class.java))
        playerControllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        playerControllerFuture.addListener({
            playerController = playerControllerFuture.get()

            val playerView: PlayerView = findViewById(R.id.playerView)
            playerView.player = playerController

            setPictureInPictureParams(
                PictureInPictureParams.Builder()
                    .setAutoEnterEnabled(true)
                    .setSeamlessResizeEnabled(true)
                    .build()
            )

            val playerMediaItem: MediaItem = MediaItem.Builder()
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .setUri(Uri.parse(url))
                .build()

            playerController.addMediaItem(playerMediaItem)
            playerController.playWhenReady = true
            playerController.prepare()
        }, MoreExecutors.directExecutor())
    }

    override fun onStop() {
        super.onStop()
        MediaController.releaseFuture(playerControllerFuture)
    }

    @SuppressLint("SwitchIntDef")
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        when (newConfig.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> {
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
            Configuration.ORIENTATION_LANDSCAPE -> {
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN
            }
        }
    }

    override fun onDestroy() {
        stopService(Intent(this, PlayerService::class.java))
        super.onDestroy()
    }

    private fun getInfo() : String {
        val body = """{
            "context": {
                "client": {
                    "hl": "en",
                    "gl": "${this.resources.configuration.locales.get(0).country}",
                    "clientName": "IOS",
                    "clientVersion": "19.45.4",
                    "deviceMake": "Apple",
                    "deviceModel": "iPhone16,2",
                    "osName": "iPhone",
                    "osVersion": "18.1.0.22B83"
                }
            },
            "contentCheckOk": true,
            "racyCheckOk": true,
            "videoId": "yUw36wY8fMw"
        }"""

        val requestBody = body.trimIndent().toRequestBody()

        val client: OkHttpClient = OkHttpClient.Builder().build()

        val request = Request.Builder()
            .method("POST", requestBody)
            .header("User-Agent", "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)")
            .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
            .build()

        val result = client.newCall(request).execute().body.string()
        val jsonObject = JSONObject(result)

        return jsonObject.getJSONObject("streamingData").optString("hlsManifestUrl")
    }
}