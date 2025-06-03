package h.lillie.ytplayer

import kotlinx.serialization.Serializable

@Serializable
data class Info(
    val id: String,
    val title: String,
    val author: String,
    val artwork: String,
    val live: Boolean,
    val url: String,
    val expiration: String,
    val subtitles: ArrayList<InfoSubs>?
)