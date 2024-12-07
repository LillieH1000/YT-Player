package h.lillie.ytplayer

import android.content.Intent
import android.os.Bundle
import android.os.StrictMode
import androidx.appcompat.app.AppCompatActivity
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class Main : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when {
            intent?.action == Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    val youtubeRegex = Regex("^.*(?:(?:youtu\\.be\\/|v\\/|vi\\/|u\\/\\w\\/|embed\\/|shorts\\/|live\\/)|(?:(?:watch)?\\?v(?:i)?=|\\&v(?:i)?=))([^#\\&\\?]*).*")
                    if (youtubeRegex.containsMatchIn(intent.getStringExtra(Intent.EXTRA_TEXT)!!)) {
                        val result = youtubeRegex.findAll(intent.getStringExtra(Intent.EXTRA_TEXT)!!).map { it.groupValues[1] }.joinToString()

                        val policy = StrictMode.ThreadPolicy.Builder().permitNetwork().build()
                        StrictMode.setThreadPolicy(policy)

                        val body = """{
                            "context": {
                                "client": {
                                    "hl": "en",
                                    "gl": "${this.resources.configuration.locales.get(0).country}",
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
                            "videoId": "$result"
                        }"""

                        val requestBody = body.trimIndent().toRequestBody()

                        val client: OkHttpClient = OkHttpClient.Builder().build()

                        val request = Request.Builder()
                            .method("POST", requestBody)
                            .header("User-Agent", "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)")
                            .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                            .build()

                        val jsonObject = JSONObject(client.newCall(request).execute().body.string())

                        Application.title = jsonObject.getJSONObject("videoDetails").optString("title")
                        Application.author = jsonObject.getJSONObject("videoDetails").optString("author")
                        val artworkArray = jsonObject.getJSONObject("videoDetails").getJSONObject("thumbnail").getJSONArray("thumbnails")
                        Application.artwork = artworkArray.getJSONObject((artworkArray.length() - 1)).optString("url")
                        Application.url = jsonObject.getJSONObject("streamingData").optString("hlsManifestUrl")

                        startActivity(Intent(this, Player::class.java))
                    }
                }
            }
        }
    }
}