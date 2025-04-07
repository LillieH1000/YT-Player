package h.lillie.ytplayer

import com.chaquo.python.Python
import com.google.gson.Gson
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class Requests {
    suspend fun ytdlp(videoID: String?, searchQuery: String?): Info = withContext(Dispatchers.IO) {
        val py: Python = Python.getInstance()
        val rq = py.getModule("ytdlp").callAttr("getInfo", videoID, searchQuery).toString()
        val jo = JSONObject(rq)

        Application.id = optString(jo, "id")
        Application.author = optString(jo, "author")
        Application.artwork = optString(jo, "artwork")
        Application.live = jo.optBoolean("live")
        Application.url = optString(jo, "url")
        Application.expiration = optString(jo, "expiration")
        Application.subtitles = jo.optJSONArray("subtitles")

        val gson = Gson()
        return@withContext gson.fromJson(rq, Info::class.java)
    }

    suspend fun sponsorBlock(videoId: String) = withContext(Dispatchers.IO) {
        val client = HttpClient(CIO)
        val response: HttpResponse = client.get("https://sponsor.ajay.app/api/skipSegments?videoID=$videoId&category=sponsor")
        if (!response.status.isSuccess()) {
            Application.sponsorBlock = null
            return@withContext
        }

        Application.sponsorBlock = JSONArray(response.bodyAsText())

        return@withContext
    }

    private fun optString(info: JSONObject, key: String): String? {
        if (info.isNull(key)) {
            return null
        }

        return info.optString(key)
    }
}