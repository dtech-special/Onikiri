package com.onikiri.open.data

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

internal object Http {
    private const val USER_AGENT = "Onikiri/1.0 (Android)"

    fun open(
        url: String,
        accept: String? = null,
        headers: Map<String, String> = emptyMap(),
        readTimeoutMs: Int = 30_000
    ): HttpURLConnection {
        require(url.startsWith("https://") || url.startsWith("http://")) { "Only http(s) URLs are allowed: $url" }
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = readTimeoutMs
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", USER_AGENT)
        if (accept != null) connection.setRequestProperty("Accept", accept)
        for ((key, value) in headers) connection.setRequestProperty(key, value)
        return connection
    }

    @Throws(IOException::class)
    fun readLimited(input: InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (output.size() + read > maxBytes) throw IOException("Response is larger than $maxBytes bytes")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
