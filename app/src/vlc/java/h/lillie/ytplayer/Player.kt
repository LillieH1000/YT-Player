package h.lillie.ytplayer

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.res.Configuration
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.WindowInsets
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import org.videolan.libvlc.MediaPlayer

@SuppressLint("SwitchIntDef")
class Player : AppCompatActivity() {
    private lateinit var playerServiceBinder: PlayerService.LibVLCBinder
    private lateinit var playerSource: MediaPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.player)

        onBackPressedDispatcher.addCallback(this) {}

        when {
            intent?.action == Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    val intentFilter = IntentFilter()
                    intentFilter.addAction("h.lillie.ytplayer.register")
                    registerReceiver(playerBroadcastReceiver, intentFilter, RECEIVER_NOT_EXPORTED)

                    broadcast(intent)
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
        Log.d("YT Player", "Entered Foreground")
        if (this::playerSource.isInitialized && !playerSource.vlcVout.areViewsAttached()) {
            Log.d("YT Player", "Attach Views")
            playerSource.attachViews(findViewById(R.id.playerView), null, false, false)
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d("YT Player", "Entered Background")
        playerSource.detachViews()
    }

    override fun onDestroy() {
        unregisterReceiver(playerBroadcastReceiver)
        stopService(Intent(this, PlayerService::class.java))
        super.onDestroy()
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

            Application.requests(result)

            bindService(Intent(this, PlayerService::class.java), object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    playerServiceBinder = service as PlayerService.LibVLCBinder
                    
                    val broadcastIntent = Intent("h.lillie.ytplayer.info")
                    broadcastIntent.setPackage(this@Player.packageName)
                    sendBroadcast(broadcastIntent)
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                }
            }, Context.BIND_AUTO_CREATE)
        }
    }

    private val playerBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "h.lillie.ytplayer.register") {
                playerSource = playerServiceBinder.getPlayer()
                playerSource.attachViews(findViewById(R.id.playerView), null, false, false)
            }
        }
    }
}