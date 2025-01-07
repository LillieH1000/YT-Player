package h.lillie.ytplayer

import android.app.Application
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONArray

class Application : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this));
        }
        if (packageManager.hasSystemFeature("org.chromium.arc.device_management")) {
            chromeosDevice = true
        }
    }

    companion object {
        var id = String()
        var title = String()
        var author = String()
        var artwork = String()
        var views: Int = 0
        var likes: Int = 0
        var dislikes: Int = 0
        var live: Boolean = false
        var audioUrl = String()
        var hlsUrl = String()
        var sponsorBlock: JSONArray? = JSONArray()
        var castActive: Boolean = false
        var chromeosDevice: Boolean = false
    }
}