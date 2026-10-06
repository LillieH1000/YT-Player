package h.lillie.ytplayer.requests

import android.annotation.SuppressLint
import android.content.Context
import android.net.http.HttpEngine
import android.os.Build
import android.os.ext.SdkExtensions
import com.chaquo.python.Python
import h.lillie.ytplayer.data.Return
import h.lillie.ytplayer.data.YTdlp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class Requests {
    suspend fun extractor(context: Context, videoID: String): Return? = withContext(Dispatchers.IO) {
        val py: Python = Python.getInstance()

        val info: YTdlp = runCatching {
            Json.decodeFromString<YTdlp>(py.getModule("ytdlp").callAttr("getInfo", "${context.applicationInfo.nativeLibraryDir}/libqjs.so", videoID).toString())
        }.getOrNull() ?: return@withContext null

        return@withContext Return(
            info.id,
            info.title,
            info.author,
            info.artwork,
            info.channel,
            info.thumbnail,
            info.description,
            info.live,
            info.views,
            info.likes,
            info.type,
            info.hls,
            info.availability,
            info.subtitles
        )
    }

    suspend fun returnYouTubeDislike(context: Context, videoID: String): Long? = withContext(Dispatchers.IO) {
        val body: String = if (SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 7) {
            httpEngineRequest(context, "https://returnyoutubedislikeapi.com/votes?videoId=$videoID")
        } else {
            okHttpRequest("https://returnyoutubedislikeapi.com/votes?videoId=$videoID")
        } ?: return@withContext null

        return@withContext JSONObject(body).getLong("dislikes")
    }

    suspend fun sponsorBlock(context: Context, videoID: String): JSONArray? = withContext(Dispatchers.IO) {
        val body: String = if (SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 7) {
            httpEngineRequest(context, "https://sponsor.ajay.app/api/skipSegments?videoID=$videoID&category=sponsor")
        } else {
            okHttpRequest("https://sponsor.ajay.app/api/skipSegments?videoID=$videoID&category=sponsor")
        } ?: return@withContext null

        return@withContext JSONArray(body)
    }

    @SuppressLint("NewApi")
    private suspend fun httpEngineRequest(context: Context, url: String): String? = withContext(Dispatchers.IO) {
        val httpEngine: HttpEngine = HttpEngine.Builder(context)
            .setEnableHttp2(true)
            .setEnableQuic(true)
            .build()

        val connection: HttpURLConnection = httpEngine.openConnection(URL(url)) as HttpURLConnection
        connection.requestMethod = "GET"

        val responseCode: Int = connection.responseCode
        if (responseCode != 200) {
            connection.disconnect()
            return@withContext null
        }

        val body: String = connection.getInputStream().bufferedReader().use { it.readText() }
        connection.disconnect()
        return@withContext body
    }

    private suspend fun okHttpRequest(url: String): String? = withContext(Dispatchers.IO) {
        val client: OkHttpClient = OkHttpClient.Builder().build()

        val request: Request = Request.Builder()
            .method("GET", null)
            .url(url)
            .build()

        val response: Response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            return@withContext null
        }

        val body: String = response.body.string()
        response.close()
        return@withContext body
    }
}