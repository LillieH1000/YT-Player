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
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

@OptIn(UnstableApi::class)
@Suppress("Deprecation")
@SuppressLint("ClickableViewAccessibility", "SwitchIntDef")
class Player : AppCompatActivity() {
    private lateinit var playerControllerFuture: ListenableFuture<MediaController>
    private lateinit var playerController: MediaController
    private lateinit var playerHandler: Handler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.player)

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
                "videoId": "$result"
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

    private fun ui() {
        CastButtonFactory.setUpMediaRouteButton(this, findViewById(R.id.castButton))

        val leftView: View = findViewById(R.id.leftView)
        leftView.setOnTouchListener(object : View.OnTouchListener {
            val gestureDetector = GestureDetector(playerTouchLeft)
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                return gestureDetector.onTouchEvent(event!!)
            }
        })

        val rightView: View = findViewById(R.id.rightView)
        rightView.setOnTouchListener(object : View.OnTouchListener {
            val gestureDetector = GestureDetector(playerTouchRight)
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                return gestureDetector.onTouchEvent(event!!)
            }
        })

        val playPauseRestartButton: ImageButton = findViewById(R.id.playPauseRestartButton)
        playPauseRestartButton.setOnClickListener {
            if (!playerController.isPlaying) {
                playerController.play()
            } else {
                playerController.pause()
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
    
    private val playerTouchLeft = object : SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            return true
        }
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            val playPauseRestartButton: ImageButton = findViewById(R.id.playPauseRestartButton)
            if (playPauseRestartButton.visibility == View.GONE) {
                playPauseRestartButton.visibility = View.VISIBLE
            } else {
                playPauseRestartButton.visibility = View.GONE
            }
            val castButton: MediaRouteButton = findViewById(R.id.castButton)
            if (castButton.visibility == View.GONE) {
                castButton.visibility = View.VISIBLE
            } else {
                castButton.visibility = View.GONE
            }
            val shareButton: ImageButton = findViewById(R.id.shareButton)
            if (shareButton.visibility == View.GONE) {
                shareButton.visibility = View.VISIBLE
            } else {
                shareButton.visibility = View.GONE
            }
            return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            playerController.seekBack()
            return true
        }
    }

    private val playerTouchRight = object : SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            return true
        }
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            val playPauseRestartButton: ImageButton = findViewById(R.id.playPauseRestartButton)
            if (playPauseRestartButton.visibility == View.GONE) {
                playPauseRestartButton.visibility = View.VISIBLE
            } else {
                playPauseRestartButton.visibility = View.GONE
            }
            val castButton: MediaRouteButton = findViewById(R.id.castButton)
            if (castButton.visibility == View.GONE) {
                castButton.visibility = View.VISIBLE
            } else {
                castButton.visibility = View.GONE
            }
            val shareButton: ImageButton = findViewById(R.id.shareButton)
            if (shareButton.visibility == View.GONE) {
                shareButton.visibility = View.VISIBLE
            } else {
                shareButton.visibility = View.GONE
            }
            return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            playerController.seekForward()
            return true
        }
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
            }
            playerHandler.postDelayed(this, 1000)
        }
    }
}