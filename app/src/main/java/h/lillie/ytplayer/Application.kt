package h.lillie.ytplayer

import android.app.Application

class Application : Application() {
    companion object {
        var id = String()
        var title = String()
        var author = String()
        var artwork = String()
        var audioUrl = String()
        var hlsUrl = String()
    }
}