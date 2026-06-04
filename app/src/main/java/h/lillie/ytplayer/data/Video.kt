package h.lillie.ytplayer.data

import kotlinx.serialization.Serializable

@Serializable
data class Video(
    val url: String?,
    val indexRange: IndexRange,
    val initRange: InitRange,
    val codec: String,
    val ext: String,
    val height: Long,
    val width: Long
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