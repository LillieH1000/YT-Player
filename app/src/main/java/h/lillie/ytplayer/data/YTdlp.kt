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
    val hlsUrl: String?,
    val expiration: String,
    val subtitles: ArrayList<Subtitles>?,
    val videoUrl: String?,
    val videoIndexStart: String?,
    val videoIndexEnd: String?,
    val videoInitStart: String?,
    val videoInitEnd: String?,
    val videoCodec: String?,
    val videoExt: String?,
    val videoHeight: Long?,
    val videoWidth: Long?,
    val videoDuration: Long?,
    val audioUrl: String?,
    val audioIndexStart: String?,
    val audioIndexEnd: String?,
    val audioInitStart: String?,
    val audioInitEnd: String?,
    val audioCodec: String?,
    val audioExt: String?
)
