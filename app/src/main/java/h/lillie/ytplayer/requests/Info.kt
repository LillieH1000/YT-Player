package h.lillie.ytplayer.requests

import kotlinx.serialization.Serializable

@Serializable
data class Info(
    val id: String,
    val title: String,
    val author: String,
    val artwork: String,
    val live: Boolean,
    val views: Long,
    val likes: Long,
    val type: String,
    val manifestPath: String?,
    val hlsUrl: String?,
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