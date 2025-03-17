package h.lillie.ytplayer

import android.app.Application
import android.content.pm.PackageManager
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

class Application : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            androidTVDevice = true
        }
        if (packageManager.hasSystemFeature("org.chromium.arc.device_management")) {
            chromeOSDevice = true
        }
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            wearOSDevice = true
        }
    }

    companion object {
        var id: String? = null
        var title = MutableStateFlow<String?>(null)
        var author: String? = null
        var artwork: String? = null
        var live: Boolean = false
        var url: String? = null
        var subtitles: JSONObject? = null
        var sponsorBlock: JSONArray? = null
        var androidTVDevice: Boolean = false
        var chromeOSDevice: Boolean = false
        var wearOSDevice: Boolean = false
    }
}