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
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.time.Duration.Companion.seconds

class Requests {
    suspend fun extractor(context: Context, videoID: String?, searchQuery: String?): Return? = withContext(Dispatchers.IO) {
        val py: Python = Python.getInstance()

        val info: YTdlp = runCatching {
            Json.decodeFromString<YTdlp>(py.getModule("ytdlp").callAttr("getInfo", "${context.applicationInfo.nativeLibraryDir}/libqjs.so", videoID, searchQuery).toString())
        }.getOrNull() ?: return@withContext null

        val base: String = buildString {
            append("""
                <?xml version="1.0" encoding="UTF-8"?>
                <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" profiles="urn:mpeg:dash:profile:isoff-on-demand:2011" type="static" mediaPresentationDuration="${(info.duration ?: 0).seconds.toIsoString()}" minBufferTime="PT2S">
                    <Period start="PT0S">
            """.trimIndent())

            append("\n")
            var count: Long = 0

            info.video?.forEach { video ->
                append("""
                    <AdaptationSet id="$count" contentType="video" mimeType="video/mp4" segmentAlignment="true" startWithSAP="1">
                        <Representation id="$count" bandwidth="3000000" width="${video.width}" height="${video.height}" codecs="${video.codec}">
                            <BaseURL>${video.url.replace("&", "&amp;")}</BaseURL>
                            <SegmentBase indexRange="${video.indexRange.start}-${video.indexRange.end}">
                                <Initialization range="${video.initRange.start}-${video.initRange.end}" />
                            </SegmentBase>
                        </Representation>
                    </AdaptationSet>
                """.trimIndent().prependIndent("\t\t"))
                append("\n")
                count++
            }

            info.audio?.forEach { audio ->
                append("""
                    <AdaptationSet id="$count" contentType="audio" mimeType="audio/m4a" segmentAlignment="true" startWithSAP="1">
                        <Representation id="$count" bandwidth="128000" audioSamplingRate="48000" codecs="${audio.codec}">
                            <BaseURL>${audio.url.replace("&", "&amp;")}</BaseURL>
                            <SegmentBase indexRange="${audio.indexRange.start}-${audio.indexRange.end}">
                                <Initialization range="${audio.initRange.start}-${audio.initRange.end}" />
                            </SegmentBase>
                        </Representation>
                    </AdaptationSet>
                """.trimIndent().prependIndent("\t\t"))
                append("\n")
                count++
            }

            info.subtitles?.forEach { subtitle ->
                append("""
                    <AdaptationSet contentType="text" mimeType="text/vtt" lang="${subtitle.id}">
                        <Role schemeIdUri="urn:mpeg:dash:role:2011" value="subtitle" />
                        <Representation id="caption_${subtitle.id}" bandwidth="256">
                            <BaseURL>${subtitle.url.replace("&", "&amp;")}</BaseURL>
                        </Representation>
                    </AdaptationSet>
                """.trimIndent().prependIndent("\t\t"))
                append("\n")
            }

            append("""
                    </Period>
                </MPD>
            """.trimIndent())
        }

        val manifest = File(context.filesDir, "manifest.mpd")
        manifest.writeText(base)

        return@withContext Return(
            info.id,
            info.title,
            info.author,
            info.artwork,
            info.live,
            info.views,
            info.likes,
            info.type,
            info.hls?.url,
            info.hls?.expiration,
            info.subtitles,
            manifest.absolutePath
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