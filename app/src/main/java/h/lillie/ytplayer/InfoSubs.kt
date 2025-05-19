package h.lillie.ytplayer

import kotlinx.serialization.Serializable

@Serializable
data class InfoSubs(
    val id: String,
    val name: String,
    val url: String
)