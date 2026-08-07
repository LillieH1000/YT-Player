package h.lillie.ytplayer.player

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
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import coil3.compose.AsyncImage
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import h.lillie.ytplayer.data.Subtitles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Collections
import java.util.concurrent.TimeUnit

class Player: ComponentActivity(), Player.Listener {
    private lateinit var playerControllerFuture: ListenableFuture<MediaController>
    private var playerController = MutableStateFlow<MediaController?>(null)
    private var playerHandler: Handler = Handler(Looper.getMainLooper())
    private var playerSubtitles: ArrayList<Subtitles>? = null
    @UnstableApi private var playerSubtitlesView: SubtitleView? = null
    private var playerSubtitlesViewParent: ViewGroup? = null
    private var chromeOSDevice: Boolean = false
    private var isFirstLaunch: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (packageManager.hasSystemFeature("org.chromium.arc.device_management")) chromeOSDevice = true
        if (!chromeOSDevice) WindowInsetsControllerCompat(window, window.decorView).systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        enableEdgeToEdge()
        setContent {
            CreatePlayerUI()
        }

        onBackPressedDispatcher.addCallback(this) {
            @SuppressLint("SwitchIntDef")
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
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(playerBroadcastReceiver, intentFilter, RECEIVER_NOT_EXPORTED)
        } else {
            @SuppressLint("UnspecifiedRegisterReceiverFlag")
            registerReceiver(playerBroadcastReceiver, intentFilter)
        }

        when {
            intent?.action == Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    isFirstLaunch = true
                    val youtubeRegex = Regex("^.*(?:(?:youtu\\.be/|v/|vi/|u/\\w/|embed/|shorts/|live/)|(?:(?:watch)?\\?vi?=|&vi?=))([^#&?]*).*")
                    val info: String = intent.getStringExtra(Intent.EXTRA_TEXT)!!
                    if (youtubeRegex.containsMatchIn(info)) createPlayer(youtubeRegex.findAll(info).joinToString { it.groupValues[1] })
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
                    val info: String = intent.getStringExtra(Intent.EXTRA_TEXT)!!
                    if (youtubeRegex.containsMatchIn(info)) createPlayer(youtubeRegex.findAll(info).joinToString { it.groupValues[1] })
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        @SuppressLint("SwitchIntDef")
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
        playerHandler.post(playerTask)
    }

    override fun onStop() {
        super.onStop()
        playerHandler.removeCallbacksAndMessages(null)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT == 30 && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) enterPictureInPictureMode(PictureInPictureParams.Builder().build())
    }

    override fun onDestroy() {
        super.onDestroy()
        if (this::playerControllerFuture.isInitialized) MediaController.releaseFuture(playerControllerFuture)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        unregisterReceiver(playerBroadcastReceiver)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            showOverlay.value = false
            showPlaybackSpeed.value = false
            showSubtitles.value = false
            showSleepTimer.value = false
            showInfo.value = false
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
            KeyEvent.KEYCODE_DPAD_LEFT -> playerController.value?.seekBack()
            KeyEvent.KEYCODE_DPAD_RIGHT -> playerController.value?.seekForward()
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
                        val info: String = clipData.getItemAt(0).text.toString()
                        if (youtubeRegex.containsMatchIn(info)) {
                            createPlayer(youtubeRegex.findAll(info).joinToString { it.groupValues[1] })
                        }
                    }
                }
            }
            @SuppressLint("SwitchIntDef")
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
        loopChecked.value = repeatMode != Player.REPEAT_MODE_OFF
    }

    override fun onCues(cueGroup: CueGroup) {
        super.onCues(cueGroup)
        @UnstableApi
        playerSubtitlesView?.setCues(cueGroup.cues.map { cue ->
            cue.buildUpon()
                .setLine(Cue.DIMEN_UNSET, Cue.TYPE_UNSET)
                .setPosition(Cue.DIMEN_UNSET)
                .build()
        })
    }

    private var autoRotateEnabled = MutableStateFlow(false)
    private var deviceRotation = MutableStateFlow(0)
    private var isPlaying = MutableStateFlow(0)
    private var loopChecked = MutableStateFlow(false)
    private var playerDuration = MutableStateFlow(0f)
    private var playerPosition = MutableStateFlow(0f)
    private var playerSize = MutableStateFlow(false)
    private var playbackSpeed = MutableStateFlow(1f)
    private var playerTime = MutableStateFlow<String?>(null)
    private var showOverlay = MutableStateFlow(true)
    private var showInfo = MutableStateFlow(false)
    private var showSubtitles = MutableStateFlow(false)
    private var showSleepTimer = MutableStateFlow(false)
    private var showPlaybackSpeed = MutableStateFlow(false)
    private var subtitlesChecked = MutableStateFlow<List<Boolean>>(listOf())
    private var sleepTimerChecked = MutableStateFlow(listOf(false, false, false, false, false))

    @Composable
    private fun CreatePlayerUI() {
        // States

        val autoRotateEnabledState by autoRotateEnabled.collectAsState()
        val deviceRotationState by deviceRotation.collectAsState()
        val isPlayingState by isPlaying.collectAsState()
        val loopCheckedState by loopChecked.collectAsState()
        val playerControllerState by playerController.collectAsState()
        val playerDurationState by playerDuration.collectAsState()
        val playerPositionState by playerPosition.collectAsState()
        val playerSizeState by playerSize.collectAsState()
        val playbackSpeedState by playbackSpeed.collectAsState()
        val playerTimeState by playerTime.collectAsState()
        val showOverlayState by showOverlay.collectAsState()
        val showInfoState by showInfo.collectAsState()
        val showSubtitlesState by showSubtitles.collectAsState()
        val showSleepTimerState by showSleepTimer.collectAsState()
        val showPlaybackSpeedState by showPlaybackSpeed.collectAsState()
        val subtitlesCheckedState by subtitlesChecked.collectAsState()
        val sleepTimerCheckedState by sleepTimerChecked.collectAsState()

        // Player View

        @UnstableApi
        AndroidView(
            modifier = Modifier
                .background(Color.Black)
                .systemBarsPadding()
                .fillMaxSize(),
            factory = { context ->
                PlayerView(context).apply {
                    this.useController = false
                    playerSubtitlesView = SubtitleView(context).apply {
                        this.setApplyEmbeddedStyles(false)
                    }
                    this.subtitleView?.apply {
                        playerSubtitlesViewParent = this
                        this.setApplyEmbeddedStyles(false)
                        this.setFractionalTextSize(0f)
                    }
                }
            },
            update = { playerView ->
                playerView.apply {
                    this.player = playerControllerState
                    this.resizeMode = if (playerSizeState && deviceRotationState == 1) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                }
            }
        )

        // Subtitles Landscape View

        @UnstableApi
        AndroidView(
            modifier = Modifier
                .systemBarsPadding()
                .fillMaxSize(),
            factory = { context ->
                FrameLayout(context)
            },
            update = { view ->
                if (deviceRotationState == 1) {
                    playerSubtitlesViewParent?.removeView(playerSubtitlesView)
                    view.addView(playerSubtitlesView)
                } else {
                    view.removeView(playerSubtitlesView)
                    playerSubtitlesViewParent?.addView(playerSubtitlesView)
                }
            }
        )

        // 3 View

        Row(
            modifier = Modifier
                .systemBarsPadding()
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .noRippleClickable(
                        onDoubleClick = {
                            playerController.value?.seekBack()
                        },
                        onClick = {
                            if (playerControllerState?.mediaItemCount == 1) {
                                showOverlay.value = !showOverlayState
                                showPlaybackSpeed.value = false
                                showSubtitles.value = false
                                showSleepTimer.value = false
                                showInfo.value = false
                            }
                        }
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .noRippleClickable(
                        onDoubleClick = {},
                        onClick = {
                            if (playerControllerState?.mediaItemCount == 1) {
                                showOverlay.value = !showOverlayState
                                showPlaybackSpeed.value = false
                                showSubtitles.value = false
                                showSleepTimer.value = false
                                showInfo.value = false
                            }
                        }
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .noRippleClickable(
                        onDoubleClick = {
                            playerController.value?.seekForward()
                        },
                        onClick = {
                            if (playerControllerState?.mediaItemCount == 1) {
                                showOverlay.value = !showOverlayState
                                showPlaybackSpeed.value = false
                                showSubtitles.value = false
                                showSleepTimer.value = false
                                showInfo.value = false
                            }
                        }
                    )
            )
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
                        .systemBarsPadding()
                        .displayCutoutPadding()
                        .fillMaxSize()
                } else {
                    Modifier
                        .background(
                            brush = SolidColor(Color.Black),
                            alpha = 0.4F
                        )
                        .systemBarsPadding()
                        .fillMaxSize()
                }
            ) {
                // Play/Pause/Restart Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(50.dp)
                        .clip(CircleShape)
                        .noRippleClickable {
                            if (playerController.value != null && playerControllerState?.mediaItemCount == 1) {
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
                                2 -> Icons.Default.PlayArrow
                                3 -> Icons.Default.Pause
                                else -> Icons.Default.Replay
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
                    if (playerControllerState?.mediaItemCount == 1) {
                        @OptIn(ExperimentalMaterial3Api::class)
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
                    }
                    // Reset To Live Button
                    if (playerControllerState?.mediaItemCount == 1 && playerControllerState?.mediaMetadata?.extras?.getBoolean("live") == true) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .width(40.dp)
                                .align(Alignment.CenterVertically)
                                .clip(CircleShape)
                                .noRippleClickable {
                                    playerController.value?.seekToDefaultPosition()
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                tint = Color.White,
                                contentDescription = ""
                            )
                        }
                    }
                    // Fill Button
                    if (!chromeOSDevice && deviceRotationState == 1 && playerControllerState?.mediaItemCount == 1) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .width(40.dp)
                                .align(Alignment.CenterVertically)
                                .clip(CircleShape)
                                .noRippleClickable {
                                    playerSize.value = !playerSizeState
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Default.FitScreen,
                                tint = Color.White,
                                contentDescription = ""
                            )
                        }
                    }
                    // Fullscreen Button
                    if (!autoRotateEnabledState && !chromeOSDevice && playerControllerState?.mediaItemCount == 1) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .width(40.dp)
                                .align(Alignment.CenterVertically)
                                .clip(CircleShape)
                                .noRippleClickable {
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
                        text = if (playerControllerState?.mediaItemCount == 1) {
                            playerTimeState!!
                        } else {
                            ""
                        },
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
                    Text(
                        modifier = Modifier
                            .weight(1f)
                            .align(Alignment.CenterVertically),
                        text = if (playerControllerState?.mediaItemCount == 1) {
                            playerControllerState?.mediaMetadata?.title.toString()
                        } else {
                            ""
                        },
                        color = Color.White,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1
                    )
                    // Menu Buttons
                    Row(
                        modifier = Modifier
                            .wrapContentWidth()
                            .align(Alignment.CenterVertically)
                    ) {
                        if (playerControllerState?.mediaItemCount == 1) {
                            // Info Button
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .width(40.dp)
                                    .align(Alignment.CenterVertically)
                                    .clip(CircleShape)
                                    .noRippleClickable {
                                        showInfo.value = true
                                    }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    tint = Color.White,
                                    contentDescription = ""
                                )
                            }
                            // Sleep Timer Button
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .width(40.dp)
                                    .align(Alignment.CenterVertically)
                                    .clip(CircleShape)
                                    .noRippleClickable {
                                        showSleepTimer.value = true
                                    }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Bedtime,
                                    tint = Color.White,
                                    contentDescription = ""
                                )
                            }
                            // Loop Button
                            if (playerControllerState?.mediaMetadata?.extras?.getBoolean("live") != true) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .width(40.dp)
                                        .align(Alignment.CenterVertically)
                                        .clip(CircleShape)
                                        .noRippleClickable {
                                            if (playerController.value?.repeatMode == Player.REPEAT_MODE_OFF) {
                                                playerController.value?.repeatMode = Player.REPEAT_MODE_ONE
                                            } else {
                                                playerController.value?.repeatMode = Player.REPEAT_MODE_OFF
                                            }
                                        }
                                ) {
                                    Icon(
                                        imageVector = if (!loopCheckedState) {
                                            Icons.Outlined.Repeat
                                        } else {
                                            Icons.Outlined.RepeatOne
                                        },
                                        tint = Color.White,
                                        contentDescription = ""
                                    )
                                }
                            }
                            // Speed Button
                            if (playerControllerState?.mediaMetadata?.extras?.getBoolean("live") != true) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .width(40.dp)
                                        .align(Alignment.CenterVertically)
                                        .clip(CircleShape)
                                        .noRippleClickable {
                                            showPlaybackSpeed.value = true
                                        }
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Speed,
                                        tint = Color.White,
                                        contentDescription = ""
                                    )
                                }
                            }
                            // Subtitles Button
                            if (playerSubtitles != null && playerControllerState?.mediaMetadata?.extras?.getBoolean("live") != true) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .width(40.dp)
                                        .align(Alignment.CenterVertically)
                                        .clip(CircleShape)
                                        .noRippleClickable {
                                            showSubtitles.value = true
                                        }
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Subtitles,
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

        // Subtitles Sheet

        if (showSubtitlesState) {
            @OptIn(ExperimentalMaterial3Api::class)
            ModalBottomSheet(
                containerColor = Color.DarkGray,
                modifier = Modifier.statusBarsPadding(),
                dragHandle = {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 15.dp, end = 15.dp, top = 5.dp),
                            text = "Subtitles",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                        BottomSheetDefaults.DragHandle(
                            modifier = Modifier.align(Alignment.Center),
                            color = Color.LightGray
                        )
                    }
                },
                onDismissRequest = {
                    showSubtitles.value = false
                }
            ) {
                Box(
                    modifier = Modifier
                        .systemBarsPadding()
                        .padding(bottom = 20.dp)
                        .wrapContentHeight()
                        .fillMaxWidth()
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .wrapContentHeight()
                            .fillMaxWidth()
                            .clickable(
                                enabled = true,
                                interactionSource = null,
                                indication = null,
                                onClick = {})
                    ) {
                        items(playerSubtitles!!.size + 1) { index ->
                            Row(
                                modifier = Modifier
                                    .height(50.dp)
                                    .noRippleClickable {
                                        Collections.replaceAll(
                                            subtitlesChecked.value,
                                            true,
                                            false
                                        )
                                        subtitlesChecked.update { list ->
                                            list.toMutableList().apply {
                                                set(index, true)
                                            }.toList()
                                        }
                                        when (index) {
                                            0 -> {
                                                playerController.value?.trackSelectionParameters = playerController.value?.trackSelectionParameters!!.buildUpon()
                                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                                    .build()
                                            }
                                            else -> {
                                                playerController.value?.trackSelectionParameters = playerController.value?.trackSelectionParameters!!.buildUpon()
                                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                    .setPreferredTextLanguage(playerSubtitles!![index - 1].id)
                                                    .build()
                                            }
                                        }
                                    }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.CenterVertically)
                                        .padding(start = 10.dp)
                                        .weight(1f)
                                ) {
                                    Text(
                                        text = when (index) {
                                            0 -> "Off"
                                            else -> playerSubtitles!![index - 1].name
                                        },
                                        color = Color.White,
                                        overflow = TextOverflow.Ellipsis,
                                        maxLines = 1
                                    )
                                }
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.CenterVertically)
                                        .scale(0.9f)
                                        .width(40.dp)
                                ) {
                                    Checkbox(
                                        colors = CheckboxColors(
                                            checkedCheckmarkColor = Color.White,
                                            uncheckedCheckmarkColor = Color.Unspecified,
                                            checkedBoxColor = Color.Unspecified,
                                            uncheckedBoxColor = Color.Unspecified,
                                            disabledCheckedBoxColor = Color.Unspecified,
                                            disabledUncheckedBoxColor = Color.Unspecified,
                                            disabledIndeterminateBoxColor = Color.Unspecified,
                                            checkedBorderColor = Color.Unspecified,
                                            uncheckedBorderColor = Color.Unspecified,
                                            disabledBorderColor = Color.Unspecified,
                                            disabledUncheckedBorderColor = Color.Unspecified,
                                            disabledIndeterminateBorderColor = Color.Unspecified
                                        ),
                                        checked = subtitlesCheckedState[index],
                                        onCheckedChange = null
                                    )
                                }
                            }
                            if (index < playerSubtitles!!.size) {
                                HorizontalDivider(
                                    color = Color.LightGray,
                                    modifier = Modifier.padding(start = 10.dp, end = 10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Sleep Timer Sheet

        if (showSleepTimerState) {
            @OptIn(ExperimentalMaterial3Api::class)
            ModalBottomSheet(
                containerColor = Color.DarkGray,
                modifier = Modifier.statusBarsPadding(),
                dragHandle = {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 15.dp, end = 15.dp, top = 5.dp),
                            text = "Sleep Timer",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                        BottomSheetDefaults.DragHandle(
                            modifier = Modifier.align(Alignment.Center),
                            color = Color.LightGray
                        )
                    }
                },
                onDismissRequest = {
                    showSleepTimer.value = false
                }
            ) {
                Box(
                    modifier = Modifier
                        .systemBarsPadding()
                        .padding(bottom = 20.dp)
                        .wrapContentHeight()
                        .fillMaxWidth()
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .wrapContentHeight()
                            .fillMaxWidth()
                            .clickable(
                                enabled = true,
                                interactionSource = null,
                                indication = null,
                                onClick = {})
                    ) {
                        items(5) { index ->
                            Row(
                                modifier = Modifier
                                    .height(50.dp)
                                    .noRippleClickable {
                                        Collections.replaceAll(sleepTimerChecked.value, true, false)
                                        sleepTimerChecked.update { list ->
                                            list.toMutableList().apply {
                                                set(index, true)
                                            }.toList()
                                        }
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
                            ) {
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.CenterVertically)
                                        .padding(start = 10.dp)
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
                                        .scale(0.9f)
                                        .width(40.dp)
                                ) {
                                    Checkbox(
                                        colors = CheckboxColors(
                                            checkedCheckmarkColor = Color.White,
                                            uncheckedCheckmarkColor = Color.Unspecified,
                                            checkedBoxColor = Color.Unspecified,
                                            uncheckedBoxColor = Color.Unspecified,
                                            disabledCheckedBoxColor = Color.Unspecified,
                                            disabledUncheckedBoxColor = Color.Unspecified,
                                            disabledIndeterminateBoxColor = Color.Unspecified,
                                            checkedBorderColor = Color.Unspecified,
                                            uncheckedBorderColor = Color.Unspecified,
                                            disabledBorderColor = Color.Unspecified,
                                            disabledUncheckedBorderColor = Color.Unspecified,
                                            disabledIndeterminateBorderColor = Color.Unspecified
                                        ),
                                        checked = sleepTimerCheckedState[index],
                                        onCheckedChange = null
                                    )
                                }
                            }
                            if (index < 4) {
                                HorizontalDivider(
                                    color = Color.LightGray,
                                    modifier = Modifier.padding(start = 10.dp, end = 10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Playback Speed Sheet

        if (showPlaybackSpeedState) {
            @OptIn(ExperimentalMaterial3Api::class)
            ModalBottomSheet(
                containerColor = Color.DarkGray,
                modifier = Modifier.statusBarsPadding(),
                dragHandle = {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 15.dp, end = 15.dp, top = 5.dp),
                            text = "Playback Speed",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                        BottomSheetDefaults.DragHandle(
                            modifier = Modifier.align(Alignment.Center),
                            color = Color.LightGray
                        )
                    }
                },
                onDismissRequest = {
                    showPlaybackSpeed.value = false
                }
            ) {
                Box(
                    modifier = Modifier
                        .systemBarsPadding()
                        .padding(bottom = 20.dp)
                        .wrapContentHeight()
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(start = 10.dp, end = 10.dp)
                    ) {
                        val sliderSource = remember { MutableInteractionSource() }
                        Slider(
                            interactionSource = sliderSource,
                            steps = 18,
                            thumb = {
                                SliderDefaults.Thumb(
                                    interactionSource = sliderSource,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.LightGray
                                    ),
                                )
                            },
                            track = { sliderState ->
                                SliderDefaults.Track(
                                    colors = SliderDefaults.colors(
                                        activeTickColor = Color.DarkGray,
                                        inactiveTickColor = Color.DarkGray,
                                        activeTrackColor = Color.LightGray,
                                        inactiveTrackColor = Color.LightGray
                                    ),
                                    sliderState = sliderState
                                )
                            },
                            value = playbackSpeedState,
                            valueRange = 0.1f..2f,
                            onValueChange = { value ->
                                val decimalFormat = DecimalFormat("#.#")
                                playerController.value!!.playbackParameters = PlaybackParameters(decimalFormat.format(value).toFloat())
                                playbackSpeed.value = decimalFormat.format(value).toFloat()
                            }
                        )
                        Text(
                            modifier = Modifier.padding(start = 5.dp, end = 5.dp),
                            text = "Speed: $playbackSpeedState",
                            color = Color.White,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Info Sheet

        if (showInfoState) {
            @OptIn(ExperimentalMaterial3Api::class)
            ModalBottomSheet(
                containerColor = Color.DarkGray,
                modifier = Modifier.statusBarsPadding(),
                dragHandle = {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 15.dp, end = 15.dp, top = 5.dp),
                            text = "Info",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                        BottomSheetDefaults.DragHandle(
                            modifier = Modifier.align(Alignment.Center),
                            color = Color.LightGray
                        )
                    }
                },
                onDismissRequest = {
                    showInfo.value = false
                }
            ) {
                Column(
                    modifier = Modifier
                        .systemBarsPadding()
                        .padding(bottom = 20.dp)
                        .wrapContentHeight()
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.height(50.dp)
                    ) {
                        // Views
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(start = 15.dp, end = 15.dp, top = 5.dp),
                                text = "Views",
                                color = Color.White,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1
                            )
                            Text(
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(start = 15.dp, end = 15.dp, top = 5.dp),
                                text = NumberFormat.getInstance().format(playerControllerState?.mediaMetadata?.extras?.getLong("views")),
                                color = Color.White,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1
                            )
                        }
                        // Likes
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(start = 15.dp, end = 15.dp, top = 5.dp),
                                text = "Likes",
                                color = Color.White,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1
                            )
                            Text(
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(start = 15.dp, end = 15.dp, top = 5.dp),
                                text = NumberFormat.getInstance().format(playerControllerState?.mediaMetadata?.extras?.getLong("likes")),
                                color = Color.White,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1
                            )
                        }
                        // Dislikes
                        if (playerControllerState?.mediaMetadata?.extras?.getLong("dislikes") != null) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .padding(start = 15.dp, end = 15.dp, top = 5.dp),
                                    text = "Dislikes",
                                    color = Color.White,
                                    overflow = TextOverflow.Ellipsis,
                                    maxLines = 1
                                )
                                Text(
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .padding(start = 15.dp, end = 15.dp, top = 5.dp),
                                    text = NumberFormat.getInstance().format(playerControllerState?.mediaMetadata?.extras?.getLong("dislikes")),
                                    color = Color.White,
                                    overflow = TextOverflow.Ellipsis,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    HorizontalDivider(
                        color = Color.LightGray,
                        modifier = Modifier.padding(10.dp)
                    )
                    // Channel
                    Row(
                        modifier = Modifier.height(40.dp)
                    ) {
                        AsyncImage(
                            modifier = Modifier
                                .padding(start = 15.dp, end = 15.dp)
                                .clip(CircleShape),
                            model = playerControllerState?.mediaMetadata?.extras?.getString("artwork"),
                            contentDescription = ""
                        )
                        Text(
                            modifier = Modifier.align(Alignment.CenterVertically),
                            text = playerControllerState?.mediaMetadata?.artist.toString(),
                            color = Color.White,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                        Box(modifier = Modifier.weight(1f))
                        val uriHandler = LocalUriHandler.current
                        Box(
                            modifier = Modifier
                                .height(30.dp)
                                .width(70.dp)
                                .align(Alignment.CenterVertically)
                                .padding(end = 15.dp)
                                .border(1.dp, Color.LightGray, RoundedCornerShape(16.dp))
                                .noRippleClickable {
                                    uriHandler.openUri(playerControllerState?.mediaMetadata?.extras?.getString("channel")!!)
                                },
                        ) {
                            Text(
                                modifier = Modifier.align(Alignment.Center),
                                text = "View",
                                color = Color.White,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1
                            )
                        }
                    }
                    HorizontalDivider(
                        color = Color.LightGray,
                        modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 20.dp)
                    )
                    // Description
                    LazyColumn {
                        item {
                            Text(
                                modifier = Modifier.padding(start = 15.dp, end = 15.dp),
                                text = playerControllerState?.mediaMetadata?.extras?.getString("description")!!,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }

    private fun createPlayer(videoID: String?) {
        playerController.value?.stop()
        playerController.value?.removeMediaItem(0)
        showOverlay.value = true
        showPlaybackSpeed.value = false
        showSubtitles.value = false
        showSleepTimer.value = false
        showInfo.value = false
        isPlaying.value = 0

        Toast.makeText(this, "Loading, please wait", Toast.LENGTH_SHORT).show()

        val sessionToken = SessionToken(this, ComponentName(this, Service::class.java))
        playerControllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        playerControllerFuture.addListener({
            playerController.value = playerControllerFuture.get()
            playerController.value!!.addListener(this)
            playbackSpeed.value = 1f

            Collections.replaceAll(sleepTimerChecked.value, true, false)
            sleepTimerChecked.update { list ->
                list.toMutableList().apply {
                    set(0, true)
                }.toList()
            }

            if (Build.VERSION.SDK_INT >= 31 && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
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
            sendBroadcast(broadcastIntent)
        }, MoreExecutors.directExecutor())
    }

    private fun optTime(time: Long): String {
        val hours: Long = TimeUnit.MILLISECONDS.toHours(time)
        val minutes: Long = (TimeUnit.MILLISECONDS.toMinutes(time) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(time)))
        val seconds: Long = (TimeUnit.MILLISECONDS.toSeconds(time) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(time)))
        var formatted = ""
        if (hours != 0L) {
            formatted += "$hours:"
        }
        if (formatted != "") {
            formatted += if (minutes >= 10) {
                "$minutes:"
            } else {
                "0$minutes:"
            }
        }
        if (formatted == "") {
            formatted += "$minutes:"
        }
        formatted += if (seconds >= 10) {
            seconds
        } else {
            "0$seconds"
        }
        return formatted
    }

    private fun BroadcastReceiver.coroutineScope(onReceive: suspend () -> Unit) {
        val pendingResult: BroadcastReceiver.PendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            onReceive()
        }.invokeOnCompletion {
            pendingResult.finish()
        }
    }

    @Composable
    private fun Modifier.noRippleClickable(onDoubleClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier = composed {
        combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onDoubleClick = onDoubleClick,
            onClick = onClick
        )
    }

    private val playerBroadcastReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = coroutineScope {
            if (intent?.action == "h.lillie.ytplayer.activity.subtitles") {
                if (subtitlesChecked.value.isNotEmpty()) {
                    subtitlesChecked.update { list ->
                        list.toMutableList().apply {
                            clear()
                        }.toList()
                    }
                }

                playerSubtitles = if (Build.VERSION.SDK_INT >= 33) {
                    intent.extras!!.getParcelableArrayList("subtitles", Subtitles::class.java)
                } else {
                    @Suppress("Deprecation")
                    intent.extras!!.getParcelableArrayList("subtitles")
                } ?: return@coroutineScope

                subtitlesChecked.update { list ->
                    list.toMutableList().apply {
                        add(true)
                    }.toList()
                }

                playerSubtitles!!.forEach { _ ->
                    subtitlesChecked.update { list ->
                        list.toMutableList().apply {
                            add(false)
                        }.toList()
                    }
                }
                return@coroutineScope
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
                    Player.STATE_BUFFERING -> isPlaying.value = 1
                    Player.STATE_ENDED -> isPlaying.value = 4
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