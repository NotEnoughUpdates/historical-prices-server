package dev.dediamondpro.priceserver.utils

import java.io.InputStream
import java.net.URL
import java.util.zip.GZIPInputStream
import javax.net.ssl.HttpsURLConnection

object NetworkUtils {

    fun getGzipInputStream(url: String): InputStream {
        val con = setupConnection(URL(url))
        return GZIPInputStream(con.inputStream)
    }

    fun getInputStream(url: String): InputStream {
        val con = setupConnection(URL(url))
        con.setRequestProperty("Accept-Encoding", "gzip")
        val inputStream = con.inputStream
        return if (con.contentEncoding == "gzip") GZIPInputStream(inputStream) else inputStream
    }

    private fun setupConnection(url: URL): HttpsURLConnection {
        val con = url.openConnection() as HttpsURLConnection
        con.setRequestProperty(
            "User-Agent",
            "Graph Server-1.0.0"
        )
        con.connectTimeout = 5000
        con.readTimeout = 5000
        return con
    }
}