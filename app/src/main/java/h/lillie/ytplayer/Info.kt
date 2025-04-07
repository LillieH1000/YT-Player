package h.lillie.ytplayer

import org.json.JSONArray

data class Info(
    val id: String,
    val title: String,
    val author: String,
    val artwork: String,
    val live: Boolean,
    val url: String,
    val expiration: String,
    val subtitles: JSONArray?
)