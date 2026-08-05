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
    val duration: Long?,
    val video: ArrayList<Video>?,
    val audio: ArrayList<Audio>?,
    val hls: Hls?,
    val availability: Long,
    val subtitles: ArrayList<Subtitles>?
)