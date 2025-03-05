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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@SuppressLint("SwitchIntDef")
class Player: ComponentActivity(), Player.Listener {
    private lateinit var playerControllerFuture: ListenableFuture<MediaController>
    private lateinit var playerHandler: Handler
    private var playerController = MutableStateFlow<MediaController?>(null)
    private var isFirstLaunch: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CreatePlayerUI()
        }

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
        if (!Application.androidTVDevice && !Application.chromeOSDevice && !Application.wearOSDevice) {
            createRequest(intent.getStringExtra(Intent.EXTRA_TEXT)!!)
        }
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

    private var isPlaying = mutableIntStateOf(0)

    @Composable
    private fun CreatePlayerUI() {
        // Remembers

        var showOverlay by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }
        var subtitlesChecked by remember { mutableStateOf(false) }
        var loopChecked by remember { mutableStateOf(false) }
        val isPlaying by remember { isPlaying }

        // States

        val player by playerController.collectAsState()
        val title by Application.title.collectAsState()

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
                    this.player = player
                    this.useController = false
                }
            },
            update = { playerView ->
                playerView.player = player
            }
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
                                showSettings = false
                            },
                            onDoubleTap = {
                                playerController.value?.seekBack()
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
                                showSettings = false
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
                                showSettings = false
                            },
                            onDoubleTap = {
                                playerController.value?.seekForward()
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
                    .background(colorResource(R.color.dimBlack))
            ) {
                // Play/Pause/Restart Button
                IconButton(
                    modifier = Modifier.align(Alignment.Center),
                    onClick = {
                        if (playerController.value != null) {
                            if (!playerController.value!!.isPlaying) {
                                playerController.value?.play()
                            } else {
                                playerController.value?.pause()
                            }
                        }
                    }
                ) {
                    Icon(
                        modifier = if (isPlaying == 0) {
                            Modifier.size(50.dp).alpha(0f)
                        } else {
                            Modifier.size(50.dp).alpha(1f)
                        },
                        painter = when (isPlaying) {
                            1 -> {
                                painterResource(androidx.media3.session.R.drawable.media3_icon_skip_back)
                            }
                            2 -> {
                                painterResource(androidx.media3.session.R.drawable.media3_icon_play)
                            }
                            3 -> {
                                painterResource(androidx.media3.session.R.drawable.media3_icon_pause)
                            }
                            else -> {
                                painterResource(R.drawable.empty)
                            }
                        },
                        tint = colorResource(R.color.white),
                        contentDescription = ""
                    )
                }
                // Top Row
                Row(
                    modifier = Modifier
                        .height(50.dp)
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp)
                        .navigationBarsPadding()
                        .statusBarsPadding()
                        .systemBarsPadding()
                ) {
                    // Title View
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .align(Alignment.CenterVertically)
                    ) {
                        if (title != null) {
                            Text(
                                text = title!!,
                                color = colorResource(R.color.white),
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1
                            )
                        }
                    }
                    // Menu Buttons
                    Column(
                        modifier = Modifier
                            .wrapContentWidth()
                            .align(Alignment.CenterVertically)
                    ) {
                        Row(
                            modifier = Modifier.wrapContentWidth()
                        ) {
                            // Close Button
                            if (Application.androidTVDevice) {
                                IconButton(
                                    modifier = Modifier.width(50.dp),
                                    onClick = {
                                        finish()
                                    }
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.close),
                                        tint = colorResource(R.color.white),
                                        contentDescription = ""
                                    )
                                }
                            }
                            // Settings Button
                            IconButton(
                                modifier = Modifier.width(50.dp),
                                onClick = {
                                    if (!showSettings) {
                                        showSettings = true
                                    } else {
                                        showSettings = false
                                    }
                                }
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.settings),
                                    tint = colorResource(R.color.white),
                                    contentDescription = ""
                                )
                            }
                        }
                    }
                }
            }
        }

        // Settings View

        if (showSettings) {
            Column(
                modifier = Modifier
                    .wrapContentHeight()
                    .width(170.dp)
                    .padding(start = 10.dp, end = 10.dp, top = 50.dp)
                    .navigationBarsPadding()
                    .statusBarsPadding()
                    .systemBarsPadding()
                    .background(colorResource(R.color.darkGrey))
                    .clickable(enabled = true, interactionSource = null, indication = null, onClick = {})
            ) {
                // Subtitles (EN)
                Row(
                    modifier = Modifier
                        .height(40.dp)
                        .padding(start = 10.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .align(Alignment.CenterVertically)
                    ) {
                        Text(
                            text = "Subtitles (EN)",
                            color = colorResource(R.color.white),
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                    }
                    Column(
                    ) {
                        Switch(
                            modifier = Modifier.scale(0.8f),
                            checked = subtitlesChecked,
                            onCheckedChange = {
                                subtitlesChecked = it
                                if (!subtitlesChecked) {
                                    playerController.value?.trackSelectionParameters = playerController.value?.trackSelectionParameters!!.buildUpon()
                                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                        .build()
                                } else {
                                    playerController.value?.trackSelectionParameters = playerController.value?.trackSelectionParameters!!.buildUpon()
                                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                        .setPreferredTextLanguage("en")
                                        .build()
                                }
                            }
                        )
                    }
                }
                // Loop Video
                Row(
                    modifier = Modifier
                        .height(40.dp)
                        .padding(start = 10.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .align(Alignment.CenterVertically)
                    ) {
                        Text(
                            text = "Loop Video",
                            color = colorResource(R.color.white),
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                    }
                    Column(
                    ) {
                        Switch(
                            modifier = Modifier.scale(0.8f),
                            checked = loopChecked,
                            onCheckedChange = {
                                loopChecked = it
                                if (playerController.value?.repeatMode == Player.REPEAT_MODE_OFF) {
                                    playerController.value?.repeatMode = Player.REPEAT_MODE_ONE
                                } else {
                                    playerController.value?.repeatMode = Player.REPEAT_MODE_OFF
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun createRequest(url: String) {
        val youtubeRegex = Regex("^.*(?:(?:youtu\\.be/|v/|vi/|u/\\w/|embed/|shorts/|live/)|(?:(?:watch)?\\?vi?=|&vi?=))([^#&?]*).*")
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
            playerController.value = playerControllerFuture.get()
            playerController.value!!.addListener(this)

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

    private val playerTask = object: Runnable {
        override fun run() {
            if (playerController.value != null && playerController.value!!.mediaItemCount == 1) {
                if (playerController.value!!.playbackState == Player.STATE_ENDED) {
                    isPlaying.intValue = 1
                } else {
                    if (!playerController.value!!.isPlaying) {
                        isPlaying.intValue = 2
                    } else {
                        isPlaying.intValue = 3
                    }
                }
            }
            playerHandler.postDelayed(this, 1000)
        }
    }
}