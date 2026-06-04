package h.lillie.ytplayer.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class Subtitles(
    val id: String,
    val name: String,
    val url: String
): Parcelable