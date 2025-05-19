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
import kotlinx.serialization.json.Json
import org.json.JSONArray

class Requests {
    suspend fun ytdlp(videoID: String?, searchQuery: String?): Info? = withContext(Dispatchers.IO) {
        val py: Python = Python.getInstance()
        var rq = ""

        runCatching {
            rq = py.getModule("ytdlp").callAttr("getInfo", videoID, searchQuery).toString()
        }.onFailure {
            return@withContext null
        }

        return@withContext Json.decodeFromString<Info>(rq)
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