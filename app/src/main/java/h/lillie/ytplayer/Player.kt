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
import android.view.WindowManager
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.DecimalFormat
import java.util.Collections
import java.util.concurrent.TimeUnit

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
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (!Application.chromeOSDevice) {
            WindowInsetsControllerCompat(window, window.decorView).systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        enableEdgeToEdge()
        setContent {
            CreatePlayerUI()
        }

        onBackPressedDispatcher.addCallback(this) {
            when (resources.configuration.orientation) {
                Configuration.ORIENTATION_PORTRAIT -> {
                    if (!Application.chromeOSDevice) {
                        deviceRotation.intValue = 0
                        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
                    }
                }
                Configuration.ORIENTATION_LANDSCAPE -> {
                    if (!Application.chromeOSDevice) {
                        deviceRotation.intValue = 1
                        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
                    }
                }
            }
        }

        when {
            intent?.action == Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    isFirstLaunch = true
                    createPlayer(intent.getStringExtra(Intent.EXTRA_TEXT)!!)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        when {
            intent.action == Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    createPlayer(intent.getStringExtra(Intent.EXTRA_TEXT)!!)
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        when (newConfig.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> {
                if (!Application.chromeOSDevice) {
                    deviceRotation.intValue = 0
                    WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
                }
            }
            Configuration.ORIENTATION_LANDSCAPE -> {
                if (!Application.chromeOSDevice) {
                    deviceRotation.intValue = 1
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

    override fun onDestroy() {
        super.onDestroy()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            if (!isFirstLaunch) {
                isFirstLaunch = true
                val clipManager: ClipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clipData: ClipData? = clipManager.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    createPlayer(clipData.getItemAt(0).text.toString())
                }
            }
            when (resources.configuration.orientation) {
                Configuration.ORIENTATION_PORTRAIT -> {
                    if (!Application.chromeOSDevice) {
                        deviceRotation.intValue = 0
                        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
                    }
                }
                Configuration.ORIENTATION_LANDSCAPE -> {
                    if (!Application.chromeOSDevice) {
                        deviceRotation.intValue = 1
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

    private var deviceRotation = mutableIntStateOf(0)
    private var isPlaying = mutableIntStateOf(0)
    private var loopChecked = mutableStateOf(false)
    private var playerDuration = mutableFloatStateOf(0f)
    private var playerPosition = mutableFloatStateOf(0f)
    private var playerTime = MutableStateFlow<String?>(null)
    private var subtitlesChecked = mutableStateListOf<Boolean>()
    private var sleepTimerChecked = mutableStateListOf(false, false, false, false, false, false)

    @Composable
    private fun CreatePlayerUI() {
        // Remembers

        var showOverlay by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }
        var showSubtitles by remember { mutableStateOf(false) }
        var showSleepTimer by remember { mutableStateOf(false) }
        val playbackSpeed = remember { MutableStateFlow("1") }
        val playerTime = remember { playerTime }
        val sliderSource = remember { MutableInteractionSource() }
        val subtitlesChecked = remember { subtitlesChecked }
        val sleepTimerChecked = remember { sleepTimerChecked }
        val deviceRotation by remember { deviceRotation }
        val isPlaying by remember { isPlaying }
        val loopChecked by remember { loopChecked }
        val playerDuration by remember { playerDuration }
        val playerPosition by remember { playerPosition }

        // States

        val player by playerController.collectAsState()
        val speed by playbackSpeed.collectAsState()
        val time by playerTime.collectAsState()
        val title by Application.title.collectAsState()

        // Player View

        if (player != null) {
            val presentationState = rememberPresentationState(player!!)

            PlayerSurface(
                modifier = if (deviceRotation == 1) {
                    Modifier
                        .background(colorResource(R.color.black))
                        .navigationBarsPadding()
                        .statusBarsPadding()
                        .systemBarsPadding()
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .resizeWithContentScale(ContentScale.Fit, presentationState.videoSizeDp)
                } else {
                    Modifier
                        .background(colorResource(R.color.black))
                        .navigationBarsPadding()
                        .statusBarsPadding()
                        .systemBarsPadding()
                        .resizeWithContentScale(ContentScale.Fit, presentationState.videoSizeDp)
                },
                player = player!!,
                surfaceType = SURFACE_TYPE_SURFACE_VIEW
            )
        }

        // 3 View

        Row(
            modifier = if (deviceRotation == 1) {
                Modifier
                    .navigationBarsPadding()
                    .statusBarsPadding()
                    .systemBarsPadding()
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .fillMaxSize()
            } else {
                Modifier
                    .navigationBarsPadding()
                    .statusBarsPadding()
                    .systemBarsPadding()
                    .fillMaxSize()
            }
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
                                showSleepTimer = false
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
                                showSleepTimer = false
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
                                showSleepTimer = false
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
                modifier = if (deviceRotation == 1) {
                    Modifier
                        .navigationBarsPadding()
                        .statusBarsPadding()
                        .systemBarsPadding()
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .fillMaxSize()
                        .background(colorResource(R.color.dimBlack))
                } else {
                    Modifier
                        .navigationBarsPadding()
                        .statusBarsPadding()
                        .systemBarsPadding()
                        .fillMaxSize()
                        .background(colorResource(R.color.dimBlack))
                }
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
                    if (isPlaying == 1) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(30.dp),
                            strokeWidth = 3.dp,
                            color = colorResource(R.color.white)
                        )
                    }
                    if (isPlaying >= 2) {
                        Icon(
                            modifier = Modifier.size(50.dp),
                            painter = when (isPlaying) {
                                2 -> {
                                    painterResource(androidx.media3.session.R.drawable.media3_icon_play)
                                }
                                3 -> {
                                    painterResource(androidx.media3.session.R.drawable.media3_icon_pause)
                                }
                                else -> {
                                    painterResource(androidx.media3.session.R.drawable.media3_icon_skip_back)
                                }
                            },
                            tint = colorResource(R.color.white),
                            contentDescription = ""
                        )
                    }
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
                if (time != null) {
                    Text(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 15.dp, end = 15.dp, bottom = 45.dp),
                        text = time!!,
                        color = colorResource(R.color.white),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1
                    )
                }
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
                            // Share Button
                            if (!Application.chromeOSDevice) {
                                IconButton(
                                    modifier = Modifier.width(50.dp),
                                    onClick = {
                                        startActivity(Intent.createChooser(Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, "https://youtu.be/${Application.id}")
                                            type = "text/plain"
                                        }, null))
                                    }
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.share),
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
                                        if (!showSettings && !showSubtitles && !showSleepTimer) {
                                            showSettings = true
                                        } else {
                                            showSettings = false
                                            showSubtitles = false
                                            showSleepTimer = false
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
                modifier = if (deviceRotation == 1) {
                    Modifier
                        .navigationBarsPadding()
                        .statusBarsPadding()
                        .systemBarsPadding()
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .padding(start = 10.dp, end = 10.dp, top = 50.dp)
                        .wrapContentHeight()
                } else {
                    Modifier
                        .navigationBarsPadding()
                        .statusBarsPadding()
                        .systemBarsPadding()
                        .padding(start = 10.dp, end = 10.dp, top = 50.dp)
                        .wrapContentHeight()
                }
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
                    // Subtitles
                    if (Application.subtitles != null) {
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
                    }
                    // Sleep Timer
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
                                    showSleepTimer = true
                                })
                    ) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterVertically)
                                .weight(1f)
                        ) {
                            Text(
                                text = "Sleep Timer (Beta)",
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
                            modifier = Modifier.align(Alignment.CenterVertically)
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
                    // Playback Speed
                    Row(
                        modifier = Modifier
                            .height(40.dp)
                            .padding(start = 10.dp, end = 10.dp)
                    ) {
                        Text(
                            modifier = Modifier.align(Alignment.CenterVertically),
                            text = "Speed: ${speed}x",
                            color = colorResource(R.color.white),
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                    }
                    Row(
                        modifier = Modifier
                            .height(30.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .align(Alignment.CenterVertically)
                        ) {
                            IconButton(
                                modifier = Modifier
                                    .scale(0.6f)
                                    .align(Alignment.CenterHorizontally),
                                onClick = {
                                    val decimalFormat = DecimalFormat("#.#")
                                    if (decimalFormat.format(playerController.value!!.playbackParameters.speed).toFloat() > 0.1f) {
                                        playerController.value!!.playbackParameters = PlaybackParameters(decimalFormat.format(playerController.value!!.playbackParameters.speed).toFloat() - 0.1f)
                                        playbackSpeed.value = decimalFormat.format(playerController.value!!.playbackParameters.speed)
                                    }
                                }
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.remove),
                                    tint = colorResource(R.color.white),
                                    contentDescription = ""
                                )
                            }
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .align(Alignment.CenterVertically)
                        ) {
                            IconButton(
                                modifier = Modifier
                                    .scale(0.6f)
                                    .align(Alignment.CenterHorizontally),
                                onClick = {
                                    val decimalFormat = DecimalFormat("#.#")
                                    if (decimalFormat.format(playerController.value!!.playbackParameters.speed).toFloat() < 2.0f) {
                                        playerController.value!!.playbackParameters = PlaybackParameters(decimalFormat.format(playerController.value!!.playbackParameters.speed).toFloat() + 0.1f)
                                        playbackSpeed.value = decimalFormat.format(playerController.value!!.playbackParameters.speed)
                                    }
                                }
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.add),
                                    tint = colorResource(R.color.white),
                                    contentDescription = ""
                                )
                            }
                        }
                    }
                }
            }
        }

        // Subtitles View

        if (showSubtitles) {
            Row(
                modifier = if (deviceRotation == 1) {
                    Modifier
                        .navigationBarsPadding()
                        .statusBarsPadding()
                        .systemBarsPadding()
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .padding(start = 10.dp, end = 10.dp, top = 50.dp)
                        .wrapContentHeight()
                } else {
                    Modifier
                        .navigationBarsPadding()
                        .statusBarsPadding()
                        .systemBarsPadding()
                        .padding(start = 10.dp, end = 10.dp, top = 50.dp)
                        .wrapContentHeight()
                }
            ) {
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                }
                LazyColumn(
                    modifier = Modifier
                        .wrapContentHeight()
                        .heightIn(0.dp, 150.dp)
                        .width(150.dp)
                        .background(colorResource(R.color.darkGrey))
                        .clickable(
                            enabled = true,
                            interactionSource = null,
                            indication = null,
                            onClick = {})
                ) {
                    val subtitles: JSONArray? = Application.subtitles
                    if (subtitles != null) {
                        item {
                            Row(
                                modifier = Modifier
                                    .height(30.dp)
                                    .padding(start = 10.dp)
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
                                    Checkbox(
                                        checked = subtitlesChecked[0],
                                        onCheckedChange = { checked ->
                                            Collections.replaceAll(subtitlesChecked, true, false)
                                            subtitlesChecked[0] = true
                                            if (checked) {
                                                playerController.value?.trackSelectionParameters = playerController.value?.trackSelectionParameters!!.buildUpon()
                                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                                    .build()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        items(subtitles.length()) { index ->
                            Row(
                                modifier = Modifier
                                    .height(30.dp)
                                    .padding(start = 10.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.CenterVertically)
                                        .weight(1f)
                                ) {
                                    Text(
                                        text = optString(subtitles.getJSONObject(index), "name")!!,
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
                                    Checkbox(
                                        checked = subtitlesChecked[index + 1],
                                        onCheckedChange = { checked ->
                                            Collections.replaceAll(subtitlesChecked, true, false)
                                            subtitlesChecked[index + 1] = true
                                            if (checked) {
                                                playerController.value?.trackSelectionParameters = playerController.value?.trackSelectionParameters!!.buildUpon()
                                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                    .setPreferredTextLanguage(optString(subtitles.getJSONObject(index), "id"))
                                                    .build()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sleep Timer View

        if (showSleepTimer) {
            Row(
                modifier = if (deviceRotation == 1) {
                    Modifier
                        .navigationBarsPadding()
                        .statusBarsPadding()
                        .systemBarsPadding()
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .padding(start = 10.dp, end = 10.dp, top = 50.dp)
                        .wrapContentHeight()
                } else {
                    Modifier
                        .navigationBarsPadding()
                        .statusBarsPadding()
                        .systemBarsPadding()
                        .padding(start = 10.dp, end = 10.dp, top = 50.dp)
                        .wrapContentHeight()
                }
            ) {
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                }
                LazyColumn(
                    modifier = Modifier
                        .wrapContentHeight()
                        .heightIn(0.dp, 150.dp)
                        .width(150.dp)
                        .background(colorResource(R.color.darkGrey))
                        .clickable(
                            enabled = true,
                            interactionSource = null,
                            indication = null,
                            onClick = {})
                ) {
                    // Off
                    item {
                        Row(
                            modifier = Modifier
                                .height(30.dp)
                                .padding(start = 10.dp)
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
                                Checkbox(
                                    checked = sleepTimerChecked[0],
                                    onCheckedChange = { checked ->
                                        Collections.replaceAll(sleepTimerChecked, true, false)
                                        sleepTimerChecked[0] = true
                                        if (checked) {
                                            val broadcastIntent = Intent("h.lillie.ytplayer.timer")
                                            broadcastIntent.setPackage(this@Player.packageName)
                                            broadcastIntent.putExtra("enable", false)
                                            sendBroadcast(broadcastIntent)
                                        }
                                    }
                                )
                            }
                        }
                    }
                    // 15 Minutes
                    item {
                        Row(
                            modifier = Modifier
                                .height(30.dp)
                                .padding(start = 10.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.CenterVertically)
                                    .weight(1f)
                            ) {
                                Text(
                                    text = "15 Minutes",
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
                                Checkbox(
                                    checked = sleepTimerChecked[1],
                                    onCheckedChange = { checked ->
                                        Collections.replaceAll(sleepTimerChecked, true, false)
                                        sleepTimerChecked[1] = true
                                        if (checked) {
                                            val broadcastIntent = Intent("h.lillie.ytplayer.timer")
                                            broadcastIntent.setPackage(this@Player.packageName)
                                            broadcastIntent.putExtra("enable", true)
                                            broadcastIntent.putExtra("time", TimeUnit.MINUTES.toMillis(15))
                                            sendBroadcast(broadcastIntent)
                                        }
                                    }
                                )
                            }
                        }
                    }
                    // 30 Minutes
                    item {
                        Row(
                            modifier = Modifier
                                .height(30.dp)
                                .padding(start = 10.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.CenterVertically)
                                    .weight(1f)
                            ) {
                                Text(
                                    text = "30 Minutes",
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
                                Checkbox(
                                    checked = sleepTimerChecked[2],
                                    onCheckedChange = { checked ->
                                        Collections.replaceAll(sleepTimerChecked, true, false)
                                        sleepTimerChecked[2] = true
                                        if (checked) {
                                            val broadcastIntent = Intent("h.lillie.ytplayer.timer")
                                            broadcastIntent.setPackage(this@Player.packageName)
                                            broadcastIntent.putExtra("enable", true)
                                            broadcastIntent.putExtra("time", TimeUnit.MINUTES.toMillis(30))
                                            sendBroadcast(broadcastIntent)
                                        }
                                    }
                                )
                            }
                        }
                    }
                    // 45 Minutes
                    item {
                        Row(
                            modifier = Modifier
                                .height(30.dp)
                                .padding(start = 10.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.CenterVertically)
                                    .weight(1f)
                            ) {
                                Text(
                                    text = "45 Minutes",
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
                                Checkbox(
                                    checked = sleepTimerChecked[3],
                                    onCheckedChange = { checked ->
                                        Collections.replaceAll(sleepTimerChecked, true, false)
                                        sleepTimerChecked[3] = true
                                        if (checked) {
                                            val broadcastIntent = Intent("h.lillie.ytplayer.timer")
                                            broadcastIntent.setPackage(this@Player.packageName)
                                            broadcastIntent.putExtra("enable", true)
                                            broadcastIntent.putExtra("time", TimeUnit.MINUTES.toMillis(45))
                                            sendBroadcast(broadcastIntent)
                                        }
                                    }
                                )
                            }
                        }
                    }
                    // 1 Hour
                    item {
                        Row(
                            modifier = Modifier
                                .height(30.dp)
                                .padding(start = 10.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.CenterVertically)
                                    .weight(1f)
                            ) {
                                Text(
                                    text = "1 Hour",
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
                                Checkbox(
                                    checked = sleepTimerChecked[4],
                                    onCheckedChange = { checked ->
                                        Collections.replaceAll(sleepTimerChecked, true, false)
                                        sleepTimerChecked[4] = true
                                        if (checked) {
                                            val broadcastIntent = Intent("h.lillie.ytplayer.timer")
                                            broadcastIntent.setPackage(this@Player.packageName)
                                            broadcastIntent.putExtra("enable", true)
                                            broadcastIntent.putExtra("time", TimeUnit.MINUTES.toMillis(60))
                                            sendBroadcast(broadcastIntent)
                                        }
                                    }
                                )
                            }
                        }
                    }
                    // End Of Video
                    item {
                        Row(
                            modifier = Modifier
                                .height(30.dp)
                                .padding(start = 10.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.CenterVertically)
                                    .weight(1f)
                            ) {
                                Text(
                                    text = "End Of Video",
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
                                Checkbox(
                                    checked = sleepTimerChecked[5],
                                    onCheckedChange = { checked ->
                                        Collections.replaceAll(sleepTimerChecked, true, false)
                                        sleepTimerChecked[5] = true
                                        if (checked) {
                                            val broadcastIntent = Intent("h.lillie.ytplayer.timer")
                                            broadcastIntent.setPackage(this@Player.packageName)
                                            broadcastIntent.putExtra("enable", true)
                                            broadcastIntent.putExtra("time", 0L)
                                            sendBroadcast(broadcastIntent)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun createPlayer(url: String) {
        val youtubeRegex = Regex("^.*(?:(?:youtu\\.be/|v/|vi/|u/\\w/|embed/|shorts/|live/)|(?:(?:watch)?\\?vi?=|&vi?=))([^#&?]*).*")
        if (!youtubeRegex.containsMatchIn(url)) {
            return
        }
        val sessionToken = SessionToken(this, ComponentName(this, PlayerService::class.java))
        playerControllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        playerControllerFuture.addListener({
            playerController.value = playerControllerFuture.get()
            playerController.value!!.addListener(this)

            if (subtitlesChecked.isNotEmpty()) {
                subtitlesChecked.clear()
            }
            val subtitles: JSONArray? = Application.subtitles
            if (subtitles != null) {
                subtitlesChecked.add(true)
                for (i in 0 until subtitles.length()) {
                    subtitlesChecked.add(false)
                }
            }

            Collections.replaceAll(sleepTimerChecked, true, false)
            sleepTimerChecked[0] = true

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
            broadcastIntent.putExtra("videoID", youtubeRegex.findAll(url).map { it.groupValues[1] }.joinToString())
            sendBroadcast(broadcastIntent)
        }, MoreExecutors.directExecutor())
    }

    private fun optString(info: JSONObject, key: String): String? {
        if (info.isNull(key)) {
            return null
        }

        return info.optString(key)
    }

    private fun optTime(time: Long): String {
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


    private val playerTask = object: Runnable {
        override fun run() {
            val player: MediaController? = playerController.value
            if (player != null && player.mediaItemCount == 1) {
                when (player.playbackState) {
                    Player.STATE_BUFFERING -> {
                        isPlaying.intValue = 1
                    }
                    Player.STATE_ENDED -> {
                        isPlaying.intValue = 4
                    }
                    else -> {
                        if (!player.isPlaying) {
                            isPlaying.intValue = 2
                        } else {
                            isPlaying.intValue = 3
                        }
                    }
                }

                val duration = player.duration
                val position = player.currentPosition
                if (duration >= 0 && position >= 0) {
                    playerDuration.floatValue = duration.toFloat()

                    if (position <= duration) {
                        playerPosition.floatValue = position.toFloat()
                    }
                    if (position > duration) {
                        playerPosition.floatValue = duration.toFloat()
                    }

                    playerTime.value = "${optTime(position)} / ${optTime(duration)}"
                }
            }
            playerHandler.postDelayed(this, 1000)
        }
    }
}