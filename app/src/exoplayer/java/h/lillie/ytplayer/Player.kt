package h.lillie.ytplayer

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import androidx.mediarouter.app.MediaRouteButton
import com.bumptech.glide.Glide
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.material.slider.Slider
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
@SuppressLint("ClickableViewAccessibility", "SetTextI18n", "SwitchIntDef")
class Player : AppCompatActivity(), Player.Listener {
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

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)
        if ((error as ExoPlaybackException).type == ExoPlaybackException.TYPE_SOURCE) {
            val mainView: RelativeLayout = findViewById(R.id.mainView)
            mainView.visibility = View.GONE
            val overlayView: RelativeLayout = findViewById(R.id.overlayView)
            overlayView.visibility = View.GONE
            val errorView: LinearLayout = findViewById(R.id.errorView)
            errorView.visibility = View.VISIBLE
        } else {
            Toast.makeText(this, "Unknown Error", Toast.LENGTH_LONG).show()
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event != null && (event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER || event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS)) {
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            val overlayView: RelativeLayout = findViewById(R.id.overlayView)
            overlayView.visibility = View.GONE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
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
        }
    }

    private fun broadcast(intent: Intent) {
        val youtubeRegex = Regex("^.*(?:(?:youtu\\.be\\/|v\\/|vi\\/|u\\/\\w\\/|embed\\/|shorts\\/|live\\/)|(?:(?:watch)?\\?v(?:i)?=|\\&v(?:i)?=))([^#\\&\\?]*).*")
        if (youtubeRegex.containsMatchIn(intent.getStringExtra(Intent.EXTRA_TEXT)!!)) {
            val result = youtubeRegex.findAll(intent.getStringExtra(Intent.EXTRA_TEXT)!!).map { it.groupValues[1] }.joinToString()

            if (this::playerController.isInitialized && playerController.mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_MUSIC) {
                Toast.makeText(this, "Failed, Please Disable Cast First", Toast.LENGTH_LONG).show()
                return
            }

            Application.requests(result)

            val sessionToken = SessionToken(this, ComponentName(this, PlayerService::class.java))
            playerControllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
            playerControllerFuture.addListener({
                playerController = playerControllerFuture.get()
                playerController.addListener(this)

                val playerView: PlayerView = findViewById(R.id.playerView)
                playerView.player = playerController

                setPictureInPictureParams(
                    PictureInPictureParams.Builder()
                        .setAutoEnterEnabled(true)
                        .setSeamlessResizeEnabled(true)
                        .build()
                )
                updateUI()

                val broadcastIntent = Intent("h.lillie.ytplayer.info")
                broadcastIntent.setPackage(this.packageName)
                sendBroadcast(broadcastIntent)
            }, MoreExecutors.directExecutor())
        }
    }

    private var gestureDirection: Int = 0

    private fun createUI() {
        CastButtonFactory.setUpMediaRouteButton(this, findViewById(R.id.castButton))

        val artworkView: ImageView = findViewById(R.id.artworkView)
        Glide.with(this).load(Application.artwork).into(artworkView)

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
            if (!playerController.isPlaying) {
                playerController.play()
            } else {
                playerController.pause()
            }
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

    private fun updateUI() {
        val titleView: TextView = findViewById(R.id.titleView)
        titleView.text = Application.title

        val castButton: MediaRouteButton = findViewById(R.id.castButton)
        val repeatButton: ImageButton = findViewById(R.id.repeatButton)
        if (Application.live) {
            castButton.visibility = View.GONE
            repeatButton.visibility = View.GONE
        } else {
            castButton.visibility = View.VISIBLE
            repeatButton.visibility = View.VISIBLE
        }
        val menuButtons: LinearLayout = findViewById(R.id.menuButtons)
        menuButtons.invalidate()
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

    private val playerTouch = object : GestureDetector.SimpleOnGestureListener() {
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
            return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (gestureDirection == 0) {
                playerController.seekBack()
            }
            if (gestureDirection == 2) {
                playerController.seekForward()
            }
            return true
        }
    }
    
    private val playerTask = object : Runnable {
        override fun run() {
            if (this@Player::playerController.isInitialized) {
                val playerView: PlayerView = findViewById(R.id.playerView)
                val artworkView: ImageView = findViewById(R.id.artworkView)
                if (playerController.mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_MUSIC) {
                    playerView.visibility = View.GONE
                    artworkView.visibility = View.VISIBLE
                } else {
                    playerView.visibility = View.VISIBLE
                    artworkView.visibility = View.GONE
                }

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