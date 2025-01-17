package h.lillie.ytplayer

import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class Requests {
    suspend fun ytdlp(videoId: String) = withContext(Dispatchers.IO) {
        val py = Python.getInstance()
        val info = JSONObject((py.getModule("ytdlp").callAttr("getInfo", videoId)).toString())

        Application.id = info.optString("id")
        Application.title = info.optString("title")
        Application.author = info.optString("author")
        Application.artwork = info.optString("artwork")
        Application.live = info.optBoolean("live")
        Application.audioUrl = info.optString("audioUrl")
        Application.hlsUrl = info.optString("hlsUrl")

        return@withContext
    }

    suspend fun sponsorBlock(videoId: String) = withContext(Dispatchers.IO) {
        val client: OkHttpClient = OkHttpClient.Builder().build()

        val request = Request.Builder()
            .method("GET", null)
            .url("https://sponsor.ajay.app/api/skipSegments?videoID=$videoId&categories=[\"sponsor\",\"selfpromo\",\"interaction\",\"intro\",\"outro\",\"preview\",\"music_offtopic\"]")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            Application.sponsorBlock = null
            return@withContext
        }

        try {
            val jsonArray = JSONArray(response.body.string())
            Application.sponsorBlock = jsonArray
        } catch (_: JSONException) {
            Application.sponsorBlock = null
        }

        return@withContext
    }
}