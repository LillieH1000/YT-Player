package h.lillie.ytplayer.requests

import android.content.Context
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

class Requests {
    suspend fun ytdlp(videoID: String?, searchQuery: String?): Info? = withContext(Dispatchers.IO) {
        val py: Python = Python.getInstance()

        runCatching {
            Json.decodeFromString<Info>(py.getModule("ytdlp").callAttr("getInfo", videoID, searchQuery).toString())
        }.getOrNull()
    }

    suspend fun returnYouTubeDislike(context: Context, videoID: String): Int? = withContext(Dispatchers.IO) {
        val client: OkHttpClient.Builder = OkHttpClient.Builder()

        val request: Request = Request.Builder()
            .method("GET", null)
            .url("https://returnyoutubedislikeapi.com/votes?videoId=$videoID")
            .build()

        val response: Response = client.build().newCall(request).execute()
        if (!response.isSuccessful) {
            return@withContext null
        }

        return@withContext JSONObject(response.body.string()).getInt("dislikes")
    }

    suspend fun sponsorBlock(context: Context, videoID: String): JSONArray? = withContext(Dispatchers.IO) {
        val client: OkHttpClient.Builder = OkHttpClient.Builder()

        val request: Request = Request.Builder()
            .method("GET", null)
            .url("https://sponsor.ajay.app/api/skipSegments?videoID=$videoID&category=sponsor")
            .build()

        val response: Response = client.build().newCall(request).execute()
        if (!response.isSuccessful) {
            return@withContext null
        }

        return@withContext JSONArray(response.body.string())
    }
}