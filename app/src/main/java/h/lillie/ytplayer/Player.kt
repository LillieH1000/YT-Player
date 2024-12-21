package h.lillie.ytplayer

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import android.view.View
import android.widget.ImageButton
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.activity.addCallback
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.material.slider.Slider
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
@Suppress("Deprecation")
@SuppressLint("ClickableViewAccessibility", "SetTextI18n", "SwitchIntDef")
class Player : AppCompatActivity() {
    private lateinit var playerControllerFuture: ListenableFuture<MediaController>
    private lateinit var playerController: MediaController
    private lateinit var playerHandler: Handler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.player)

        onBackPressedDispatcher.addCallback(this) {}

        when {
            intent?.action == Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    when (resources.configuration.orientation) {
                        Configuration.ORIENTATION_PORTRAIT -> {
                            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                        }
                        Configuration.ORIENTATION_LANDSCAPE -> {
                            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN
                        }
                    }
                    broadcast(intent)
                    ui()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        broadcast(intent!!)
    }

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

    override fun onResume() {
        super.onResume()
        if (this::playerHandler.isInitialized) {
            playerHandler.post(playerTask)
        }
    }

    override fun onStop() {
        super.onStop()
        playerHandler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        MediaController.releaseFuture(playerControllerFuture)
        stopService(Intent(this, PlayerService::class.java))
        super.onDestroy()
    }

    private fun broadcast(intent: Intent) {
        val youtubeRegex = Regex("^.*(?:(?:youtu\\.be\\/|v\\/|vi\\/|u\\/\\w\\/|embed\\/|shorts\\/|live\\/)|(?:(?:watch)?\\?v(?:i)?=|\\&v(?:i)?=))([^#\\&\\?]*).*")
        if (youtubeRegex.containsMatchIn(intent.getStringExtra(Intent.EXTRA_TEXT)!!)) {
            val result = youtubeRegex.findAll(intent.getStringExtra(Intent.EXTRA_TEXT)!!).map { it.groupValues[1] }.joinToString()

            val policy = StrictMode.ThreadPolicy.Builder().permitNetwork().build()
            StrictMode.setThreadPolicy(policy)

            innertube(result)
            sponsorBlock(result)

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

                val broadcastIntent = Intent("h.lillie.ytplayer.info")
                broadcastIntent.setPackage(this.packageName)
                sendBroadcast(broadcastIntent)
            }, MoreExecutors.directExecutor())
        }
    }

    private fun innertube(videoId: String) {
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
                "videoId": "$videoId"
            }"""

        val requestBody = body.trimIndent().toRequestBody()

        val client: OkHttpClient = OkHttpClient.Builder().build()

        val request = Request.Builder()
            .method("POST", requestBody)
            .header("User-Agent", "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)")
            .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
            .build()

        val jsonObject = JSONObject(client.newCall(request).execute().body.string())

        Application.id = jsonObject.getJSONObject("videoDetails").optString("videoId")
        Application.title = jsonObject.getJSONObject("videoDetails").optString("title")
        Application.author = jsonObject.getJSONObject("videoDetails").optString("author")
        val artworkArray = jsonObject.getJSONObject("videoDetails").getJSONObject("thumbnail").getJSONArray("thumbnails")
        Application.artwork = artworkArray.getJSONObject((artworkArray.length() - 1)).optString("url")
        val adaptiveFormats = jsonObject.getJSONObject("streamingData").getJSONArray("adaptiveFormats")
        for (i in 0 until adaptiveFormats.length()) {
            if (adaptiveFormats.getJSONObject(i).optString("mimeType").contains("audio/mp4") && adaptiveFormats.getJSONObject(i).optString("audioQuality") == "AUDIO_QUALITY_MEDIUM") {
                Application.audioUrl = adaptiveFormats.getJSONObject(i).optString("url")
            }
        }
        Application.hlsUrl = jsonObject.getJSONObject("streamingData").optString("hlsManifestUrl")
    }

    private fun sponsorBlock(videoId: String) {
        val client: OkHttpClient = OkHttpClient.Builder().build()

        val request = Request.Builder()
            .method("GET", null)
            .url("https://sponsor.ajay.app/api/skipSegments?videoID=$videoId&categories=[\"sponsor\",\"selfpromo\",\"interaction\",\"intro\",\"outro\",\"preview\",\"music_offtopic\"]")
            .build()

        Application.sponsorBlock = client.newCall(request).execute().body.string()
    }

    private fun ui() {
        CastButtonFactory.setUpMediaRouteButton(this, findViewById(R.id.castButton))

        val overlayView: RelativeLayout = findViewById(R.id.overlayView)
        overlayView.setOnClickListener {
            val overlayChildView: RelativeLayout = findViewById(R.id.overlayChildView)
            if (overlayChildView.visibility == View.GONE) {
                overlayChildView.visibility = View.VISIBLE
            } else {
                overlayChildView.visibility = View.GONE
            }
        }

        val playPauseRestartButton: ImageButton = findViewById(R.id.playPauseRestartButton)
        playPauseRestartButton.setOnClickListener {
            if (!playerController.isPlaying) {
                playerController.play()
            } else {
                playerController.pause()
            }
        }

        val backButton: ImageButton = findViewById(R.id.backButton)
        backButton.setOnClickListener {
            playerController.seekBack()
        }

        val forwardButton: ImageButton = findViewById(R.id.forwardButton)
        forwardButton.setOnClickListener {
            playerController.seekForward()
        }

        val progressSlider: Slider = findViewById(R.id.progressSlider)
        progressSlider.addOnChangeListener { _, value, fromUser ->
            val duration = playerController.duration
            val position = playerController.currentPosition
            if (fromUser && duration >= 0 && position >= 0 && position <= duration) {
                playerController.seekTo(value.toLong())
            }
        }

        val repeatButton: ImageButton = findViewById(R.id.repeatButton)
        repeatButton.setOnClickListener {
            if (playerController.repeatMode == Player.REPEAT_MODE_OFF) {
                playerController.repeatMode = Player.REPEAT_MODE_ONE
            } else {
                playerController.repeatMode = Player.REPEAT_MODE_OFF
            }
        }

        val shareButton: ImageButton = findViewById(R.id.shareButton)
        shareButton.setOnClickListener {
            startActivity(Intent.createChooser(Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "https://youtu.be/${Application.id}")
                type = "text/plain"
            }, null))
        }

        playerHandler = Handler(Looper.getMainLooper())
        playerHandler.post(playerTask)
    }

    private fun time(time: Long) : String {
        val hours: Int = TimeUnit.MILLISECONDS.toHours(time).toInt()
        val minutes: Int = (TimeUnit.MILLISECONDS.toMinutes(time) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(time))).toInt()
        val seconds: Int = (TimeUnit.MILLISECONDS.toSeconds(time) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(time))).toInt()
        var formatted = ""
        if (hours != 0) {
            formatted += "$hours:"
        }
        if (formatted != "") {
            if (minutes >= 10) {
                formatted += "$minutes:"
            } else {
                formatted += "0$minutes:"
            }
        }
        if (formatted == "") {
            formatted += "$minutes:"
        }
        if (seconds >= 10) {
            formatted += seconds
        } else {
            formatted += "0$seconds"
        }
        return formatted
    }
    
    private val playerTask = object : Runnable {
        override fun run() {
            if (this@Player::playerController.isInitialized) {
                val playPauseRestartButton: ImageButton = findViewById(R.id.playPauseRestartButton)
                if (!playerController.isPlaying) {
                    playPauseRestartButton.setImageResource(androidx.media3.session.R.drawable.media3_icon_play)
                } else {
                    playPauseRestartButton.setImageResource(androidx.media3.session.R.drawable.media3_icon_pause)
                }

                val repeatButton: ImageButton = findViewById(R.id.repeatButton)
                if (playerController.repeatMode == Player.REPEAT_MODE_OFF) {
                    repeatButton.setImageResource(androidx.media3.session.R.drawable.media3_icon_repeat_all)
                } else {
                    repeatButton.setImageResource(androidx.media3.session.R.drawable.media3_icon_repeat_one)
                }

                val duration = playerController.duration
                val position = playerController.currentPosition
                if (duration >= 0 && position >= 0 && position <= duration) {
                    val progressSlider: Slider = findViewById(R.id.progressSlider)
                    progressSlider.valueTo = duration.toFloat()
                    progressSlider.value = position.toFloat()

                    val timeView: TextView = findViewById(R.id.timeView)
                    timeView.text = "${time(position)} / ${time(duration)}"
                }
            }
            playerHandler.postDelayed(this, 1000)
        }
    }
}