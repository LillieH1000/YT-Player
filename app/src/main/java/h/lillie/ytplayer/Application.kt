package h.lillie.ytplayer

import android.app.Application

class Application : Application() {
    companion object {
        var title = String()
        var author = String()
        var artwork = String()
        var url = String()
    }
}