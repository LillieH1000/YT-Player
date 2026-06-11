package h.lillie.ytplayer.data

import kotlinx.serialization.Serializable

@Serializable
data class Video(
    val codec: String,
    val height: Long,
    val width: Long,
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