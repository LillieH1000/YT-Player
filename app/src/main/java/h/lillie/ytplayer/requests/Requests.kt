package h.lillie.ytplayer.requests

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.File
import kotlin.time.Duration.Companion.seconds

class Requests {
    suspend fun extractor(context: Context, videoID: String?, searchQuery: String?): Info = withContext(Dispatchers.IO) {
        NewPipe.init(Downloader())
        val service = NewPipe.getService(ServiceList.YouTube.serviceId)
        val info = StreamInfo.getInfo(service, "https://www.youtube.com/watch?v=${videoID}")

        val videoStream: VideoStream = info.videoOnlyStreams.maxWith(
            compareBy<VideoStream> { it.height }
                .thenBy { it.codec.contains("vp9") }
        )

        val audioStream: AudioStream = info.audioStreams.maxWith(
            compareBy<AudioStream> { it.bitrate }
                .thenBy { it.codec.contains("opus") }
        )

        val manifest = File(context.filesDir, "manifest.mpd")
        manifest.writeText("""
            <?xml version="1.0" encoding="UTF-8"?>
            <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" profiles="urn:mpeg:dash:profile:isoff-on-demand:2011" type="static" mediaPresentationDuration="${info.duration.seconds.toIsoString()}" minBufferTime="PT2.0S">
              <Period>
                <AdaptationSet mimeType="${videoStream.format?.mimeType}" segmentAlignment="true" startWithSAP="1">
                  <Representation id="video" bandwidth="3000000" width="3840" height="2160" codecs="${videoStream.codec}">
                    <BaseURL>${videoStream.url!!.replace("&", "&amp;")}</BaseURL>
                  </Representation>
                </AdaptationSet>
                <AdaptationSet mimeType="${audioStream.format?.mimeType}" segmentAlignment="true" startWithSAP="1">
                  <Representation id="audio" bandwidth="128000" audioSamplingRate="48000" codecs="${audioStream.codec}">
                    <BaseURL>${audioStream.url!!.replace("&", "&amp;")}</BaseURL>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent())

        return@withContext Info(
            info.id,
            info.name,
            info.uploaderName,
            info.thumbnails.maxWith(
                compareBy<Image> { it.height }
                    .thenBy { it.width }
            ).url,
            info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM,
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