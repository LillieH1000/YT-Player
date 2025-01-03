package h.lillie.ytplayer

import android.app.Application
import android.os.StrictMode
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class Application : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this));
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
        var videoUrl = String()
        var audioUrl = String()
        var hlsUrl = String()
        var sponsorBlock: JSONArray? = JSONArray()
        var castActive: Boolean = false
        fun requests(videoId: String) {
            val policy = StrictMode.ThreadPolicy.Builder().permitNetwork().build()
            StrictMode.setThreadPolicy(policy)

            h.lillie.ytplayer.Application().ytdlp(videoId)
            h.lillie.ytplayer.Application().sponsorBlock(videoId)
            h.lillie.ytplayer.Application().returnYouTubeDislike(videoId)
        }
    }

    private fun ytdlp(videoId: String) {
        val py = Python.getInstance()
        val info = JSONObject((py.getModule("ytdlp").callAttr("getInfo", videoId)).toString())

        id = info.optString("id")
        title = info.optString("title")
        author = info.optString("uploader")
        artwork = info.optString("thumbnail")
        views = info.optInt("view_count")
        likes = info.optInt("like_count")
        live = info.optBoolean("is_live")
        videoUrl = info.getJSONArray("requested_formats").getJSONObject(0).optString("url")
        audioUrl = info.getJSONArray("requested_formats").getJSONObject(1).optString("url")
    }

    private fun sponsorBlock(videoId: String) {
        val client: OkHttpClient = OkHttpClient.Builder().build()

        val request = Request.Builder()
            .method("GET", null)
            .url("https://sponsor.ajay.app/api/skipSegments?videoID=$videoId&categories=[\"sponsor\",\"selfpromo\",\"interaction\",\"intro\",\"outro\",\"preview\",\"music_offtopic\"]")
            .build()

        try {
            val jsonArray = JSONArray(client.newCall(request).execute().body.string())
            sponsorBlock = jsonArray
        } catch (_: JSONException) {
            sponsorBlock = null
        }
    }

    private fun returnYouTubeDislike(videoId: String) {
        val client: OkHttpClient = OkHttpClient.Builder().build()

        val request = Request.Builder()
            .method("GET", null)
            .url("https://returnyoutubedislikeapi.com/votes?videoId=$videoId")
            .build()

        val jsonObject = JSONObject(client.newCall(request).execute().body.string())

        dislikes = jsonObject.optInt("dislikes")
    }
}