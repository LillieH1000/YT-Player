package h.lillie.ytplayer

import kotlinx.serialization.Serializable

@Serializable
data class SubtitlesInfo(
    val id: String,
    val name: String,
    val url: String
)