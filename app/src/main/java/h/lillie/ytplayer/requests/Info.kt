package h.lillie.ytplayer.requests

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
data class Info(
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
    val subtitles: ArrayList<Subtitles>?
) {
    @Serializable
    data class Return(
        val info: Info,
        val manifestPath: String
    )
    @Serializable
    @Parcelize
    data class Subtitles(
        val name: String,
        val tag: String
    ): Parcelable
    @Serializable
    data class YTdlp(
        val info: Info,
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
}