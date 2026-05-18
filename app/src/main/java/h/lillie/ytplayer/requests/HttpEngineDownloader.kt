package h.lillie.ytplayer.requests

import android.annotation.SuppressLint
import android.content.Context
import android.net.http.HttpEngine
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.net.HttpURLConnection
import java.net.URL

class HttpEngineDownloader(val context: Context): Downloader() {
    @SuppressLint("NewApi")
    override fun execute(request: Request): Response? {
        val httpEngine: HttpEngine = HttpEngine.Builder(context)
            .setEnableHttp2(true)
            .setEnableQuic(true)
            .build()

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
        if (responseCode != 200) {
            connection.disconnect()
            return null
        }

        val responseMessage: String = connection.responseMessage
        val headers = connection.headerFields
        val body: String = connection.getInputStream().bufferedReader().use { it.readText() }
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