package h.lillie.ytplayer

import android.Manifest
import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
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
import org.json.JSONObject
import java.text.DecimalFormat
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SwitchIntDef", "UnspecifiedRegisterReceiverFlag")
class Player: ComponentActivity(), Player.Listener {
    private lateinit var playerControllerFuture: ListenableFuture<MediaController>
    private lateinit var playerHandler: Handler
    private var playerController = MutableStateFlow<MediaController?>(null)
    private var playerSubtitles: JSONArray? = null
    private var chromeOSDevice: Boolean = false
    private var isFirstLaunch: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (packageManager.hasSystemFeature("org.chromium.arc.device_management")) {
            chromeOSDevice = true
        }
        if (!chromeOSDevice) {
            WindowInsetsControllerCompat(window, window.decorView).systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        enableEdgeToEdge()
        setContent {
            CreatePlayerUI()
        }

        onBackPressedDispatcher.addCallback(this) {
            when (resources.configuration.orientation) {
                Configuration.ORIENTATION_PORTRAIT -> {
                    if (!chromeOSDevice) {
                        deviceRotation.value = 0
                        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
                    }
                }
                Configuration.ORIENTATION_LANDSCAPE -> {
                    if (!chromeOSDevice) {
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
                        createPlayer(youtubeRegex.findAll(info).map { it.groupValues[1] }.joinToString(), null)
                    }
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
                    val youtubeRegex = Regex("^.*(?:(?:youtu\\.be/|v/|vi/|u/\\w/|embed/|shorts/|live/)|(?:(?:watch)?\\?vi?=|&vi?=))([^#&?]*).*")
                    val info = intent.getStringExtra(Intent.EXTRA_TEXT)!!
                    if (youtubeRegex.containsMatchIn(info)) {
                        createPlayer(youtubeRegex.findAll(info).map { it.groupValues[1] }.joinToString(), null)
                    }
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        when (newConfig.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> {
                if (!chromeOSDevice) {
                    deviceRotation.value = 0
                    WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
                }
            }
            Configuration.ORIENTATION_LANDSCAPE -> {
                if (!chromeOSDevice) {
                    deviceRotation.value = 1
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
        if (this::playerHandler.isInitialized) {
            playerHandler.removeCallbacksAndMessages(null)
        }
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            if (!isFirstLaunch) {
                isFirstLaunch = true
                if (chromeOSDevice) {
                    val clipManager: ClipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    val clipData: ClipData? = clipManager.primaryClip
                    if (clipData != null && clipData.itemCount > 0) {
                        val youtubeRegex = Regex("^.*(?:(?:youtu\\.be/|v/|vi/|u/\\w/|embed/|shorts/|live/)|(?:(?:watch)?\\?vi?=|&vi?=))([^#&?]*).*")
                        val info = clipData.getItemAt(0).text.toString()
                        if (youtubeRegex.containsMatchIn(info)) {
                            createPlayer(youtubeRegex.findAll(info).map { it.groupValues[1] }.joinToString(), null)
                        }
                    }
                }
            }
            when (resources.configuration.orientation) {
                Configuration.ORIENTATION_PORTRAIT -> {
                    if (!chromeOSDevice) {
                        deviceRotation.value = 0
                        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
                    }
                }
                Configuration.ORIENTATION_LANDSCAPE -> {
                    if (!chromeOSDevice) {
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

    private var deviceRotation = MutableStateFlow(0)
    private var isPlaying = MutableStateFlow(0)
    private var loopChecked = MutableStateFlow(false)
    private var playerDuration = MutableStateFlow(0f)
    private var playerPosition = MutableStateFlow(0f)
    private var playbackSpeed = MutableStateFlow("1")
    private var playerTime = MutableStateFlow<String?>(null)
    private var showOverlay = MutableStateFlow(true)
    private var showSettings = MutableStateFlow(false)
    private var showSubtitles = MutableStateFlow(false)
    private var showSleepTimer = MutableStateFlow(false)
    private var subtitlesChecked = MutableStateFlow<List<Boolean>>(listOf())
    private var sleepTimerChecked = MutableStateFlow(listOf(false, false, false, false, false))

    @Composable
    private fun CreatePlayerUI() {
        // States

        val deviceRotationState by deviceRotation.collectAsState()
        val isPlayingState by isPlaying.collectAsState()
        val loopCheckedState by loopChecked.collectAsState()
        val playerControllerState by playerController.collectAsState()
        val playerDurationState by playerDuration.collectAsState()
        val playerPositionState by playerPosition.collectAsState()
        val playbackSpeedState by playbackSpeed.collectAsState()
        val playerTimeState by playerTime.collectAsState()
        val showOverlayState by showOverlay.collectAsState()
        val showSettingsState by showSettings.collectAsState()
        val showSubtitlesState by showSubtitles.collectAsState()
        val showSleepTimerState by showSleepTimer.collectAsState()
        val subtitlesCheckedState by subtitlesChecked.collectAsState()
        val sleepTimerCheckedState by sleepTimerChecked.collectAsState()

        // Player View

        AndroidView(
            modifier = if (deviceRotationState == 1) {
                Modifier
                    .background(colorResource(R.color.black))
                    .navigationBarsPadding()
                    .statusBarsPadding()
                    .systemBarsPadding()
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .fillMaxSize()
                    .focusTarget()
                    .focusProperties { canFocus = false }
            } else {
                Modifier
                    .background(colorResource(R.color.black))
                    .navigationBarsPadding()
                    .statusBarsPadding()
                    .systemBarsPadding()
                    .fillMaxSize()
                    .focusTarget()
                    .focusProperties { canFocus = false }
            },
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
            modifier = if (deviceRotationState == 1) {
                Modifier
                    .navigationBarsPadding()
                    .statusBarsPadding()
                    .systemBarsPadding()
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .fillMaxSize()
                    .focusTarget()
                    .focusProperties { canFocus = false }
            } else {
                Modifier
                    .navigationBarsPadding()
                    .statusBarsPadding()
                    .systemBarsPadding()
                    .fillMaxSize()
                    .focusTarget()
                    .focusProperties { canFocus = false }
            }
        ) {
            var leftClick: Long = 0
            val leftJob = remember { MutableStateFlow<Job?>(null) }
            val leftScope = rememberCoroutineScope()
            var middleClick: Long = 0
            val middleJob = remember { MutableStateFlow<Job?>(null) }
            val middleScope = rememberCoroutineScope()
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
                    leftJob.value?.cancel()
                    if (time - leftClick < 300L) {
                        if (!chromeOSDevice) {
                            playerController.value?.seekBack()
                        }
                        leftClick = 0
                        leftJob.value = null
                    } else {
                        leftJob.value = leftScope.launch {
                            leftClick = time
                            delay(300)
                            if (!showOverlayState) {
                                showOverlay.value = true
                            } else {
                                showOverlay.value = false
                            }
                            showSettings.value = false
                            showSubtitles.value = false
                            showSleepTimer.value = false
                            leftClick = 0
                            leftJob.value = null
                        }
                    }
                }
            ) {}
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
                            if (!showOverlayState) {
                                showOverlay.value = true
                            } else {
                                showOverlay.value = false
                            }
                            showSettings.value = false
                            showSubtitles.value = false
                            showSleepTimer.value = false
                            middleClick = 0
                            middleJob.value = null
                        }
                    }
                }
            ) {}
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
                        if (!chromeOSDevice) {
                            playerController.value?.seekForward()
                        }
                        rightClick = 0
                        rightJob.value = null
                    } else {
                        rightJob.value = rightScope.launch {
                            rightClick = time
                            delay(300)
                            if (!showOverlayState) {
                                showOverlay.value = true
                            } else {
                                showOverlay.value = false
                            }
                            showSettings.value = false
                            showSubtitles.value = false
                            showSleepTimer.value = false
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
                        .navigationBarsPadding()
                        .statusBarsPadding()
                        .systemBarsPadding()
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .fillMaxSize()
                        .focusTarget()
                        .focusProperties { canFocus = false }
                        .background(colorResource(R.color.dimBlack))
                } else {
                    Modifier
                        .navigationBarsPadding()
                        .statusBarsPadding()
                        .systemBarsPadding()
                        .fillMaxSize()
                        .focusTarget()
                        .focusProperties { canFocus = false }
                        .background(colorResource(R.color.dimBlack))
                }
            ) {
                // Play/Pause/Restart Button (Android)
                if (!chromeOSDevice) {
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
                        if (isPlayingState == 1) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(30.dp),
                                strokeWidth = 3.dp,
                                color = colorResource(R.color.white)
                            )
                        }
                        if (isPlayingState >= 2) {
                            Icon(
                                modifier = Modifier.size(50.dp),
                                painter = when (isPlayingState) {
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
                }
                // Bottom Row
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(start = 10.dp, end = 10.dp, bottom = 40.dp)
                        .height(25.dp)
                        .fillMaxWidth()
                ) {
                    if (chromeOSDevice) {
                        // Play/Pause/Restart Button (ChromeOS)
                        IconButton(
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
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 3.dp,
                                    color = colorResource(R.color.white)
                                )
                            }
                            if (isPlayingState >= 2) {
                                Icon(
                                    modifier = Modifier.size(25.dp),
                                    painter = when (isPlayingState) {
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
                        // Seek Back Button (ChromeOS)
                        IconButton(
                            onClick = {
                                playerController.value?.seekBack()
                            }
                        ) {
                            Icon(
                                modifier = Modifier.size(25.dp),
                                painter = painterResource(androidx.media3.session.R.drawable.media3_icon_skip_back_10),
                                tint = colorResource(R.color.white),
                                contentDescription = ""
                            )
                        }
                        // Seek Forward Button (ChromeOS)
                        IconButton(
                            onClick = {
                                playerController.value?.seekForward()
                            }
                        ) {
                            Icon(
                                modifier = Modifier.size(25.dp),
                                painter = painterResource(androidx.media3.session.R.drawable.media3_icon_skip_forward_10),
                                tint = colorResource(R.color.white),
                                contentDescription = ""
                            )
                        }
                    }
                    // Progress Slider
                    val sliderSource = remember { MutableInteractionSource() }
                    Slider(
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
                        value = playerPositionState,
                        valueRange = 0f..playerDurationState,
                        onValueChange = { newValue ->
                            playerController.value?.seekTo(newValue.toLong())
                        }
                    )
                }
                if (playerTimeState != null) {
                    Text(
                        modifier = if (chromeOSDevice) {
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 160.dp, end = 15.dp, bottom = 28.dp)
                        } else {
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 15.dp, end = 15.dp, bottom = 28.dp)
                        },
                        text = playerTimeState!!,
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
                        if (playerControllerState?.mediaMetadata?.title != null) {
                            Text(
                                text = playerControllerState?.mediaMetadata?.title.toString(),
                                color = colorResource(R.color.white),
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1
                            )
                        } else {
                            Text(
                                text = "No Video Loaded",
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
                            if (!chromeOSDevice) {
                                // Voice Search Button
                                IconButton(
                                    modifier = Modifier.width(50.dp),
                                    onClick = {
                                        playerControllerState?.pause()
                                        if (ContextCompat.checkSelfPermission(this@Player, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                            ActivityCompat.requestPermissions(this@Player, listOf(Manifest.permission.RECORD_AUDIO).toTypedArray(), 0)
                                        } else {
                                            val voiceIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                            voiceIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, this@Player.packageName)
                                            voiceIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.EXTRA_LANGUAGE_MODEL)
                                            voiceIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                                            voiceIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en")
                                            voiceIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say the video name you wish to search")
                                            playerSearch.launch(voiceIntent)
                                        }
                                    }
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.search),
                                        tint = colorResource(R.color.white),
                                        contentDescription = ""
                                    )
                                }
                            }
                            // Share Button
                            if (playerControllerState?.mediaItemCount == 1) {
                                IconButton(
                                    modifier = Modifier.width(50.dp),
                                    onClick = {
                                        if (chromeOSDevice) {
                                            val clipManager: ClipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                                            val clipData: ClipData = ClipData.newPlainText("", "https://youtu.be/${playerControllerState?.mediaMetadata?.extras?.getString("id")}")
                                            clipManager.setPrimaryClip(clipData)
                                        } else {
                                            val shareIntent = Intent()
                                            shareIntent.action = Intent.ACTION_SEND
                                            shareIntent.putExtra(Intent.EXTRA_TEXT, "https://youtu.be/${playerControllerState?.mediaMetadata?.extras?.getString("id")}")
                                            shareIntent.type = "text/plain"
                                            startActivity(Intent.createChooser(shareIntent, null))
                                        }
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
                            if (playerControllerState?.mediaItemCount == 1) {
                                IconButton(
                                    modifier = Modifier.width(50.dp),
                                    onClick = {
                                        if (!showSettingsState && !showSubtitlesState && !showSleepTimerState) {
                                            showSettings.value = true
                                        } else {
                                            showSettings.value = false
                                            showSubtitles.value = false
                                            showSleepTimer.value = false
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

        if (showSettingsState && playerControllerState?.mediaItemCount == 1) {
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
                ) {}
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
                                color = colorResource(R.color.white),
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
        }

        // Subtitles View

        if (showSubtitlesState && playerControllerState?.mediaItemCount == 1) {
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
                ) {}
                val lazyState = rememberLazyListState()
                val scrollState = rememberScrollAreaState(lazyState)
                ScrollArea(state = scrollState) {
                    LazyColumn(
                        state = lazyState,
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
                        val subtitles: JSONArray? = playerSubtitles
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
                                            checked = subtitlesCheckedState[0],
                                            onCheckedChange = { checked ->
                                                Collections.replaceAll(subtitlesChecked.value, true, false)
                                                subtitlesChecked.update { list ->
                                                    list.toMutableList().apply {
                                                        set(0, true)
                                                    }.toList()
                                                }
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
                                            checked = subtitlesCheckedState[index + 1],
                                            onCheckedChange = { checked ->
                                                Collections.replaceAll(subtitlesChecked.value, true, false)
                                                subtitlesChecked.update { list ->
                                                    list.toMutableList().apply {
                                                        set(index + 1, true)
                                                    }.toList()
                                                }
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
                    VerticalScrollbar(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .wrapContentHeight()
                            .heightIn(0.dp, 150.dp)
                            .width(4.dp)
                    ) {
                        Thumb(
                            modifier = Modifier.background(colorResource(R.color.lightGrey))
                        )
                    }
                }
            }
        }

        // Sleep Timer View

        if (showSleepTimerState && playerControllerState?.mediaItemCount == 1) {
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
                ) {}
                val lazyState = rememberLazyListState()
                val scrollState = rememberScrollAreaState(lazyState)
                ScrollArea(state = scrollState) {
                    LazyColumn(
                        state = lazyState,
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
                                        checked = sleepTimerCheckedState[0],
                                        onCheckedChange = { checked ->
                                            Collections.replaceAll(sleepTimerChecked.value, true, false)
                                            sleepTimerChecked.update { list ->
                                                list.toMutableList().apply {
                                                    set(0, true)
                                                }.toList()
                                            }
                                            if (checked) {
                                                val broadcastIntent = Intent("h.lillie.ytplayer.service.timer")
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
                                        checked = sleepTimerCheckedState[1],
                                        onCheckedChange = { checked ->
                                            Collections.replaceAll(sleepTimerChecked.value, true, false)
                                            sleepTimerChecked.update { list ->
                                                list.toMutableList().apply {
                                                    set(1, true)
                                                }.toList()
                                            }
                                            if (checked) {
                                                val broadcastIntent = Intent("h.lillie.ytplayer.service.timer")
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
                                        checked = sleepTimerCheckedState[2],
                                        onCheckedChange = { checked ->
                                            Collections.replaceAll(sleepTimerChecked.value, true, false)
                                            sleepTimerChecked.update { list ->
                                                list.toMutableList().apply {
                                                    set(2, true)
                                                }.toList()
                                            }
                                            if (checked) {
                                                val broadcastIntent = Intent("h.lillie.ytplayer.service.timer")
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
                                        checked = sleepTimerCheckedState[3],
                                        onCheckedChange = { checked ->
                                            Collections.replaceAll(sleepTimerChecked.value, true, false)
                                            sleepTimerChecked.update { list ->
                                                list.toMutableList().apply {
                                                    set(3, true)
                                                }.toList()
                                            }
                                            if (checked) {
                                                val broadcastIntent = Intent("h.lillie.ytplayer.service.timer")
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
                                        checked = sleepTimerCheckedState[4],
                                        onCheckedChange = { checked ->
                                            Collections.replaceAll(sleepTimerChecked.value, true, false)
                                            sleepTimerChecked.update { list ->
                                                list.toMutableList().apply {
                                                    set(4, true)
                                                }.toList()
                                            }
                                            if (checked) {
                                                val broadcastIntent = Intent("h.lillie.ytplayer.service.timer")
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
                    }
                    VerticalScrollbar(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .wrapContentHeight()
                            .heightIn(0.dp, 150.dp)
                            .width(4.dp)
                    ) {
                        Thumb(
                            modifier = Modifier.background(colorResource(R.color.lightGrey))
                        )
                    }
                }
            }
        }
    }

    private fun createPlayer(videoID: String?, searchQuery: String?) {
        playerController.value?.stop()
        playerController.value?.removeMediaItem(0)

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

            playerHandler = Handler(Looper.getMainLooper())
            playerHandler.post(playerTask)

            val broadcastIntent = Intent("h.lillie.ytplayer.service.info")
            broadcastIntent.setPackage(this.packageName)
            broadcastIntent.putExtra("videoID", videoID)
            broadcastIntent.putExtra("searchQuery", searchQuery)
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
                val subtitles = intent.extras!!.getString("subtitles")

                if (subtitlesChecked.value.isNotEmpty()) {
                    subtitlesChecked.update { list ->
                        list.toMutableList().apply {
                            clear()
                        }.toList()
                    }
                }

                if (subtitles != null) {
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
                } else {
                    playerSubtitles = null
                }
            }
        }
    }

    private val playerSearch = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS) ?: return@registerForActivityResult
            createPlayer(null, data[0])
        }
    }

    private val playerTask = object: Runnable {
        override fun run() {
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