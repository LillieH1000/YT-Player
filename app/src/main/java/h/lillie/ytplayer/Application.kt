package h.lillie.ytplayer

import android.app.Application
import org.json.JSONArray

class Application : Application() {
    override fun onCreate() {
        super.onCreate()
        if (packageManager.hasSystemFeature("org.chromium.arc.device_management")) {
            chromeosDevice = true
        }
    }

    companion object {
        var id = String()
        var title = String()
        var author = String()
        var artwork = String()
        var views = String()
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