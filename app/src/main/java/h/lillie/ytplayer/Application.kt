package h.lillie.ytplayer

import android.app.Application
import org.json.JSONArray

class Application : Application() {
    companion object {
        var id = String()
        var title = String()
        var author = String()
        var artwork = String()
        var views = String()
        var likes: Int = 0
        var dislikes: Int = 0
        var audioUrl = String()
        var hlsUrl = String()
        var sponsorBlock: JSONArray? = JSONArray()
    }
}