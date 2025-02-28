package h.lillie.ytplayer

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@SuppressLint("SwitchIntDef")
class Player: ComponentActivity(), Player.Listener {
    private lateinit var playerControllerFuture: ListenableFuture<MediaController>
    private lateinit var playerController: MediaController
    private lateinit var playerHandler: Handler
    private var isFirstLaunch: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        onBackPressedDispatcher.addCallback(this) {
            when (resources.configuration.orientation) {
                Configuration.ORIENTATION_PORTRAIT -> {
                    if (!Application.androidTVDevice && !Application.chromeOSDevice && !Application.wearOSDevice) {
                        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
                    }
                }
                Configuration.ORIENTATION_LANDSCAPE -> {
                    if (!Application.androidTVDevice && !Application.chromeOSDevice && !Application.wearOSDevice) {
                        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
                    }
                }
            }
        }

        when {
            intent?.action == Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    isFirstLaunch = true
                    createRequest(intent.getStringExtra(Intent.EXTRA_TEXT)!!)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        createRequest(intent.getStringExtra(Intent.EXTRA_TEXT)!!)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        when (newConfig.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> {
                if (!Application.androidTVDevice && !Application.chromeOSDevice && !Application.wearOSDevice) {
                    WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
                }
            }
            Configuration.ORIENTATION_LANDSCAPE -> {
                if (!Application.androidTVDevice && !Application.chromeOSDevice && !Application.wearOSDevice) {
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            if (!isFirstLaunch) {
                isFirstLaunch = true
                val clipManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clipData = clipManager.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    createRequest(clipData.getItemAt(0).text.toString())
                }
            }
            when (resources.configuration.orientation) {
                Configuration.ORIENTATION_PORTRAIT -> {
                    if (!Application.androidTVDevice && !Application.chromeOSDevice && !Application.wearOSDevice) {
                        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
                    }
                }
                Configuration.ORIENTATION_LANDSCAPE -> {
                    if (!Application.androidTVDevice && !Application.chromeOSDevice && !Application.wearOSDevice) {
                        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
                    }
                }
            }
        }
    }

    private fun createRequest(url: String) {
        val youtubeRegex = Regex("^.*(?:(?:youtu\\.be\\/|v\\/|vi\\/|u\\/\\w\\/|embed\\/|shorts\\/|live\\/)|(?:(?:watch)?\\?v(?:i)?=|\\&v(?:i)?=))([^#\\&\\?]*).*")
        if (youtubeRegex.containsMatchIn(url)) {
            val id = youtubeRegex.findAll(url).map { it.groupValues[1] }.joinToString()
            CoroutineScope(Dispatchers.Main).launch {
                val request = Requests()
                request.ytdlp(id)
                request.sponsorBlock(id)
                createPlayer()
            }
        }
    }

    private fun createPlayer() {
        val sessionToken = SessionToken(this, ComponentName(this, PlayerService::class.java))
        playerControllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        playerControllerFuture.addListener({
            playerController = playerControllerFuture.get()
            playerController.addListener(this)

            setContent {
                CreatePlayerUI()
            }

            if (packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) && Build.VERSION.SDK_INT >= 31) {
                setPictureInPictureParams(
                    PictureInPictureParams.Builder()
                        .setAutoEnterEnabled(true)
                        .setSeamlessResizeEnabled(true)
                        .build()
                )
            }

            playerHandler = Handler(Looper.getMainLooper())
            playerHandler.post(playerTask)

            val broadcastIntent = Intent("h.lillie.ytplayer.info")
            broadcastIntent.setPackage(this.packageName)
            sendBroadcast(broadcastIntent)
        }, MoreExecutors.directExecutor())
    }

    private var isPlaying = mutableIntStateOf(0)

    @Composable
    private fun CreatePlayerUI() {
        var showOverlay by remember { mutableStateOf(false) }
        val isPlaying by remember { isPlaying }

        // Player View

        AndroidView(
            modifier = Modifier
                .background(colorResource(R.color.black))
                .fillMaxSize()
                .navigationBarsPadding()
                .statusBarsPadding()
                .systemBarsPadding(),
            factory = { context ->
                PlayerView(context).apply {
                    player = playerController
                    useController = false
                }
            },
        )

        // 3 View

        Row(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .statusBarsPadding()
                .systemBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                if (!showOverlay) {
                                    showOverlay = true
                                } else {
                                    showOverlay = false
                                }
                            },
                            onDoubleTap = {
                                playerController.seekBack()
                            }
                        )
                    }
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                if (!showOverlay) {
                                    showOverlay = true
                                } else {
                                    showOverlay = false
                                }
                            }
                        )
                    }
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                if (!showOverlay) {
                                    showOverlay = true
                                } else {
                                    showOverlay = false
                                }
                            },
                            onDoubleTap = {
                                playerController.seekForward()
                            }
                        )
                    }
            )
        }

        // Overlay View

        if (showOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .statusBarsPadding()
                    .systemBarsPadding()
            ) {
                IconButton(
                    modifier = Modifier.align(Alignment.Center),
                    onClick = {
                        if (!playerController.isPlaying) {
                            playerController.play()
                        } else {
                            playerController.pause()
                        }
                    }
                ) {
                    Icon(
                        modifier = Modifier.size(50.dp),
                        painter = if (isPlaying == 1) {
                            painterResource(androidx.media3.session.R.drawable.media3_icon_play)
                        } else if (isPlaying == 2) {
                            painterResource(androidx.media3.session.R.drawable.media3_icon_pause)
                        } else {
                            painterResource(androidx.media3.session.R.drawable.media3_icon_skip_back)
                        },
                        tint = colorResource(R.color.white),
                        contentDescription = ""
                    )
                }
                Row(
                    modifier = Modifier
                        .height(50.dp)
                        .padding(start = 10.dp, end = 10.dp)
                        .navigationBarsPadding()
                        .statusBarsPadding()
                        .systemBarsPadding()
                ) {
                    Text(
                        modifier = Modifier.align(Alignment.CenterVertically),
                        text = Application.title,
                        color = colorResource(R.color.white),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1
                    )
                }
            }
        }
    }

    private val playerTask = object: Runnable {
        override fun run() {
            if (this@Player::playerController.isInitialized && playerController.mediaItemCount == 1) {
                if (playerController.playbackState == Player.STATE_ENDED) {
                    isPlaying.intValue = 0
                } else {
                    if (!playerController.isPlaying) {
                        isPlaying.intValue = 1
                    } else {
                        isPlaying.intValue = 2
                    }
                }
            }
            playerHandler.postDelayed(this, 1000)
        }
    }
}