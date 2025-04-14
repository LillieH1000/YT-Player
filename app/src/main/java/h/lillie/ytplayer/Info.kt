package h.lillie.ytplayer

data class Info(
    val id: String,
    val title: String,
    val author: String,
    val artwork: String,
    val live: Boolean,
    val url: String,
    val subtitles: ArrayList<InfoSubs>?
)