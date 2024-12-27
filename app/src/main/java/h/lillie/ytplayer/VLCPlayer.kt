package h.lillie.ytplayer

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.StrictMode
import android.util.Log
import android.view.MotionEvent
import android.view.WindowInsets
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.videolan.libvlc.MediaPlayer
import kotlin.math.abs

@SuppressLint("SwitchIntDef")
class VLCPlayer : AppCompatActivity(), SensorEventListener {
    private lateinit var playerServiceBinder: VLCPlayerService.LibVLCBinder
    private lateinit var playerSource: MediaPlayer
    private lateinit var playerHandler: Handler
    private var playerSensor: Sensor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.vlcplayer)

        onBackPressedDispatcher.addCallback(this) {}

        when {
            intent?.action == Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    when (resources.configuration.orientation) {
                        Configuration.ORIENTATION_PORTRAIT -> {
                            window.insetsController?.apply {
                                show(WindowInsets.Type.systemBars())
                            }
                        }
                        Configuration.ORIENTATION_LANDSCAPE -> {
                            window.insetsController?.apply {
                                hide(WindowInsets.Type.systemBars())
                            }
                        }
                    }

                    val sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
                    playerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                    sensorManager.registerListener(this, playerSensor, 500 * 1000)

                    val intentFilter = IntentFilter()
                    intentFilter.addAction("h.lillie.ytplayer.register")
                    registerReceiver(playerBroadcastReceiver, intentFilter, RECEIVER_NOT_EXPORTED)

                    broadcast(intent)
                    createUI()
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
                window.insetsController?.apply {
                    show(WindowInsets.Type.systemBars())
                }
            }
            Configuration.ORIENTATION_LANDSCAPE -> {
                window.insetsController?.apply {
                    hide(WindowInsets.Type.systemBars())
                }
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null && event.sensor == playerSensor) {
            if ((abs(event.values[1]) > abs(event.values[0])) && event.values[1] > 1) {
                // Portrait
            } else {
                if (event.values[0] > 1) {
                    // Landscape
                } else if (event.values[0] < -1) {
                    // Landscape Reverse
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    override fun onResume() {
        super.onResume()
        Log.d("YT Player", "Entered Foreground")
        if (this::playerSource.isInitialized && !playerSource.vlcVout.areViewsAttached()) {
            Log.d("YT Player", "Attach Views")
            playerSource.attachViews(findViewById(R.id.playerView), null, false, false)
        }
        if (this::playerHandler.isInitialized) {
            playerHandler.post(playerTask)
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d("YT Player", "Entered Background")
        playerSource.detachViews()
        playerHandler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        unregisterReceiver(playerBroadcastReceiver)
        stopService(Intent(this, VLCPlayerService::class.java))
        super.onDestroy()
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event != null && (event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER || event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS)) {
            return true
        }
        return super.onTouchEvent(event)
    }

    private fun broadcast(intent: Intent) {
        val youtubeRegex = Regex("^.*(?:(?:youtu\\.be\\/|v\\/|vi\\/|u\\/\\w\\/|embed\\/|shorts\\/|live\\/)|(?:(?:watch)?\\?v(?:i)?=|\\&v(?:i)?=))([^#\\&\\?]*).*")
        if (youtubeRegex.containsMatchIn(intent.getStringExtra(Intent.EXTRA_TEXT)!!)) {
            val result = youtubeRegex.findAll(intent.getStringExtra(Intent.EXTRA_TEXT)!!).map { it.groupValues[1] }.joinToString()

            val policy = StrictMode.ThreadPolicy.Builder().permitNetwork().build()
            StrictMode.setThreadPolicy(policy)

            innertube(result)
            sponsorBlock(result)
            returnYouTubeDislike(result)

            bindService(Intent(this, VLCPlayerService::class.java), object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    playerServiceBinder = service as VLCPlayerService.LibVLCBinder
                    playerServiceBinder.setView(findViewById(R.id.playerView))
                    
                    val broadcastIntent = Intent("h.lillie.ytplayer.info")
                    broadcastIntent.setPackage(this@VLCPlayer.packageName)
                    sendBroadcast(broadcastIntent)
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                }
            }, Context.BIND_AUTO_CREATE)
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
        Application.views = jsonObject.getJSONObject("videoDetails").optString("viewCount")
        Application.live = jsonObject.getJSONObject("videoDetails").optBoolean("isLive")

        var audioInfo = 0
        var audioUrl = ""
        val adaptiveFormats = jsonObject.getJSONObject("streamingData").getJSONArray("adaptiveFormats")
        for (i in 0 until adaptiveFormats.length()) {
            if (adaptiveFormats.getJSONObject(i).optString("mimeType").contains("audio/mp4") && adaptiveFormats.getJSONObject(i).optString("audioQuality") == "AUDIO_QUALITY_HIGH" && audioInfo <= 2) {
                audioInfo = 3
                audioUrl = adaptiveFormats.getJSONObject(i).optString("url")
            }
            if (adaptiveFormats.getJSONObject(i).optString("mimeType").contains("audio/mp4") && adaptiveFormats.getJSONObject(i).optString("audioQuality") == "AUDIO_QUALITY_MEDIUM" && audioInfo <= 1) {
                audioInfo = 2
                audioUrl = adaptiveFormats.getJSONObject(i).optString("url")
            }
            if (adaptiveFormats.getJSONObject(i).optString("mimeType").contains("audio/mp4") && adaptiveFormats.getJSONObject(i).optString("audioQuality") == "AUDIO_QUALITY_LOW" && audioInfo == 0) {
                audioInfo = 1
                audioUrl = adaptiveFormats.getJSONObject(i).optString("url")
            }
        }
        Application.audioUrl = audioUrl
        Application.hlsUrl = jsonObject.getJSONObject("streamingData").optString("hlsManifestUrl")
    }

    private fun sponsorBlock(videoId: String) {
        val client: OkHttpClient = OkHttpClient.Builder().build()

        val request = Request.Builder()
            .method("GET", null)
            .url("https://sponsor.ajay.app/api/skipSegments?videoID=$videoId&categories=[\"sponsor\",\"selfpromo\",\"interaction\",\"intro\",\"outro\",\"preview\",\"music_offtopic\"]")
            .build()

        try {
            val jsonArray = JSONArray(client.newCall(request).execute().body.string())
            Application.sponsorBlock = jsonArray
        } catch (_: JSONException) {
            Application.sponsorBlock = null
        }
    }

    private fun returnYouTubeDislike(videoId: String) {
        val client: OkHttpClient = OkHttpClient.Builder().build()

        val request = Request.Builder()
            .method("GET", null)
            .url("https://returnyoutubedislikeapi.com/votes?videoId=$videoId")
            .build()

        val jsonObject = JSONObject(client.newCall(request).execute().body.string())

        Application.likes = jsonObject.optInt("likes")
        Application.dislikes = jsonObject.optInt("dislikes")
    }

    private fun createUI() {
        playerHandler = Handler(Looper.getMainLooper())
        playerHandler.post(playerTask)
    }

    private val playerBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "h.lillie.ytplayer.register") {
                playerSource = playerServiceBinder.getPlayer()
                playerSource.attachViews(findViewById(R.id.playerView), null, false, false)
            }
        }
    }
    
    private val playerTask = object : Runnable {
        override fun run() {
            playerHandler.postDelayed(this, 1000)
        }
    }
}