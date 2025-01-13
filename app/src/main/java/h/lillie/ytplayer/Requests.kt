package h.lillie.ytplayer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class Requests {
    suspend fun innertube(videoId: String) = withContext(Dispatchers.IO) {
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

        val client: OkHttpClient = OkHttpClient.Builder().build()

        val request = Request.Builder()
            .method("POST", body.trimIndent().toRequestBody())
            .header("User-Agent", "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)")
            .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            return@withContext
        }

        val jsonObject = JSONObject(response.body.string())
        Application.id = jsonObject.getJSONObject("videoDetails").optString("videoId")
        Application.title = jsonObject.getJSONObject("videoDetails").optString("title")
        Application.author = jsonObject.getJSONObject("videoDetails").optString("author")
        val artworkArray = jsonObject.getJSONObject("videoDetails").getJSONObject("thumbnail").getJSONArray("thumbnails")
        Application.artwork = artworkArray.getJSONObject((artworkArray.length() - 1)).optString("url")
        Application.views = jsonObject.getJSONObject("videoDetails").optString("viewCount")
        Application.live = jsonObject.getJSONObject("videoDetails").optBoolean("isLive")

        var audioInfo = 0
        var audioUrl = ""
        val adaptiveFormats = jsonObject.getJSONObject("streamingData").getJSONArray("adaptiveFormats")
        for (i in 0 until adaptiveFormats.length()) {
            if (adaptiveFormats.getJSONObject(i).optString("mimeType").contains("audio/mp4") && adaptiveFormats.getJSONObject(i).optString("audioQuality") == "AUDIO_QUALITY_HIGH" && audioInfo <= 2) {
                audioInfo = 3
                audioUrl = adaptiveFormats.getJSONObject(i).optString("url")
            }
            if (adaptiveFormats.getJSONObject(i).optString("mimeType").contains("audio/mp4") && adaptiveFormats.getJSONObject(i).optString("audioQuality") == "AUDIO_QUALITY_MEDIUM" && audioInfo <= 1) {
                audioInfo = 2
                audioUrl = adaptiveFormats.getJSONObject(i).optString("url")
            }
            if (adaptiveFormats.getJSONObject(i).optString("mimeType").contains("audio/mp4") && adaptiveFormats.getJSONObject(i).optString("audioQuality") == "AUDIO_QUALITY_LOW" && audioInfo == 0) {
                audioInfo = 1
                audioUrl = adaptiveFormats.getJSONObject(i).optString("url")
            }
        }
        Application.audioUrl = audioUrl
        Application.hlsUrl = jsonObject.getJSONObject("streamingData").optString("hlsManifestUrl")

        return@withContext
    }

    suspend fun sponsorBlock(videoId: String) = withContext(Dispatchers.IO) {
        val client: OkHttpClient = OkHttpClient.Builder().build()

        val request = Request.Builder()
            .method("GET", null)
            .url("https://sponsor.ajay.app/api/skipSegments?videoID=$videoId&categories=[\"sponsor\",\"selfpromo\",\"interaction\",\"intro\",\"outro\",\"preview\",\"music_offtopic\"]")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            Application.sponsorBlock = null
            return@withContext
        }

        try {
            val jsonArray = JSONArray(response.body.string())
            Application.sponsorBlock = jsonArray
        } catch (_: JSONException) {
            Application.sponsorBlock = null
        }

        return@withContext
    }

    suspend fun returnYouTubeDislike(videoId: String) = withContext(Dispatchers.IO) {
        val client: OkHttpClient = OkHttpClient.Builder().build()

        val request = Request.Builder()
            .method("GET", null)
            .url("https://returnyoutubedislikeapi.com/votes?videoId=$videoId")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            Application.dislikes = 0
            return@withContext
        }

        val jsonObject = JSONObject(response.body.string())
        Application.likes = jsonObject.optInt("likes")
        Application.dislikes = jsonObject.optInt("dislikes")

        return@withContext
    }
}