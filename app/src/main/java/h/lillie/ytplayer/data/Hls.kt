package h.lillie.ytplayer.data

import kotlinx.serialization.Serializable

@Serializable
data class Hls(
    val expiration: Long,
    val url: String
)