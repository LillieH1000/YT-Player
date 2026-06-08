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

        if (info.live && info.hlsUrl != null) return@withContext Return(
            info.id,
            info.title,
            info.author,
            info.artwork,
            true,
            info.views,
            info.likes,
            info.type,
            info.hlsUrl,
            info.expiration,
            info.subtitles,
            null
        )

        val base: String = buildString {
            append("""
                <?xml version="1.0" encoding="UTF-8"?>
                <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" profiles="urn:mpeg:dash:profile:isoff-on-demand:2011" type="static" mediaPresentationDuration="${info.duration!!.seconds.toIsoString()}" minBufferTime="PT2S">
                    <Period id="0" start="PT0S">
                        <AdaptationSet id="0" contentType="video" mimeType="video/${info.video!!.ext}" segmentAlignment="true" startWithSAP="1">
                            <Representation id="0" bandwidth="3000000" width="${info.video.width}" height="${info.video.height}" codecs="${info.video.codec}">
                                <BaseURL>${info.video.url.replace("&", "&amp;")}</BaseURL>
                                <SegmentBase indexRange="${info.video.indexRange.start}-${info.video.indexRange.end}">
                                    <Initialization range="${info.video.initRange.start}-${info.video.initRange.end}" />
                                </SegmentBase>
                            </Representation>
                        </AdaptationSet>
                        <AdaptationSet id="1" contentType="audio" mimeType="audio/${info.audio!!.ext}" segmentAlignment="true" startWithSAP="1">
                            <Representation id="1" bandwidth="128000" audioSamplingRate="48000" codecs="${info.audio.codec}">
                                <BaseURL>${info.audio.url.replace("&", "&amp;")}</BaseURL>
                                <SegmentBase indexRange="${info.audio.indexRange.start}-${info.audio.indexRange.end}">
                                    <Initialization range="${info.audio.initRange.start}-${info.audio.initRange.end}" />
                                </SegmentBase>
                            </Representation>
                        </AdaptationSet>
            """.trimIndent())

            append("\n")

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
            info.hlsUrl,
            info.expiration,
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