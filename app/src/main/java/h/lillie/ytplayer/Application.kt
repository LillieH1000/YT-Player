package h.lillie.ytplayer

import android.app.Application
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray

class Application : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        if (packageManager.hasSystemFeature("org.chromium.arc.device_management")) {
            chromeOSDevice = true
        }
    }

    companion object {
        var id: String? = null
        var title = MutableStateFlow<String?>(null)
        var author: String? = null
        var artwork: String? = null
        var live: Boolean = false
        var url: String? = null
        var subtitles: JSONArray? = null
        var sponsorBlock: JSONArray? = null
        var chromeOSDevice: Boolean = false
    }
}