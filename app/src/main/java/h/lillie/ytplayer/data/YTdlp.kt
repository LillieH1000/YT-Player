package h.lillie.ytplayer.data

import kotlinx.serialization.Serializable

@Serializable
data class YTdlp(
    val id: String,
    val title: String,
    val author: String,
    val artwork: String,
    val live: Boolean,
    val views: Long,
    val likes: Long,
    val type: String,
    val expiration: String,
    val duration: Long?,
    val hlsUrl: String?,
    val video: ArrayList<Video>?,
    val audio: ArrayList<Audio>?,
    val subtitles: ArrayList<Subtitles>?
)