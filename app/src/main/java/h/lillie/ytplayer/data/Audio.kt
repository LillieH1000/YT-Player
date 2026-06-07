package h.lillie.ytplayer.data

import kotlinx.serialization.Serializable

@Serializable
data class Audio(
    val codec: String,
    val indexRange: IndexRange,
    val initRange: InitRange,
    val url: String
) {
    @Serializable
    data class IndexRange(
        val start: String,
        val end: String
    )
    @Serializable
    data class InitRange(
        val start: String,
        val end: String
    )
}