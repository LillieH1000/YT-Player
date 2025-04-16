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

class Requests {
    suspend fun ytdlp(videoID: String?, searchQuery: String?): Info? = withContext(Dispatchers.IO) {
        var rq = ""
        val py: Python = Python.getInstance()

        runCatching {
            rq = py.getModule("ytdlp").callAttr("getInfo", videoID, searchQuery).toString()
        }.onFailure {
            return@withContext null
        }

        val gson = Gson()
        return@withContext gson.fromJson(rq, Info::class.java)
    }

    suspend fun sponsorBlock(videoId: String): JSONArray? = withContext(Dispatchers.IO) {
        val client = HttpClient(CIO)
        val response: HttpResponse = client.get("https://sponsor.ajay.app/api/skipSegments?videoID=$videoId&category=sponsor")
        if (!response.status.isSuccess()) {
            return@withContext null
        }

        return@withContext JSONArray(response.bodyAsText())
    }
}