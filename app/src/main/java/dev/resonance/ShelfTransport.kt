package dev.resonance

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Transport returns source data; it has no database, preferences or UI state. */
interface ShelfTransport {
    fun catalog(base: String, key: String): String

    fun connection(base: String, key: String, path: String, offset: Long = 0): HttpURLConnection
}

class HttpShelfTransport : ShelfTransport {
    override fun connection(
        base: String,
        key: String,
        path: String,
        offset: Long,
    ): HttpURLConnection =
        (URL(base + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 20000
            instanceFollowRedirects = false
            setRequestProperty("Authorization", "Bearer $key")
            setRequestProperty("Accept-Encoding", "identity")
            if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
        }

    override fun catalog(base: String, key: String): String {
        val connection = connection(base, key, "/api/catalog")
        try {
            check(connection.responseCode == 200) {
                if (connection.responseCode == 401) "mac_key" else "mac_unavailable"
            }
            return connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(65536)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= MediaLimits.CATALOG_BYTES) { "capacity" }
                    output.write(buffer, 0, count)
                }
                output.toString(Charsets.UTF_8.name())
            }
        } finally {
            connection.disconnect()
        }
    }
}
