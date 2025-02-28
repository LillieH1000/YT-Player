package h.lillie.ytplayer

import android.app.PictureInPictureParams
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
class Player: ComponentActivity(), Player.Listener {
    private lateinit var playerControllerFuture: ListenableFuture<MediaController>
    private lateinit var playerController: MediaController
    private var isFirstLaunch: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !isFirstLaunch) {
            isFirstLaunch = true
            val clipManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = clipManager.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                createRequest(clipData.getItemAt(0).text.toString())
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

    @Composable
    private fun CreatePlayerUI() {
        var showOverlay by remember { mutableStateOf(false) }
        AndroidView(
            modifier = Modifier
                .background(colorResource(R.color.black))
                .fillMaxSize(),
            factory = { context ->
                PlayerView(context).apply {
                    player = playerController
                    useController = false
                }
            },
        )
        Row(modifier = Modifier.fillMaxSize()) {
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
            ) {
                if (showOverlay) {
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
                            painter = if (!playerController.isPlaying) {
                                painterResource(androidx.media3.session.R.drawable.media3_icon_play)
                            } else {
                                painterResource(androidx.media3.session.R.drawable.media3_icon_pause)
                            },
                            tint = colorResource(R.color.white),
                            contentDescription = ""
                        )
                    }
                }
            }
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

            val broadcastIntent = Intent("h.lillie.ytplayer.info")
            broadcastIntent.setPackage(this.packageName)
            sendBroadcast(broadcastIntent)
        }, MoreExecutors.directExecutor())
    }
}