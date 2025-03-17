package h.lillie.ytplayer

import com.chaquo.python.Python
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
    suspend fun ytdlp(videoId: String) = withContext(Dispatchers.IO) {
        val py: Python = Python.getInstance()
        val info = JSONObject((py.getModule("ytdlp").callAttr("getInfo", videoId)).toString())

        Application.id = optString(info, "id")
        Application.title.value = optString(info, "title")
        Application.author = optString(info, "author")
        Application.artwork = optString(info, "artwork")
        Application.live = info.optBoolean("live")
        Application.url = optString(info, "url")
        Application.subtitles = info.optJSONArray("subtitles")

        return@withContext
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