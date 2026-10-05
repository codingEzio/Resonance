package dev.resonance

import java.io.InputStream
import java.security.MessageDigest

object Fingerprint {
    fun read(input: InputStream, continueReading: () -> Boolean = { true }): Pair<String, Long> {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(256 * 1024)
        var bytes = 0L
        while (true) {
            check(continueReading()) { "cancelled" }
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
            bytes += count
        }
        return digest.digest().joinToString("") { "%02x".format(it) } to bytes
    }
}
