package h.lillie.ytplayer.requests

import kotlinx.serialization.Serializable

@Serializable
data class Info(
    val id: String,
    val title: String,
    val author: String,
    val artwork: String,
    val live: Boolean,
    val views: Int,
    val likes: Int,
    val type: String,
    val videourl: String?,
    val audiourl: String?,
    val streamurl: String?,
    val agent: String,
    val expiration: String,
    val subtitles: ArrayList<Subtitles>?
) {
    @Serializable
    data class Subtitles(
        val id: String,
        val name: String,
        val url: String
    )
}