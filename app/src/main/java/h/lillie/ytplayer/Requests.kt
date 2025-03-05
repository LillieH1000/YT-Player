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

        Application.id = info.optString("id")
        Application.title.value = info.optString("title")
        Application.author = info.optString("author")
        Application.artwork = info.optString("artwork")
        Application.live = info.optBoolean("live")
        Application.url = info.optString("url")
        Application.enCaptions = info.optString("enCaptions")

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
}