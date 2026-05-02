package h.lillie.ytplayer.requests

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.File

class Requests {
    suspend fun extractor(context: Context, videoID: String?, searchQuery: String?): Info = withContext(Dispatchers.IO) {
        NewPipe.init(Downloader())
        val service = NewPipe.getService(ServiceList.YouTube.serviceId)
        val info = StreamInfo.getInfo(service, "https://www.youtube.com/watch?v=${videoID}")

        var videoUrl = ""
        info.videoOnlyStreams.forEach { stream ->
            Log.d("TTTTTTTT", "${stream.height} / ${stream.width} / ${stream.codec}")
            if (videoUrl == "" && stream.codec == "vp9") {
                Log.d("TTTTTTTT", stream.url!!)
                videoUrl = stream.url!!
            }
        }

        var audioUrl = ""
        info.audioStreams.forEach { stream ->
            Log.d("TTTTTTTT", "${stream.quality} / ${stream.bitrate} / ${stream.codec}")
            if (audioUrl == "" && stream.codec == "opus") {
                Log.d("TTTTTTTT", stream.url!!)
                audioUrl = stream.url!!
            }
        }

        val manifest = File(context.filesDir, "manifest.mpd")
        manifest.writeText("""
            <?xml version="1.0" encoding="UTF-8"?>
            <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" profiles="urn:mpeg:dash:profile:isoff-on-demand:2011" type="static" mediaPresentationDuration="PT3M9.299S" minBufferTime="PT2.0S">
              <Period>
                <AdaptationSet mimeType="video/webm" segmentAlignment="true" startWithSAP="1">
                  <Representation id="video" bandwidth="3000000" width="3840" height="2160" codecs="vp9">
                    <BaseURL>${videoUrl.replace("&", "&amp;")}</BaseURL>
                  </Representation>
                </AdaptationSet>
                <AdaptationSet mimeType="audio/webm" segmentAlignment="true" startWithSAP="1">
                  <Representation id="audio" bandwidth="128000" audioSamplingRate="48000" codecs="opus">
                    <BaseURL>${audioUrl.replace("&", "&amp;")}</BaseURL>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent())

        return@withContext Info(
            info.id,
            info.name,
            info.uploaderName,
            "https://cdn.discordapp.com/attachments/338469594471596042/1499227651179548722/RDT_20260429_2154563781918997808338585.jpg?ex=69f6ab0e&is=69f5598e&hm=44a4ac9afd12d5666b779e660c8bec7b3eccba67885fb29566d850cc99d4d621&animated=true",
            false,
            info.viewCount,
            info.likeCount,
            "video",
            manifest.absolutePath,
            null,
            "1000000000",
            null
        )
    }

    suspend fun returnYouTubeDislike(videoID: String): Long? = withContext(Dispatchers.IO) {
        val client: OkHttpClient = OkHttpClient.Builder().build()

        val request: Request = Request.Builder()
            .method("GET", null)
            .url("https://returnyoutubedislikeapi.com/votes?videoId=$videoID")
            .build()

        val response: Response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            return@withContext null
        }

        return@withContext JSONObject(response.body.string()).getLong("dislikes")
    }

    suspend fun sponsorBlock(videoID: String): JSONArray? = withContext(Dispatchers.IO) {
        val client: OkHttpClient = OkHttpClient.Builder().build()

        val request: Request = Request.Builder()
            .method("GET", null)
            .url("https://sponsor.ajay.app/api/skipSegments?videoID=$videoID&category=sponsor")
            .build()

        val response: Response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            return@withContext null
        }

        return@withContext JSONArray(response.body.string())
    }
}