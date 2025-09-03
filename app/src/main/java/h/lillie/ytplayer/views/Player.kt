package h.lillie.ytplayer.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView

@Composable
fun Player(playerController: MediaController?) {
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
                this.player = playerController
                this.useController = false
            }
        },
        update = { playerView ->
            playerView.player = playerController
        }
    )
}