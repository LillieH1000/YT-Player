package h.lillie.ytplayer

import android.app.Application
import android.os.StrictMode
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class Application : Application() {
    companion object {
        var id = String()
        var title = String()
        var author = String()
        var artwork = String()
        var views = String()
        var likes: Int = 0
        var dislikes: Int = 0
        var live: Boolean = false
        var audioUrl = String()
        var hlsUrl = String()
        var sponsorBlock: JSONArray? = JSONArray()
        var castActive: Boolean = false
        fun requests(videoId: String) {
            val policy = StrictMode.ThreadPolicy.Builder().permitNetwork().build()
            StrictMode.setThreadPolicy(policy)

            h.lillie.ytplayer.Application().innertube(videoId)
            h.lillie.ytplayer.Application().sponsorBlock(videoId)
            h.lillie.ytplayer.Application().returnYouTubeDislike(videoId)
        }
    }

    private fun innertube(videoId: String) {
        val body = """{
                "context": {
                    "client": {
                        "hl": "en",
                        "gl": "CA",
                        "clientName": "IOS",
                        "clientVersion": "19.45.4",
                        "deviceMake": "Apple",
                        "deviceModel": "iPhone16,2",
                        "osName": "iPhone",
                        "osVersion": "18.1.0.22B83"
                    }
                },
                "contentCheckOk": true,
                "racyCheckOk": true,
                "videoId": "$videoId"
            }"""

        val requestBody = body.trimIndent().toRequestBody()

        val client: OkHttpClient = OkHttpClient.Builder().build()

        val request = Request.Builder()
            .method("POST", requestBody)
            .header("User-Agent", "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)")
            .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
            .build()

        val jsonObject = JSONObject(client.newCall(request).execute().body.string())

        id = jsonObject.getJSONObject("videoDetails").optString("videoId")
        title = jsonObject.getJSONObject("videoDetails").optString("title")
        author = jsonObject.getJSONObject("videoDetails").optString("author")
        val artworkArray = jsonObject.getJSONObject("videoDetails").getJSONObject("thumbnail").getJSONArray("thumbnails")
        artwork = artworkArray.getJSONObject((artworkArray.length() - 1)).optString("url")
        views = jsonObject.getJSONObject("videoDetails").optString("viewCount")
        live = jsonObject.getJSONObject("videoDetails").optBoolean("isLive")

        var audioInfo = 0
        var audioUrlInner = ""
        val adaptiveFormats = jsonObject.getJSONObject("streamingData").getJSONArray("adaptiveFormats")
        for (i in 0 until adaptiveFormats.length()) {
            if (adaptiveFormats.getJSONObject(i).optString("mimeType").contains("audio/mp4") && adaptiveFormats.getJSONObject(i).optString("audioQuality") == "AUDIO_QUALITY_HIGH" && audioInfo <= 2) {
                audioInfo = 3
                audioUrlInner = adaptiveFormats.getJSONObject(i).optString("url")
            }
            if (adaptiveFormats.getJSONObject(i).optString("mimeType").contains("audio/mp4") && adaptiveFormats.getJSONObject(i).optString("audioQuality") == "AUDIO_QUALITY_MEDIUM" && audioInfo <= 1) {
                audioInfo = 2
                audioUrlInner = adaptiveFormats.getJSONObject(i).optString("url")
            }
            if (adaptiveFormats.getJSONObject(i).optString("mimeType").contains("audio/mp4") && adaptiveFormats.getJSONObject(i).optString("audioQuality") == "AUDIO_QUALITY_LOW" && audioInfo == 0) {
                audioInfo = 1
                audioUrlInner = adaptiveFormats.getJSONObject(i).optString("url")
            }
        }
        audioUrl = audioUrlInner
        hlsUrl = jsonObject.getJSONObject("streamingData").optString("hlsManifestUrl")
    }

    private fun sponsorBlock(videoId: String) {
        val client: OkHttpClient = OkHttpClient.Builder().build()

        val request = Request.Builder()
            .method("GET", null)
            .url("https://sponsor.ajay.app/api/skipSegments?videoID=$videoId&categories=[\"sponsor\",\"selfpromo\",\"interaction\",\"intro\",\"outro\",\"preview\",\"music_offtopic\"]")
            .build()

        try {
            val jsonArray = JSONArray(client.newCall(request).execute().body.string())
            sponsorBlock = jsonArray
        } catch (_: JSONException) {
            sponsorBlock = null
        }
    }

    private fun returnYouTubeDislike(videoId: String) {
        val client: OkHttpClient = OkHttpClient.Builder().build()

        val request = Request.Builder()
            .method("GET", null)
            .url("https://returnyoutubedislikeapi.com/votes?videoId=$videoId")
            .build()

        val jsonObject = JSONObject(client.newCall(request).execute().body.string())

        likes = jsonObject.optInt("likes")
        dislikes = jsonObject.optInt("dislikes")
    }
}