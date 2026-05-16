package h.lillie.ytplayer.requests

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response

class Downloader: Downloader() {
    override fun execute(request: Request): Response? {
        val client: OkHttpClient = OkHttpClient.Builder().build()

        val requestBuilder: okhttp3.Request.Builder = okhttp3.Request.Builder()
            .url(request.url())

        request.headers().forEach { (key, values) ->
            values.forEach { value ->
                requestBuilder.addHeader(key, value)
            }
        }

        val data: ByteArray? = request.dataToSend()
        if (data != null) {
            requestBuilder.method(request.httpMethod(), data.toRequestBody())
        } else {
            requestBuilder.method(request.httpMethod(), null)
        }

        val response: okhttp3.Response = client.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            return null
        }

        return Response(
            response.code,
            response.message,
            response.headers.toMultimap(),
            response.body.string(),
            response.request.url.toString()
        )
    }
}