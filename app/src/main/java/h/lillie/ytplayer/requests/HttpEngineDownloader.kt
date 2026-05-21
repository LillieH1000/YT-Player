package h.lillie.ytplayer.requests

import android.annotation.SuppressLint
import android.content.Context
import android.net.http.HttpEngine
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

@SuppressLint("NewApi")
class HttpEngineDownloader(context: Context): Downloader() {
    private val httpEngine: HttpEngine = HttpEngine.Builder(context)
        .setEnableHttp2(true)
        .setEnableQuic(true)
        .build()

    override fun execute(request: Request): Response {
        val connection: HttpURLConnection = httpEngine.openConnection(URL(request.url())) as HttpURLConnection
        connection.requestMethod = request.httpMethod()

        request.headers().forEach { (key, values) ->
            values.forEach { value ->
                connection.setRequestProperty(key, value)
            }
        }

        val data: ByteArray? = request.dataToSend()
        if (data != null) {
            connection.doOutput = true
            connection.getOutputStream().use { outputStream ->
                outputStream.write(data)
            }
        }

        val responseCode: Int = connection.responseCode
        val responseMessage: String = connection.responseMessage
        val headers = connection.headerFields
        val stream: InputStream = if (responseCode >= 400) {
            connection.errorStream
        } else {
            connection.getInputStream()
        }
        val body: String = stream.bufferedReader().use { it.readText() }
        val url: String = connection.url.toString()
        connection.disconnect()
        return Response(
            responseCode,
            responseMessage,
            headers,
            body,
            url
        )
    }
}