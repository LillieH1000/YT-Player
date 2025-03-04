package h.lillie.ytplayer

import android.app.Application
import android.content.pm.PackageManager
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
        var id = String()
        var title = MutableStateFlow(String())
        var author = String()
        var artwork = String()
        var live: Boolean = false
        var enCaptions = String()
        var audioUrl = String()
        var hlsUrl = String()
        var sponsorBlock: JSONArray? = null
        var castActive: Boolean = false
        var castExists: Boolean = false
        var androidTVDevice: Boolean = false
        var chromeOSDevice: Boolean = false
        var wearOSDevice: Boolean = false
    }
}