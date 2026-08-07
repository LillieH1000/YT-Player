package h.lillie.ytplayer.data

import kotlinx.serialization.Serializable

@Serializable
data class Return(
    val id: String,
    val title: String,
    val author: String,
    val artwork: String,
    val thumbnail: String,
    val description: String,
    val live: Boolean,
    val views: Long,
    val likes: Long,
    val type: String,
    val hlsUrl: String?,
    val expiration: Long?,
    val availability: Long,
    val subtitles: ArrayList<Subtitles>?,
    val manifestPath: String?
)