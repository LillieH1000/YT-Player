package h.lillie.ytplayer

import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

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

    suspend fun returnYouTubeDislike(videoID: String): Int? = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder().build()

        val request = Request.Builder()
            .method("GET", null)
            .url("https://returnyoutubedislikeapi.com/votes?videoId=$videoID")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            return@withContext null
        }

        return@withContext JSONObject(response.body.string()).getInt("dislikes")
    }

    suspend fun sponsorBlock(videoID: String): JSONArray? = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder().build()

        val request = Request.Builder()
            .method("GET", null)
            .url("https://sponsor.ajay.app/api/skipSegments?videoID=$videoID&category=sponsor")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            return@withContext null
        }

        return@withContext JSONArray(response.body.string())
    }
}