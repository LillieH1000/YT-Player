package h.lillie.ytplayer

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.text.format.Formatter
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import androidx.mediarouter.app.MediaRouteButton
import coil3.load
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
@Suppress("Deprecation")
@SuppressLint("ClickableViewAccessibility", "ObsoleteSdkInt", "SetTextI18n", "SwitchIntDef")
class Player: AppCompatActivity(), Player.Listener {
    private lateinit var playerControllerFuture: ListenableFuture<MediaController>
    private lateinit var playerController: MediaController
    private lateinit var playerHandler: Handler
    private var isFirstLaunch: Boolean = false
    private var overlayVisible: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.player)

        onBackPressedDispatcher.addCallback(this) {
            if (!Application.androidTVDevice && !Application.chromeOSDevice) {
                when (resources.configuration.orientation) {
                    Configuration.ORIENTATION_PORTRAIT -> {
                        if (!Application.chromeOSDevice && !Application.androidTVDevice) {
                            WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
                        }
                    }
                    Configuration.ORIENTATION_LANDSCAPE -> {
                        if (!Application.chromeOSDevice && !Application.androidTVDevice) {
                            WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
                        }
                    }
                }
            }
            if (Application.androidTVDevice) {
                val overlayView: RelativeLayout = findViewById(R.id.overlayView)
                if (overlayView.visibility == View.GONE) {
                    overlayView.visibility = View.VISIBLE
                    overlayVisible = true
                    val playPauseRestartButton: ImageButton = findViewById(R.id.playPauseRestartButton)
                    playPauseRestartButton.requestFocus()
                } else {
                    overlayView.visibility = View.GONE
                    overlayVisible = false
                }
                val settingsView: LinearLayout = findViewById(R.id.settingsView)
                if (settingsView.visibility == View.VISIBLE) {
                    settingsView.visibility = View.GONE
                }
            }
        }

        if (Application.androidTVDevice) {
            createServer()
            val wifiManager = getSystemService(WIFI_SERVICE) as WifiManager
            val ipView: TextView = findViewById(R.id.ipView)
            ipView.text = "http://${Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)}:8090/?url=youtubevideourl"
            ipView.visibility = View.VISIBLE
        }

        when {
            intent?.action == Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    isFirstLaunch = true
                    broadcast(intent.getStringExtra(Intent.EXTRA_TEXT)!!)
                    createUI()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!Application.androidTVDevice && !Application.chromeOSDevice) {
            broadcast(intent?.getStringExtra(Intent.EXTRA_TEXT)!!)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        when (newConfig.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> {
                if (!Application.chromeOSDevice && !Application.androidTVDevice) {
                    WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
                }
            }
            Configuration.ORIENTATION_LANDSCAPE -> {
                if (!Application.chromeOSDevice && !Application.androidTVDevice) {
                    WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
                }
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

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT <= 30 && Build.VERSION.SDK_INT >= 26 && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            enterPictureInPictureMode(PictureInPictureParams.Builder().build())
        }
    }

    override fun onDestroy() {
        MediaController.releaseFuture(playerControllerFuture)
        Process.killProcess(Process.myPid())
        super.onDestroy()
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event != null && (event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER || event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE || event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS)) {
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_SPACE -> {
                    if (this::playerController.isInitialized && playerController.mediaItemCount == 1) {
                        if (!playerController.isPlaying) {
                            playerController.play()
                        } else {
                            playerController.pause()
                        }
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER -> {
                    if (overlayVisible) {
                        val playPauseRestartButton: ImageButton = findViewById(R.id.playPauseRestartButton)
                        if (playPauseRestartButton.isFocused) {
                            if (this::playerController.isInitialized && playerController.mediaItemCount == 1) {
                                if (!playerController.isPlaying) {
                                    playerController.play()
                                } else {
                                    playerController.pause()
                                }
                            }
                        }
                        val settingsButton: ImageButton = findViewById(R.id.settingsButton)
                        if (settingsButton.isFocused) {
                            if (this::playerController.isInitialized && playerController.mediaItemCount == 1) {
                                val settingsView: LinearLayout = findViewById(R.id.settingsView)
                                if (settingsView.visibility == View.GONE) {
                                    settingsView.visibility = View.VISIBLE
                                    if (!Application.castExists) {
                                        settingsButton.nextFocusDownId = R.id.subtitlesSwitch
                                        val subtitlesSwitch: SwitchMaterial = findViewById(R.id.subtitlesSwitch)
                                        subtitlesSwitch.nextFocusUpId = R.id.settingsButton
                                    }
                                    val subtitlesSwitch: SwitchMaterial = findViewById(R.id.subtitlesSwitch)
                                    if (!subtitlesSwitch.isEnabled) {
                                        settingsButton.nextFocusDownId = R.id.repeatSwitch
                                        val repeatSwitch: SwitchMaterial = findViewById(R.id.repeatSwitch)
                                        repeatSwitch.nextFocusUpId = R.id.settingsButton
                                    }
                                } else {
                                    settingsView.visibility = View.GONE
                                    settingsButton.nextFocusDownId = R.id.playPauseRestartButton
                                }
                            }
                        }
                        val castView: RelativeLayout = findViewById(R.id.castView)
                        if (castView.isFocused) {
                            val castButton: MediaRouteButton = findViewById(R.id.castButton)
                            castButton.performClick()
                        }
                        val subtitlesSwitch: SwitchMaterial = findViewById(R.id.subtitlesSwitch)
                        if (subtitlesSwitch.isFocused) {
                            if (!subtitlesSwitch.isChecked) {
                                subtitlesSwitch.isChecked = true
                                playerController.trackSelectionParameters = playerController.trackSelectionParameters.buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                    .setPreferredTextLanguage("en")
                                    .build()
                            } else {
                                subtitlesSwitch.isChecked = false
                                playerController.trackSelectionParameters = playerController.trackSelectionParameters.buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                    .build()
                            }
                        }
                        val repeatSwitch: SwitchMaterial = findViewById(R.id.repeatSwitch)
                        if (repeatSwitch.isFocused) {
                            if (playerController.repeatMode == Player.REPEAT_MODE_OFF) {
                                playerController.repeatMode = Player.REPEAT_MODE_ONE
                            } else {
                                playerController.repeatMode = Player.REPEAT_MODE_OFF
                            }
                        }
                        val speedViewMinus: TextView = findViewById(R.id.speedViewMinus)
                        if (speedViewMinus.isFocused) {
                            val speedViewText: TextView = findViewById(R.id.speedViewText)
                            val decimalFormat = DecimalFormat("#.#")
                            if (decimalFormat.format(playerController.playbackParameters.speed).toFloat() > 0.1f) {
                                playerController.playbackParameters = PlaybackParameters(decimalFormat.format(playerController.playbackParameters.speed).toFloat() - 0.1f)
                                speedViewText.text = "Speed: ${decimalFormat.format(playerController.playbackParameters.speed)}x"
                            }
                        }
                        val speedViewPlus: TextView = findViewById(R.id.speedViewPlus)
                        if (speedViewPlus.isFocused) {
                            val speedViewText: TextView = findViewById(R.id.speedViewText)
                            val decimalFormat = DecimalFormat("#.#")
                            if (decimalFormat.format(playerController.playbackParameters.speed).toFloat() < 2.0f) {
                                playerController.playbackParameters = PlaybackParameters(decimalFormat.format(playerController.playbackParameters.speed).toFloat() + 0.1f)
                                speedViewText.text = "Speed: ${decimalFormat.format(playerController.playbackParameters.speed)}x"
                            }
                        }
                    }
                    if (!overlayVisible) {
                        if (this::playerController.isInitialized && playerController.mediaItemCount == 1) {
                            if (!playerController.isPlaying) {
                                playerController.play()
                            } else {
                                playerController.pause()
                            }
                        }
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    val progressSlider: Slider = findViewById(R.id.progressSlider)
                    if ((!overlayVisible || progressSlider.isFocused) && this@Player::playerController.isInitialized && playerController.mediaItemCount == 1) {
                        playerController.seekBack()
                        return true
                    }
                    return false
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    val progressSlider: Slider = findViewById(R.id.progressSlider)
                    if ((!overlayVisible || progressSlider.isFocused) && this@Player::playerController.isInitialized && playerController.mediaItemCount == 1) {
                        playerController.seekForward()
                        return true
                    }
                    return false
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            val overlayView: RelativeLayout = findViewById(R.id.overlayView)
            if (overlayView.visibility == View.VISIBLE) {
                overlayView.visibility = View.GONE
            }
            val settingsView: LinearLayout = findViewById(R.id.settingsView)
            if (settingsView.visibility == View.VISIBLE) {
                settingsView.visibility = View.GONE
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            if (!isFirstLaunch) {
                isFirstLaunch = true
                if (Application.chromeOSDevice) {
                    val clipManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    val clipData = clipManager.primaryClip
                    if (clipData != null && clipData.itemCount > 0) {
                        broadcast(clipData.getItemAt(0).text.toString())
                        createUI()
                    }
                }
            }
            when (resources.configuration.orientation) {
                Configuration.ORIENTATION_PORTRAIT -> {
                    if (!Application.chromeOSDevice && !Application.androidTVDevice) {
                        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
                    }
                }
                Configuration.ORIENTATION_LANDSCAPE -> {
                    if (!Application.chromeOSDevice && !Application.androidTVDevice) {
                        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
                    }
                }
            }
        }
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        super.onRepeatModeChanged(repeatMode)
        val repeatSwitch: SwitchMaterial = findViewById(R.id.repeatSwitch)
        if (repeatMode == Player.REPEAT_MODE_OFF) {
            repeatSwitch.isChecked = false
        } else {
            repeatSwitch.isChecked = true
        }
    }

    private fun broadcast(url: String) {
        val youtubeRegex = Regex("^.*(?:(?:youtu\\.be\\/|v\\/|vi\\/|u\\/\\w\\/|embed\\/|shorts\\/|live\\/)|(?:(?:watch)?\\?v(?:i)?=|\\&v(?:i)?=))([^#\\&\\?]*).*")
        if (youtubeRegex.containsMatchIn(url)) {
            val id = youtubeRegex.findAll(url).map { it.groupValues[1] }.joinToString()

            if (Application.castActive) {
                Toast.makeText(this, "Failed, Please Disable Cast First", Toast.LENGTH_LONG).show()
                return
            }

            if (Application.androidTVDevice) {
                val ipView: TextView = findViewById(R.id.ipView)
                ipView.visibility = View.GONE
            }

            if (this::playerController.isInitialized && playerController.mediaItemCount == 1) {
                playerController.stop()
                playerController.removeMediaItem(0)
                val titleView: TextView = findViewById(R.id.titleView)
                titleView.text = ""
                val playPauseRestartButton: ImageButton = findViewById(R.id.playPauseRestartButton)
                playPauseRestartButton.setImageDrawable(null)
                val progressSlider: Slider = findViewById(R.id.progressSlider)
                progressSlider.value = 0f
                val timeView: TextView = findViewById(R.id.timeView)
                timeView.text = ""
            }

            CoroutineScope(Dispatchers.Main).launch {
                val request = Requests()
                request.ytdlp(id)
                request.sponsorBlock(id)
                request.returnYouTubeDislike(id)

                val artworkView: ImageView = findViewById(R.id.artworkView)
                artworkView.load(Application.artwork)

                val sessionToken = SessionToken(this@Player, ComponentName(this@Player, PlayerService::class.java))
                playerControllerFuture = MediaController.Builder(this@Player, sessionToken).buildAsync()
                playerControllerFuture.addListener({
                    playerController = playerControllerFuture.get()
                    playerController.addListener(this@Player)

                    val playerView: PlayerView = findViewById(R.id.playerView)
                    playerView.player = playerController

                    if (Build.VERSION.SDK_INT >= 31 && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                        setPictureInPictureParams(
                            PictureInPictureParams.Builder()
                                .setAutoEnterEnabled(true)
                                .setSeamlessResizeEnabled(true)
                                .build()
                        )
                    }

                    playerHandler = Handler(Looper.getMainLooper())
                    playerHandler.post(playerTask)
                    updateUI()

                    val broadcastIntent = Intent("h.lillie.ytplayer.info")
                    broadcastIntent.setPackage(this@Player.packageName)
                    sendBroadcast(broadcastIntent)
                }, MoreExecutors.directExecutor())
            }
        } else {
            Toast.makeText(this, "Invalid Url", Toast.LENGTH_LONG).show()
        }
    }

    private fun createServer() {
        embeddedServer(CIO, port = 8090) {
            routing {
                get("/") {
                    val url = call.request.queryParameters["url"]
                    if (url != null) {
                        CoroutineScope(Dispatchers.Main).launch {
                            broadcast(url)
                        }
                    }
                }
            }
        }.start(wait = false)
    }

    private var gestureDirection: Int = 0

    private fun createUI() {
        val leftView: View = findViewById(R.id.leftView)
        leftView.setOnTouchListener(object : View.OnTouchListener {
            val gestureDetector = GestureDetector(this@Player, playerTouch)
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                gestureDirection = 0
                return gestureDetector.onTouchEvent(event!!)
            }
        })

        val middleView: RelativeLayout = findViewById(R.id.middleView)
        middleView.setOnTouchListener(object : View.OnTouchListener {
            val gestureDetector = GestureDetector(this@Player, playerTouch)
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                gestureDirection = 1
                return gestureDetector.onTouchEvent(event!!)
            }
        })

        val rightView: RelativeLayout = findViewById(R.id.rightView)
        rightView.setOnTouchListener(object : View.OnTouchListener {
            val gestureDetector = GestureDetector(this@Player, playerTouch)
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                gestureDirection = 2
                return gestureDetector.onTouchEvent(event!!)
            }
        })

        val playPauseRestartButton: ImageButton = findViewById(R.id.playPauseRestartButton)
        playPauseRestartButton.setOnClickListener {
            if (this::playerController.isInitialized && playerController.mediaItemCount == 1) {
                if (!playerController.isPlaying) {
                    playerController.play()
                } else {
                    playerController.pause()
                }
            }
        }

        val progressSlider: Slider = findViewById(R.id.progressSlider)
        progressSlider.addOnChangeListener { _, value, fromUser ->
            if (this::playerController.isInitialized && playerController.mediaItemCount == 1) {
                val duration = playerController.duration
                val position = playerController.currentPosition
                if (fromUser && duration >= 0 && position >= 0 && value <= duration) {
                    playerController.seekTo(value.toLong())
                }
            }
        }

        val infoButton: ImageButton = findViewById(R.id.infoButton)
        infoButton.setOnClickListener {
        }

        val settingsButton: ImageButton = findViewById(R.id.settingsButton)
        settingsButton.setOnClickListener {
            if (this::playerController.isInitialized && playerController.mediaItemCount == 1) {
                val settingsView: LinearLayout = findViewById(R.id.settingsView)
                if (settingsView.visibility == View.GONE) {
                    settingsView.visibility = View.VISIBLE
                } else {
                    settingsView.visibility = View.GONE
                }
            }
        }

        val castView: RelativeLayout = findViewById(R.id.castView)
        castView.setOnClickListener {
            val castButton: MediaRouteButton = findViewById(R.id.castButton)
            castButton.performClick()
        }

        val subtitlesSwitch: SwitchMaterial = findViewById(R.id.subtitlesSwitch)
        subtitlesSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            if (buttonView.isPressed) {
                if (!isChecked) {
                    playerController.trackSelectionParameters = playerController.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                } else {
                    playerController.trackSelectionParameters = playerController.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setPreferredTextLanguage("en")
                        .build()
                }
            }
        }

        val repeatSwitch: SwitchMaterial = findViewById(R.id.repeatSwitch)
        repeatSwitch.setOnCheckedChangeListener { buttonView, _ ->
            if (buttonView.isPressed) {
                if (playerController.repeatMode == Player.REPEAT_MODE_OFF) {
                    playerController.repeatMode = Player.REPEAT_MODE_ONE
                } else {
                    playerController.repeatMode = Player.REPEAT_MODE_OFF
                }
            }
        }

        val speedViewText: TextView = findViewById(R.id.speedViewText)

        val speedViewMinus: TextView = findViewById(R.id.speedViewMinus)
        speedViewMinus.setOnClickListener {
            val decimalFormat = DecimalFormat("#.#")
            if (decimalFormat.format(playerController.playbackParameters.speed).toFloat() > 0.1f) {
                playerController.playbackParameters = PlaybackParameters(decimalFormat.format(playerController.playbackParameters.speed).toFloat() - 0.1f)
                speedViewText.text = "Speed: ${decimalFormat.format(playerController.playbackParameters.speed)}x"
            }
        }

        val speedViewPlus: TextView = findViewById(R.id.speedViewPlus)
        speedViewPlus.setOnClickListener {
            val decimalFormat = DecimalFormat("#.#")
            if (decimalFormat.format(playerController.playbackParameters.speed).toFloat() < 2.0f) {
                playerController.playbackParameters = PlaybackParameters(decimalFormat.format(playerController.playbackParameters.speed).toFloat() + 0.1f)
                speedViewText.text = "Speed: ${decimalFormat.format(playerController.playbackParameters.speed)}x"
            }
        }
    }

    private fun updateUI() {
        val titleView: TextView = findViewById(R.id.titleView)
        titleView.text = Application.title

        val settingsView: LinearLayout = findViewById(R.id.settingsView)
        if (settingsView.visibility == View.VISIBLE) {
            settingsView.visibility = View.GONE
        }

        val castView: RelativeLayout = findViewById(R.id.castView)
        if (!Application.castExists) {
            castView.visibility = View.GONE
        } else {
            CastButtonFactory.setUpMediaRouteButton(this, findViewById(R.id.castButton))
            castView.visibility = View.VISIBLE
        }

        settingsView.requestLayout()

        val subtitlesSwitch: SwitchMaterial = findViewById(R.id.subtitlesSwitch)
        subtitlesSwitch.isChecked = false
        if (Application.enCaptions != "null") {
            subtitlesSwitch.isEnabled = true
        } else {
            subtitlesSwitch.isEnabled = false
        }

        val speedViewText: TextView = findViewById(R.id.speedViewText)
        speedViewText.text = "Speed: 1x"

        val menuButtons: LinearLayout = findViewById(R.id.menuButtons)
        if (Application.live) {
            menuButtons.visibility = View.GONE
        } else {
            menuButtons.visibility = View.VISIBLE
        }
    }

    private fun time(time: Long): String {
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

    private val playerTouch = object: GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            return true
        }
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            val overlayView: RelativeLayout = findViewById(R.id.overlayView)
            if (overlayView.visibility == View.GONE) {
                overlayView.visibility = View.VISIBLE
            } else {
                overlayView.visibility = View.GONE
            }
            val settingsView: LinearLayout = findViewById(R.id.settingsView)
            if (settingsView.visibility == View.VISIBLE) {
                settingsView.visibility = View.GONE
            }
            return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (this@Player::playerController.isInitialized && playerController.mediaItemCount == 1) {
                if (gestureDirection == 0) {
                    playerController.seekBack()
                }
                if (gestureDirection == 2) {
                    playerController.seekForward()
                }
            }
            return true
        }
    }
    
    private val playerTask = object: Runnable {
        override fun run() {
            if (this@Player::playerController.isInitialized && playerController.mediaItemCount == 1) {
                val playerView: PlayerView = findViewById(R.id.playerView)
                val artworkView: ImageView = findViewById(R.id.artworkView)
                if (Application.castActive) {
                    playerView.visibility = View.GONE
                    artworkView.visibility = View.VISIBLE
                } else {
                    playerView.visibility = View.VISIBLE
                    artworkView.visibility = View.GONE
                }

                val playPauseRestartButton: ImageButton = findViewById(R.id.playPauseRestartButton)
                if (playerController.playbackState == Player.STATE_ENDED) {
                    playPauseRestartButton.setImageResource(androidx.media3.session.R.drawable.media3_icon_skip_back)
                } else {
                    if (!playerController.isPlaying) {
                        playPauseRestartButton.setImageResource(androidx.media3.session.R.drawable.media3_icon_play)
                    } else {
                        playPauseRestartButton.setImageResource(androidx.media3.session.R.drawable.media3_icon_pause)
                    }
                }

                val duration = playerController.duration
                val position = playerController.currentPosition
                if (duration >= 0 && position >= 0) {
                    val progressSlider: Slider = findViewById(R.id.progressSlider)
                    progressSlider.valueTo = duration.toFloat()

                    if (position <= duration) {
                        progressSlider.value = position.toFloat()
                    }
                    if (position > duration) {
                        progressSlider.value = duration.toFloat()
                    }

                    val timeView: TextView = findViewById(R.id.timeView)
                    timeView.text = "${time(position)} / ${time(duration)}"
                }
            }
            playerHandler.postDelayed(this, 1000)
        }
    }
}