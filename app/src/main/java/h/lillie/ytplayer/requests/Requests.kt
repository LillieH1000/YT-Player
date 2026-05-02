package h.lillie.ytplayer.requests

import android.content.Context
import android.util.Log
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo

class Requests {
    suspend fun ytdlp(videoID: String?, searchQuery: String?): Info? = withContext(Dispatchers.IO) {
        NewPipe.init(Downloader())

        val service = NewPipe.getService(ServiceList.YouTube.serviceId)

        val test = StreamInfo.getInfo(service, "https://www.youtube.com/watch?v=${videoID}")

        return@withContext Info(
            test.id,
            test.name,
            test.uploaderName,
            "https://cdn.discordapp.com/attachments/338469594471596042/1499227651179548722/RDT_20260429_2154563781918997808338585.jpg?ex=69f6ab0e&is=69f5598e&hm=44a4ac9afd12d5666b779e660c8bec7b3eccba67885fb29566d850cc99d4d621&animated=true",
            false,
            1,
            1,
            "video",
            null,
            test.videoStreams[0].url!!,
            "t",
            "1000000000",
            null
        )
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