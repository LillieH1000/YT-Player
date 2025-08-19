package h.lillie.ytplayer

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.composables.core.ScrollArea
import com.composables.core.Thumb
import com.composables.core.VerticalScrollbar
import com.composables.core.rememberScrollAreaState
import com.composeunstyled.Button
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class Player: ComponentActivity(), Player.Listener {
    private lateinit var playerControllerFuture: ListenableFuture<MediaController>
    private var playerController = MutableStateFlow<MediaController?>(null)
    private var playerHandler: Handler = Handler(Looper.getMainLooper())
    private var playerSubtitles: JSONArray? = null
    private var chromeOSDevice: Boolean = false
    private var questOSDevice: Boolean = false
    private var isFirstLaunch: Boolean = false

    @SuppressLint("SwitchIntDef", "UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (packageManager.hasSystemFeature("org.chromium.arc.device_management")) {
            chromeOSDevice = true
        }
        if (Build.MODEL == "Quest") {
            questOSDevice = true
        }
        if (!chromeOSDevice && !questOSDevice) {
            WindowInsetsControllerCompat(window, window.decorView).systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        enableEdgeToEdge()
        setContent {
            CreatePlayerUI()
        }

        onBackPressedDispatcher.addCallback(this) {
            when (resources.configuration.orientation) {
                Configuration.ORIENTATION_PORTRAIT -> {
                    if (!chromeOSDevice && !questOSDevice) {
                        deviceRotation.value = 0
                        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
                    }
                }
                Configuration.ORIENTATION_LANDSCAPE -> {
                    if (!chromeOSDevice && !questOSDevice) {
                        deviceRotation.value = 1
                        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
                    }
                }
            }
        }

        val intentFilter = IntentFilter()
        intentFilter.addAction("h.lillie.ytplayer.activity.subtitles")
        if (Build.VERSION.SDK_INT <= 32) {
            registerReceiver(playerBroadcastReceiver, intentFilter)
        } else {
            registerReceiver(playerBroadcastReceiver, intentFilter, RECEIVER_NOT_EXPORTED)
        }

        when {
            intent?.action == Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    isFirstLaunch = true
                    val youtubeRegex = Regex("^.*(?:(?:youtu\\.be/|v/|vi/|u/\\w/|embed/|shorts/|live/)|(?:(?:watch)?\\?vi?=|&vi?=))([^#&?]*).*")
                    val info = intent.getStringExtra(Intent.EXTRA_TEXT)!!
                    if (youtubeRegex.containsMatchIn(info)) {
                        createPlayer(youtubeRegex.findAll(info).map { it.groupValues[1] }.joinToString())
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        when {
            intent.action == Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    val youtubeRegex = Regex("^.*(?:(?:youtu\\.be/|v/|vi/|u/\\w/|embed/|shorts/|live/)|(?:(?:watch)?\\?vi?=|&vi?=))([^#&?]*).*")
                    val info = intent.getStringExtra(Intent.EXTRA_TEXT)!!
                    if (youtubeRegex.containsMatchIn(info)) {
                        createPlayer(youtubeRegex.findAll(info).map { it.groupValues[1] }.joinToString())
                    }
                }
            }
        }
    }

    @SuppressLint("SwitchIntDef")
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        when (newConfig.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> {
                if (!chromeOSDevice && !questOSDevice) {
                    deviceRotation.value = 0
                    WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
                }
            }
            Configuration.ORIENTATION_LANDSCAPE -> {
                if (!chromeOSDevice && !questOSDevice) {
                    deviceRotation.value = 1
                    WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        playerHandler.post(playerTask)
    }

    override fun onStop() {
        super.onStop()
        playerHandler.removeCallbacksAndMessages(null)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) && Build.VERSION.SDK_INT <= 30) {
            enterPictureInPictureMode(PictureInPictureParams.Builder().build())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (this::playerControllerFuture.isInitialized) {
            MediaController.releaseFuture(playerControllerFuture)
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        unregisterReceiver(playerBroadcastReceiver)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            showOverlay.value = false
            showInfo.value = false
            showSettings.value = false
            showSubtitles.value = false
            showSleepTimer.value = false
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_SPACE -> {
                if (playerController.value != null) {
                    if (!playerController.value!!.isPlaying) {
                        playerController.value?.play()
                    } else {
                        playerController.value?.pause()
                    }
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                playerController.value?.seekBack()
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                playerController.value?.seekForward()
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    @SuppressLint("SwitchIntDef")
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            if (!isFirstLaunch) {
                isFirstLaunch = true
                if (chromeOSDevice || questOSDevice) {
                    val clipManager: ClipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    val clipData: ClipData? = clipManager.primaryClip
                    if (clipData != null && clipData.itemCount > 0) {
                        val youtubeRegex = Regex("^.*(?:(?:youtu\\.be/|v/|vi/|u/\\w/|embed/|shorts/|live/)|(?:(?:watch)?\\?vi?=|&vi?=))([^#&?]*).*")
                        val info = clipData.getItemAt(0).text.toString()
                        if (youtubeRegex.containsMatchIn(info)) {
                            createPlayer(youtubeRegex.findAll(info).map { it.groupValues[1] }.joinToString())
                        }
                    }
                }
            }
            when (resources.configuration.orientation) {
                Configuration.ORIENTATION_PORTRAIT -> {
                    if (!chromeOSDevice && !questOSDevice) {
                        deviceRotation.value = 0
                        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
                    }
                }
                Configuration.ORIENTATION_LANDSCAPE -> {
                    if (!chromeOSDevice && !questOSDevice) {
                        deviceRotation.value = 1
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

    private var autoRotateEnabled = MutableStateFlow(true)
    private var deviceRotation = MutableStateFlow(0)
    private var isPlaying = MutableStateFlow(0)
    private var loopChecked = MutableStateFlow(false)
    private var playerDuration = MutableStateFlow(0f)
    private var playerPosition = MutableStateFlow(0f)
    private var playbackSpeed = MutableStateFlow("1")
    private var playerTime = MutableStateFlow<String?>(null)
    private var showInfo = MutableStateFlow(false)
    private var showOverlay = MutableStateFlow(false)
    private var showSettings = MutableStateFlow(false)
    private var showSubtitles = MutableStateFlow(false)
    private var showSleepTimer = MutableStateFlow(false)
    private var subtitlesChecked = MutableStateFlow<List<Boolean>>(listOf())
    private var sleepTimerChecked = MutableStateFlow(listOf(false, false, false, false, false))

    @Composable
    @OptIn(ExperimentalMaterial3Api::class)
    private fun CreatePlayerUI() {
        // States

        val autoRotateEnabledState by autoRotateEnabled.collectAsState()
        val deviceRotationState by deviceRotation.collectAsState()
        val isPlayingState by isPlaying.collectAsState()
        val loopCheckedState by loopChecked.collectAsState()
        val playerControllerState by playerController.collectAsState()
        val playerDurationState by playerDuration.collectAsState()
        val playerPositionState by playerPosition.collectAsState()
        val playbackSpeedState by playbackSpeed.collectAsState()
        val playerTimeState by playerTime.collectAsState()
        val showInfoState by showInfo.collectAsState()
        val showOverlayState by showOverlay.collectAsState()
        val showSettingsState by showSettings.collectAsState()
        val showSubtitlesState by showSubtitles.collectAsState()
        val showSleepTimerState by showSleepTimer.collectAsState()
        val subtitlesCheckedState by subtitlesChecked.collectAsState()
        val sleepTimerCheckedState by sleepTimerChecked.collectAsState()

        // Player View

        AndroidView(
            modifier = Modifier
                .background(Color.Black)
                .navigationBarsPadding()
                .statusBarsPadding()
                .systemBarsPadding()
                .fillMaxSize()
                .focusTarget()
                .focusProperties { canFocus = false },
            factory = { context ->
                PlayerView(context).apply {
                    this.player = playerControllerState
                    this.useController = false
                }
            },
            update = { playerView ->
                playerView.player = playerControllerState
            }
        )

        // 3 View

        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .statusBarsPadding()
                .systemBarsPadding()
                .fillMaxSize()
                .focusTarget()
                .focusProperties { canFocus = false }
        ) {
            var leftClick: Long = 0
            val leftJob = remember { MutableStateFlow<Job?>(null) }
            val leftScope = rememberCoroutineScope()
            Button(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f),
                interactionSource = remember { MutableInteractionSource() },
                indication = IndicationFactory,
                onClick = {
                    val time = System.currentTimeMillis()
                    leftJob.value?.cancel()
                    if (time - leftClick < 300L) {
                        playerController.value?.seekBack()
                        leftClick = 0
                        leftJob.value = null
                    } else {
                        leftJob.value = leftScope.launch {
                            leftClick = time
                            delay(300)
                            if (playerControllerState?.mediaItemCount == 1) {
                                if (!showOverlayState) {
                                    showOverlay.value = true
                                } else {
                                    showOverlay.value = false
                                }
                                showInfo.value = false
                                showSettings.value = false
                                showSubtitles.value = false
                                showSleepTimer.value = false
                            }
                            leftClick = 0
                            leftJob.value = null
                        }
                    }
                }
            ) {}
            var middleClick: Long = 0
            val middleJob = remember { MutableStateFlow<Job?>(null) }
            val middleScope = rememberCoroutineScope()
            Button(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f),
                interactionSource = remember { MutableInteractionSource() },
                indication = IndicationFactory,
                onClick = {
                    val time = System.currentTimeMillis()
                    middleJob.value?.cancel()
                    if (time - middleClick < 300L) {
                        middleClick = 0
                        middleJob.value = null
                    } else {
                        middleJob.value = middleScope.launch {
                            middleClick = time
                            delay(300)
                            if (playerControllerState?.mediaItemCount == 1) {
                                if (!showOverlayState) {
                                    showOverlay.value = true
                                } else {
                                    showOverlay.value = false
                                }
                                showInfo.value = false
                                showSettings.value = false
                                showSubtitles.value = false
                                showSleepTimer.value = false
                            }
                            middleClick = 0
                            middleJob.value = null
                        }
                    }
                }
            ) {}
            var rightClick: Long = 0
            val rightJob = remember { MutableStateFlow<Job?>(null) }
            val rightScope = rememberCoroutineScope()
            Button(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f),
                interactionSource = remember { MutableInteractionSource() },
                indication = IndicationFactory,
                onClick = {
                    val time = System.currentTimeMillis()
                    rightJob.value?.cancel()
                    if (time - rightClick < 300L) {
                        playerController.value?.seekForward()
                        rightClick = 0
                        rightJob.value = null
                    } else {
                        rightJob.value = rightScope.launch {
                            rightClick = time
                            delay(300)
                            if (playerControllerState?.mediaItemCount == 1) {
                                if (!showOverlayState) {
                                    showOverlay.value = true
                                } else {
                                    showOverlay.value = false
                                }
                                showInfo.value = false
                                showSettings.value = false
                                showSubtitles.value = false
                                showSleepTimer.value = false
                            }
                            rightClick = 0
                            rightJob.value = null
                        }
                    }
                }
            ) {}
        }

        // Overlay View

        if (showOverlayState) {
            Box(
                modifier = if (deviceRotationState == 1) {
                    Modifier
                        .background(
                            brush = SolidColor(Color.Black),
                            alpha = 0.4F
                        )
                        .navigationBarsPadding()
                        .statusBarsPadding()
                        .systemBarsPadding()
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .fillMaxSize()
                        .focusTarget()
                        .focusProperties { canFocus = false }
                } else {
                    Modifier
                        .background(
                            brush = SolidColor(Color.Black),
                            alpha = 0.4F
                        )
                        .navigationBarsPadding()
                        .statusBarsPadding()
                        .systemBarsPadding()
                        .fillMaxSize()
                        .focusTarget()
                        .focusProperties { canFocus = false }
                }
            ) {
                // Play/Pause/Restart Button
                Button(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(50.dp),
                    shape = CircleShape,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = IndicationFactory,
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
                    if (isPlayingState == 1) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(30.dp),
                            strokeWidth = 3.dp,
                            color = Color.White
                        )
                    }
                    if (isPlayingState >= 2) {
                        Icon(
                            modifier = Modifier.size(40.dp),
                            imageVector = when (isPlayingState) {
                                2 -> {
                                    Icons.Default.PlayArrow
                                }
                                3 -> {
                                    Icons.Default.Pause
                                }
                                else -> {
                                    Icons.Default.Replay
                                }
                            },
                            tint = Color.White,
                            contentDescription = ""
                        )
                    }
                }
                // Bottom Row
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(start = 10.dp, end = 10.dp, bottom = 50.dp)
                        .height(50.dp)
                        .fillMaxWidth()
                ) {
                    // Progress Slider
                    val sliderSource = remember { MutableInteractionSource() }
                    Slider(
                        modifier = Modifier
                            .weight(1f)
                            .align(Alignment.CenterVertically),
                        interactionSource = sliderSource,
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
                                    activeTickColor = Color.Transparent,
                                    inactiveTickColor = Color.Transparent,
                                    activeTrackColor = Color.LightGray,
                                    inactiveTrackColor = Color.DarkGray
                                ),
                                modifier = Modifier.height(5.dp),
                                sliderState = sliderState,
                                thumbTrackGapSize = 0.dp
                            )
                        },
                        value = playerPositionState,
                        valueRange = 0f..playerDurationState,
                        onValueChange = { newValue ->
                            playerController.value?.seekTo(newValue.toLong())
                        }
                    )
                    // Fullscreen Button
                    if (!autoRotateEnabledState && !chromeOSDevice && !questOSDevice) {
                        Button(
                            modifier = Modifier
                                .width(50.dp)
                                .align(Alignment.CenterVertically),
                            shape = CircleShape,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = IndicationFactory,
                            onClick = {
                                if (requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
                                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                } else {
                                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (deviceRotationState == 1) {
                                    Icons.Default.FullscreenExit
                                } else {
                                    Icons.Default.Fullscreen
                                },
                                tint = Color.White,
                                contentDescription = ""
                            )
                        }
                    }
                }
                // Player Time
                if (playerTimeState != null) {
                    Text(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 15.dp, end = 15.dp, bottom = 45.dp),
                        text = playerTimeState!!,
                        color = Color.White,
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
                        Text(
                            text = playerControllerState?.mediaMetadata?.title.toString(),
                            color = Color.White,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
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
                            Button(
                                modifier = Modifier.width(50.dp),
                                shape = CircleShape,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = IndicationFactory,
                                onClick = {
                                    val url: String
                                    val type: String? = playerControllerState?.mediaMetadata?.extras?.getString("type")
                                    if (type == "short") {
                                        url = "https://youtube.com/shorts/${playerControllerState?.mediaMetadata?.extras?.getString("id")}"
                                    } else {
                                        url = "https://youtube.com/watch?v=${playerControllerState?.mediaMetadata?.extras?.getString("id")}"
                                    }
                                    if (chromeOSDevice || questOSDevice) {
                                        val clipManager: ClipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                                        val clipData: ClipData = ClipData.newPlainText("", url)
                                        clipManager.setPrimaryClip(clipData)
                                    } else {
                                        val shareIntent = Intent()
                                        shareIntent.action = Intent.ACTION_SEND
                                        shareIntent.putExtra(Intent.EXTRA_TEXT, url)
                                        shareIntent.type = "text/plain"
                                        startActivity(Intent.createChooser(shareIntent, null))
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Share,
                                    tint = Color.White,
                                    contentDescription = ""
                                )
                            }
                            // Settings Button
                            Button(
                                modifier = Modifier.width(50.dp),
                                shape = CircleShape,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = IndicationFactory,
                                onClick = {
                                    if (!showSettingsState && !showInfoState && !showSubtitlesState && !showSleepTimerState) {
                                        showSettings.value = true
                                    } else {
                                        showSettings.value = false
                                        showInfo.value = false
                                        showSubtitles.value = false
                                        showSleepTimer.value = false
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Settings,
                                    tint = Color.White,
                                    contentDescription = ""
                                )
                            }
                        }
                    }
                }
            }
        }

        // Settings View

        if (showSettingsState) {
            Row(
                modifier = if (deviceRotationState == 1) {
                    Modifier
                        .navigationBarsPadding()
                        .statusBarsPadding()
                        .systemBarsPadding()
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .padding(start = 10.dp, end = 10.dp, top = 50.dp)
                        .wrapContentHeight()
                        .focusTarget()
                        .focusProperties { canFocus = false }
                } else {
                    Modifier
                        .navigationBarsPadding()
                        .statusBarsPadding()
                        .systemBarsPadding()
                        .padding(start = 10.dp, end = 10.dp, top = 50.dp)
                        .wrapContentHeight()
                        .focusTarget()
                        .focusProperties { canFocus = false }
                }
            ) {
                Box(
                    modifier = Modifier.weight(1f)
                )
                Column(
                    modifier = Modifier
                        .wrapContentHeight()
                        .width(150.dp)
                        .background(Color.DarkGray)
                        .clickable(
                            enabled = true,
                            interactionSource = null,
                            indication = null,
                            onClick = {})
                ) {
                    // Info
                    if (playerControllerState?.mediaMetadata?.extras?.getBoolean("live") != true) {
                        Row(
                            modifier = Modifier
                                .height(40.dp)
                                .padding(start = 10.dp)
                                .clickable(
                                    enabled = true,
                                    interactionSource = null,
                                    indication = null,
                                    onClick = {
                                        showSettings.value = false
                                        showInfo.value = true
                                    })
                        ) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.CenterVertically)
                                    .weight(1f)
                            ) {
                                Text(
                                    text = "Info",
                                    color = Color.White,
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
                                    imageVector = Icons.AutoMirrored.Default.ArrowForwardIos,
                                    tint = Color.White,
                                    contentDescription = ""
                                )
                            }
                        }
                    }
                    // Subtitles
                    if (playerSubtitles != null && playerControllerState?.mediaMetadata?.extras?.getBoolean("live") != true) {
                        Row(
                            modifier = Modifier
                                .height(40.dp)
                                .padding(start = 10.dp)
                                .clickable(
                                    enabled = true,
                                    interactionSource = null,
                                    indication = null,
                                    onClick = {
                                        showSettings.value = false
                                        showSubtitles.value = true
                                    })
                        ) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.CenterVertically)
                                    .weight(1f)
                            ) {
                                Text(
                                    text = "Subtitles",
                                    color = Color.White,
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
                                    imageVector = Icons.AutoMirrored.Default.ArrowForwardIos,
                                    tint = Color.White,
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
                                    showSettings.value = false
                                    showSleepTimer.value = true
                                })
                    ) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterVertically)
                                .weight(1f)
                        ) {
                            Text(
                                text = "Sleep Timer",
                                color = Color.White,
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
                                imageVector = Icons.AutoMirrored.Default.ArrowForwardIos,
                                tint = Color.White,
                                contentDescription = ""
                            )
                        }
                    }
                    // Loop Video
                    if (playerControllerState?.mediaMetadata?.extras?.getBoolean("live") != true) {
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
                                    color = Color.White,
                                    overflow = TextOverflow.Ellipsis,
                                    maxLines = 1
                                )
                            }
                            Column(
                                modifier = Modifier.align(Alignment.CenterVertically)
                            ) {
                                Switch(
                                    modifier = Modifier.scale(0.8f),
                                    checked = loopCheckedState,
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
                    // Playback Speed
                    if (playerControllerState?.mediaMetadata?.extras?.getBoolean("live") != true) {
                        Row(
                            modifier = Modifier
                                .height(40.dp)
                                .padding(start = 10.dp, end = 10.dp)
                        ) {
                            Text(
                                modifier = Modifier.align(Alignment.CenterVertically),
                                text = "Speed: ${playbackSpeedState}x",
                                color = Color.White,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1
                            )
                        }
                        Row(
                            modifier = Modifier.height(30.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .align(Alignment.CenterVertically)
                            ) {
                                Button(
                                    modifier = Modifier
                                        .scale(0.6f)
                                        .align(Alignment.CenterHorizontally),
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = IndicationFactory,
                                    onClick = {
                                        val decimalFormat = DecimalFormat("#.#")
                                        if (decimalFormat.format(playerController.value!!.playbackParameters.speed).toFloat() > 0.1f) {
                                            playerController.value!!.playbackParameters = PlaybackParameters(decimalFormat.format(playerController.value!!.playbackParameters.speed).toFloat() - 0.1f)
                                            playbackSpeed.value = decimalFormat.format(playerController.value!!.playbackParameters.speed)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Remove,
                                        tint = Color.White,
                                        contentDescription = ""
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .align(Alignment.CenterVertically)
                            ) {
                                Button(
                                    modifier = Modifier
                                        .scale(0.6f)
                                        .align(Alignment.CenterHorizontally),
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = IndicationFactory,
                                    onClick = {
                                        val decimalFormat = DecimalFormat("#.#")
                                        if (decimalFormat.format(playerController.value!!.playbackParameters.speed).toFloat() < 2.0f) {
                                            playerController.value!!.playbackParameters = PlaybackParameters(decimalFormat.format(playerController.value!!.playbackParameters.speed).toFloat() + 0.1f)
                                            playbackSpeed.value = decimalFormat.format(playerController.value!!.playbackParameters.speed)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        tint = Color.White,
                                        contentDescription = ""
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Info View

        if (showInfoState) {
            Row(
                modifier = if (deviceRotationState == 1) {
                    Modifier
                        .navigationBarsPadding()
                        .statusBarsPadding()
                        .systemBarsPadding()
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .padding(start = 10.dp, end = 10.dp, top = 50.dp)
                        .wrapContentHeight()
                        .focusTarget()
                        .focusProperties { canFocus = false }
                } else {
                    Modifier
                        .navigationBarsPadding()
                        .statusBarsPadding()
                        .systemBarsPadding()
                        .padding(start = 10.dp, end = 10.dp, top = 50.dp)
                        .wrapContentHeight()
                        .focusTarget()
                        .focusProperties { canFocus = false }
                }
            ) {
                Box(
                    modifier = Modifier.weight(1f)
                )
                Column(
                    modifier = Modifier
                        .wrapContentHeight()
                        .heightIn(0.dp, 150.dp)
                        .width(150.dp)
                        .background(Color.DarkGray)
                        .clickable(
                            enabled = true,
                            interactionSource = null,
                            indication = null,
                            onClick = {})
                ) {
                    // Views
                    Row(
                        modifier = Modifier
                            .height(30.dp)
                            .padding(start = 10.dp)
                    ) {
                        Text(
                            modifier = Modifier.align(Alignment.CenterVertically),
                            text = "Views: ${NumberFormat.getInstance().format(playerControllerState?.mediaMetadata?.extras?.getInt("views"))}",
                            color = Color.White,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                    }
                    // Likes
                    Row(
                        modifier = Modifier
                            .height(30.dp)
                            .padding(start = 10.dp)
                    ) {
                        Text(
                            modifier = Modifier.align(Alignment.CenterVertically),
                            text = "Likes: ${NumberFormat.getInstance().format(playerControllerState?.mediaMetadata?.extras?.getInt("likes"))}",
                            color = Color.White,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                    }
                    // Dislikes
                    if (playerControllerState?.mediaMetadata?.extras?.getInt("dislikes") != null) {
                        Row(
                            modifier = Modifier
                                .height(30.dp)
                                .padding(start = 10.dp)
                        ) {
                            Text(
                                modifier = Modifier.align(Alignment.CenterVertically),
                                text = "Dislikes: ${NumberFormat.getInstance().format(playerControllerState?.mediaMetadata?.extras?.getInt("dislikes"))}",
                                color = Color.White,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Subtitles View

        if (showSubtitlesState) {
            Row(
                modifier = if (deviceRotationState == 1) {
                    Modifier
                        .navigationBarsPadding()
                        .statusBarsPadding()
                        .systemBarsPadding()
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .padding(start = 10.dp, end = 10.dp, top = 50.dp)
                        .wrapContentHeight()
                        .focusTarget()
                        .focusProperties { canFocus = false }
                } else {
                    Modifier
                        .navigationBarsPadding()
                        .statusBarsPadding()
                        .systemBarsPadding()
                        .padding(start = 10.dp, end = 10.dp, top = 50.dp)
                        .wrapContentHeight()
                        .focusTarget()
                        .focusProperties { canFocus = false }
                }
            ) {
                Box(
                    modifier = Modifier.weight(1f)
                )
                val lazyState = rememberLazyListState()
                val scrollState = rememberScrollAreaState(lazyState)
                ScrollArea(state = scrollState) {
                    LazyColumn(
                        state = lazyState,
                        modifier = Modifier
                            .wrapContentHeight()
                            .heightIn(0.dp, 150.dp)
                            .width(150.dp)
                            .background(Color.DarkGray)
                            .clickable(
                                enabled = true,
                                interactionSource = null,
                                indication = null,
                                onClick = {})
                    ) {
                        val subtitles: JSONArray? = playerSubtitles
                        if (subtitles != null) {
                            items(subtitles.length() + 1) { index ->
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
                                            text = when (index) {
                                                0 -> "Off"
                                                else -> subtitles.getJSONObject(index).optString("name")
                                            },
                                            color = Color.White,
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
                                            colors = CheckboxColors(
                                                checkedCheckmarkColor = Color.White,
                                                uncheckedCheckmarkColor = Color.Transparent,
                                                checkedBoxColor = Color.Transparent,
                                                uncheckedBoxColor = Color.Transparent,
                                                disabledCheckedBoxColor = CheckboxDefaults.colors().disabledCheckedBoxColor,
                                                disabledUncheckedBoxColor = CheckboxDefaults.colors().disabledUncheckedBoxColor,
                                                disabledIndeterminateBoxColor = CheckboxDefaults.colors().disabledIndeterminateBoxColor,
                                                checkedBorderColor = Color.White,
                                                uncheckedBorderColor = Color.White,
                                                disabledBorderColor = CheckboxDefaults.colors().disabledBorderColor,
                                                disabledUncheckedBorderColor = CheckboxDefaults.colors().disabledUncheckedBorderColor,
                                                disabledIndeterminateBorderColor = CheckboxDefaults.colors().disabledIndeterminateBorderColor
                                            ),
                                            checked = subtitlesCheckedState[index],
                                            onCheckedChange = { checked ->
                                                Collections.replaceAll(subtitlesChecked.value, true, false)
                                                subtitlesChecked.update { list ->
                                                    list.toMutableList().apply {
                                                        set(index, true)
                                                    }.toList()
                                                }
                                                if (checked) {
                                                    when (index) {
                                                        0 -> {
                                                            playerController.value?.trackSelectionParameters = playerController.value?.trackSelectionParameters!!.buildUpon()
                                                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                                                .build()
                                                        }
                                                        else -> {
                                                            playerController.value?.trackSelectionParameters = playerController.value?.trackSelectionParameters!!.buildUpon()
                                                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                                .setPreferredTextLanguage(subtitles.getJSONObject(index).optString("id"))
                                                                .build()
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    VerticalScrollbar(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .wrapContentHeight()
                            .heightIn(0.dp, 150.dp)
                            .width(4.dp)
                    ) {
                        Thumb(
                            modifier = Modifier.background(Color.LightGray)
                        )
                    }
                }
            }
        }

        // Sleep Timer View

        if (showSleepTimerState) {
            Row(
                modifier = if (deviceRotationState == 1) {
                    Modifier
                        .navigationBarsPadding()
                        .statusBarsPadding()
                        .systemBarsPadding()
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .padding(start = 10.dp, end = 10.dp, top = 50.dp)
                        .wrapContentHeight()
                        .focusTarget()
                        .focusProperties { canFocus = false }
                } else {
                    Modifier
                        .navigationBarsPadding()
                        .statusBarsPadding()
                        .systemBarsPadding()
                        .padding(start = 10.dp, end = 10.dp, top = 50.dp)
                        .wrapContentHeight()
                        .focusTarget()
                        .focusProperties { canFocus = false }
                }
            ) {
                Box(
                    modifier = Modifier.weight(1f)
                )
                val lazyState = rememberLazyListState()
                val scrollState = rememberScrollAreaState(lazyState)
                ScrollArea(state = scrollState) {
                    LazyColumn(
                        state = lazyState,
                        modifier = Modifier
                            .wrapContentHeight()
                            .heightIn(0.dp, 150.dp)
                            .width(150.dp)
                            .background(Color.DarkGray)
                            .clickable(
                                enabled = true,
                                interactionSource = null,
                                indication = null,
                                onClick = {})
                    ) {
                        items(5) { index ->
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
                                        text = when (index) {
                                            1 -> "15 Minutes"
                                            2 -> "30 Minutes"
                                            3 -> "45 Minutes"
                                            4 -> "1 Hour"
                                            else -> "Off"
                                        },
                                        color = Color.White,
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
                                        colors = CheckboxColors(
                                            checkedCheckmarkColor = Color.White,
                                            uncheckedCheckmarkColor = Color.Transparent,
                                            checkedBoxColor = Color.Transparent,
                                            uncheckedBoxColor = Color.Transparent,
                                            disabledCheckedBoxColor = CheckboxDefaults.colors().disabledCheckedBoxColor,
                                            disabledUncheckedBoxColor = CheckboxDefaults.colors().disabledUncheckedBoxColor,
                                            disabledIndeterminateBoxColor = CheckboxDefaults.colors().disabledIndeterminateBoxColor,
                                            checkedBorderColor = Color.White,
                                            uncheckedBorderColor = Color.White,
                                            disabledBorderColor = CheckboxDefaults.colors().disabledBorderColor,
                                            disabledUncheckedBorderColor = CheckboxDefaults.colors().disabledUncheckedBorderColor,
                                            disabledIndeterminateBorderColor = CheckboxDefaults.colors().disabledIndeterminateBorderColor
                                        ),
                                        checked = sleepTimerCheckedState[index],
                                        onCheckedChange = { checked ->
                                            Collections.replaceAll(sleepTimerChecked.value, true, false)
                                            sleepTimerChecked.update { list ->
                                                list.toMutableList().apply {
                                                    set(index, true)
                                                }.toList()
                                            }
                                            if (checked) {
                                                val broadcastIntent = Intent("h.lillie.ytplayer.service.timer")
                                                broadcastIntent.setPackage(this@Player.packageName)
                                                when (index) {
                                                    1 -> broadcastIntent.putExtra("time", TimeUnit.MINUTES.toMillis(15))
                                                    2 -> broadcastIntent.putExtra("time", TimeUnit.MINUTES.toMillis(30))
                                                    3 -> broadcastIntent.putExtra("time", TimeUnit.MINUTES.toMillis(45))
                                                    4 -> broadcastIntent.putExtra("time", TimeUnit.MINUTES.toMillis(60))
                                                    else -> broadcastIntent.putExtra("time", 0L)
                                                }
                                                sendBroadcast(broadcastIntent)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    VerticalScrollbar(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .wrapContentHeight()
                            .heightIn(0.dp, 150.dp)
                            .width(4.dp)
                    ) {
                        Thumb(
                            modifier = Modifier.background(Color.LightGray)
                        )
                    }
                }
            }
        }
    }

    private fun createPlayer(videoID: String?) {
        playerController.value?.stop()
        playerController.value?.removeMediaItem(0)
        showOverlay.value = false
        showInfo.value = false
        showSettings.value = false
        showSubtitles.value = false
        showSleepTimer.value = false

        val sessionToken = SessionToken(this, ComponentName(this, PlayerService::class.java))
        playerControllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        playerControllerFuture.addListener({
            playerController.value = playerControllerFuture.get()
            playerController.value!!.addListener(this)
            playbackSpeed.value = "1"

            Collections.replaceAll(sleepTimerChecked.value, true, false)
            sleepTimerChecked.update { list ->
                list.toMutableList().apply {
                    set(0, true)
                }.toList()
            }

            if (packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) && Build.VERSION.SDK_INT >= 31) {
                setPictureInPictureParams(
                    PictureInPictureParams.Builder()
                        .setAutoEnterEnabled(true)
                        .setSeamlessResizeEnabled(true)
                        .build()
                )
            }

            val broadcastIntent = Intent("h.lillie.ytplayer.service.info")
            broadcastIntent.setPackage(this.packageName)
            broadcastIntent.putExtra("videoID", videoID)
            broadcastIntent.putExtra("searchQuery", null as String?)
            sendBroadcast(broadcastIntent)
        }, MoreExecutors.directExecutor())
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

    private fun BroadcastReceiver.async(coroutineContext: CoroutineContext = EmptyCoroutineContext, block: suspend CoroutineScope.() -> Unit) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch(coroutineContext) {
            block()
        }.invokeOnCompletion {
            pendingResult.finish()
        }
    }

    private val playerBroadcastReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = async {
            if (intent?.action == "h.lillie.ytplayer.activity.subtitles") {
                if (subtitlesChecked.value.isNotEmpty()) {
                    subtitlesChecked.update { list ->
                        list.toMutableList().apply {
                            clear()
                        }.toList()
                    }
                }

                val subtitles = intent.extras!!.getString("subtitles")
                if (subtitles == null) {
                    playerSubtitles = null
                    return@async
                }

                val subtitlesArray = JSONArray(subtitles)
                playerSubtitles = subtitlesArray
                subtitlesChecked.update { list ->
                    list.toMutableList().apply {
                        add(true)
                    }.toList()
                }
                for (i in 0 until subtitlesArray.length()) {
                    subtitlesChecked.update { list ->
                        list.toMutableList().apply {
                            add(false)
                        }.toList()
                    }
                }
                return@async
            }
        }
    }

    private val playerTask = object: Runnable {
        override fun run() {
            // Rotation
            if (Settings.System.getInt(this@Player.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 0) {
                autoRotateEnabled.value = false
            } else {
                autoRotateEnabled.value = true
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            // Player
            val player: MediaController? = playerController.value
            if (player != null && player.mediaItemCount == 1) {
                when (player.playbackState) {
                    Player.STATE_BUFFERING -> {
                        isPlaying.value = 1
                    }
                    Player.STATE_ENDED -> {
                        isPlaying.value = 4
                    }
                    else -> {
                        if (!player.isPlaying) {
                            isPlaying.value = 2
                        } else {
                            isPlaying.value = 3
                        }
                    }
                }

                val duration = player.duration
                val position = player.currentPosition
                if (duration >= 0 && position >= 0) {
                    playerDuration.value = duration.toFloat()

                    if (position <= duration) {
                        playerPosition.value = position.toFloat()
                    }
                    if (position > duration) {
                        playerPosition.value = duration.toFloat()
                    }

                    playerTime.value = "${optTime(position)} / ${optTime(duration)}"
                }
            }
            playerHandler.postDelayed(this, 1000)
        }
    }
}