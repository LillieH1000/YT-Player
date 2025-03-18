package h.lillie.ytplayer

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.ClipData
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.unit.DpSize
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
import org.json.JSONArray
import org.json.JSONObject

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
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
                val clipManager: ClipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clipData: ClipData? = clipManager.primaryClip
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

    override fun onRepeatModeChanged(repeatMode: Int) {
        super.onRepeatModeChanged(repeatMode)
        if (repeatMode == Player.REPEAT_MODE_OFF) {
            loopChecked.value = false
        } else {
            loopChecked.value = true
        }
    }

    private var isPlaying = mutableIntStateOf(0)
    private var loopChecked = mutableStateOf(false)
    private var playerDuration = mutableFloatStateOf(0f)
    private var playerPosition = mutableFloatStateOf(0f)

    @Composable
    private fun CreatePlayerUI() {
        // Remembers

        var showOverlay by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }
        var showSubtitles by remember { mutableStateOf(false) }
        val sliderSource = remember { MutableInteractionSource() }
        val isPlaying by remember { isPlaying }
        val loopChecked by remember { loopChecked }
        val playerDuration by remember { playerDuration }
        val playerPosition by remember { playerPosition }

        // States

        val player by playerController.collectAsState()
        val title by Application.title.collectAsState()

        // Player View

        AndroidView(
            modifier = Modifier
                .background(colorResource(R.color.black))
                .navigationBarsPadding()
                .statusBarsPadding()
                .systemBarsPadding()
                .windowInsetsPadding(WindowInsets.displayCutout)
                .fillMaxSize(),
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
                .navigationBarsPadding()
                .statusBarsPadding()
                .systemBarsPadding()
                .windowInsetsPadding(WindowInsets.displayCutout)
                .fillMaxSize()
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
                                showSubtitles = false
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
                                showSubtitles = false
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
                                showSubtitles = false
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
                    .navigationBarsPadding()
                    .statusBarsPadding()
                    .systemBarsPadding()
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .fillMaxSize()
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
                // Progress Slider
                Slider(
                    interactionSource = sliderSource,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(start = 10.dp, end = 10.dp, bottom = 50.dp),
                    steps = 0,
                    thumb = {
                        SliderDefaults.Thumb(
                            interactionSource = sliderSource,
                            modifier = Modifier.size(0.dp),
                            thumbSize = DpSize.Zero
                        )
                    },
                    track = { sliderState ->
                        SliderDefaults.Track(
                            colors = SliderDefaults.colors(
                                activeTickColor = colorResource(R.color.clear),
                                inactiveTickColor = colorResource(R.color.clear),
                                activeTrackColor = colorResource(R.color.lightGrey),
                                inactiveTrackColor = colorResource(R.color.darkGrey)
                            ),
                            modifier = Modifier.height(5.dp),
                            sliderState = sliderState,
                            thumbTrackGapSize = 0.dp
                        )
                    },
                    value = playerPosition,
                    valueRange = 0f..playerDuration,
                    onValueChange = { newValue ->
                        playerController.value?.seekTo(newValue.toLong())
                    }
                )
                // Top Row
                Row(
                    modifier = Modifier
                        .padding(start = 10.dp, end = 10.dp)
                        .height(50.dp)
                        .fillMaxWidth()
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
                            if (!Application.live) {
                                IconButton(
                                    modifier = Modifier.width(50.dp),
                                    onClick = {
                                        if (!showSettings) {
                                            showSettings = true
                                        } else {
                                            showSettings = false
                                            showSubtitles = false
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
        }

        // Settings View

        if (showSettings) {
            Row(
                modifier = Modifier
                    .navigationBarsPadding()
                    .statusBarsPadding()
                    .systemBarsPadding()
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(start = 10.dp, end = 10.dp, top = 50.dp)
                    .wrapContentHeight()
            ) {
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                }
                Column(
                    modifier = Modifier
                        .wrapContentHeight()
                        .width(150.dp)
                        .background(colorResource(R.color.darkGrey))
                        .clickable(
                            enabled = true,
                            interactionSource = null,
                            indication = null,
                            onClick = {})
                ) {
                    // Subtitles (EN)
                    Row(
                        modifier = Modifier
                            .height(40.dp)
                            .padding(start = 10.dp)
                            .clickable(
                                enabled = true,
                                interactionSource = null,
                                indication = null,
                                onClick = {
                                    showSettings = false
                                    showSubtitles = true
                                })
                    ) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterVertically)
                                .weight(1f)
                        ) {
                            Text(
                                text = "Subtitles",
                                color = colorResource(R.color.white),
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1
                            )
                        }
                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterVertically)
                                .scale(0.6f)
                                .width(30.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.forward),
                                tint = colorResource(R.color.white),
                                contentDescription = ""
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

        // Subtitles View

        if (showSubtitles) {
            Row(
                modifier = Modifier
                    .navigationBarsPadding()
                    .statusBarsPadding()
                    .systemBarsPadding()
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(start = 10.dp, end = 10.dp, top = 50.dp)
                    .wrapContentHeight()
            ) {
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                }
                Column(
                    modifier = Modifier
                        .height(150.dp)
                        .width(150.dp)
                        .background(colorResource(R.color.darkGrey))
                        .verticalScroll(rememberScrollState())
                        .clickable(
                            enabled = true,
                            interactionSource = null,
                            indication = null,
                            onClick = {})
                ) {
                    val subtitles: JSONArray? = Application.subtitles
                    if (subtitles != null) {
                        Row(
                            modifier = Modifier
                                .height(30.dp)
                                .padding(start = 10.dp)
                                .clickable(
                                    enabled = true,
                                    interactionSource = null,
                                    indication = null,
                                    onClick = {
                                        playerController.value?.trackSelectionParameters = playerController.value?.trackSelectionParameters!!.buildUpon()
                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                            .build()
                                    })
                        ) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.CenterVertically)
                                    .weight(1f)
                            ) {
                                Text(
                                    text = "Off",
                                    color = colorResource(R.color.white),
                                    overflow = TextOverflow.Ellipsis,
                                    maxLines = 1
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .align(Alignment.CenterVertically)
                                    .scale(0.6f)
                                    .width(30.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.forward),
                                    tint = colorResource(R.color.white),
                                    contentDescription = ""
                                )
                            }
                        }
                        for (i in 0 until subtitles.length()) {
                            Row(
                                modifier = Modifier
                                    .height(30.dp)
                                    .padding(start = 10.dp)
                                    .clickable(
                                        enabled = true,
                                        interactionSource = null,
                                        indication = null,
                                        onClick = {
                                            playerController.value?.trackSelectionParameters = playerController.value?.trackSelectionParameters!!.buildUpon()
                                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                .setPreferredTextLanguage(optString(subtitles.getJSONObject(i), "id"))
                                                .build()
                                        })
                            ) {
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.CenterVertically)
                                        .weight(1f)
                                ) {
                                    Text(
                                        text = optString(subtitles.getJSONObject(i), "name")!!,
                                        color = colorResource(R.color.white),
                                        overflow = TextOverflow.Ellipsis,
                                        maxLines = 1
                                    )
                                }
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.CenterVertically)
                                        .scale(0.6f)
                                        .width(30.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.forward),
                                        tint = colorResource(R.color.white),
                                        contentDescription = ""
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun createRequest(url: String) {
        val youtubeRegex = Regex("^.*(?:(?:youtu\\.be/|v/|vi/|u/\\w/|embed/|shorts/|live/)|(?:(?:watch)?\\?vi?=|&vi?=))([^#&?]*).*")
        if (youtubeRegex.containsMatchIn(url)) {
            val id: String = youtubeRegex.findAll(url).map { it.groupValues[1] }.joinToString()
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

    private fun optString(info: JSONObject, key: String): String? {
        if (info.isNull(key)) {
            return null
        }

        return info.optString(key)
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

                val duration = playerController.value!!.duration
                if (duration != C.TIME_UNSET && playerDuration.floatValue == 0f) {
                    playerDuration.floatValue = duration.toFloat()
                }
                if (playerDuration.floatValue != 0f) {
                    playerPosition.floatValue = playerController.value!!.currentPosition.toFloat()
                }
            }
            playerHandler.postDelayed(this, 1000)
        }
    }
}