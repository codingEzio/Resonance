package dev.resonance

import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max

/** Eight seconds of 2 ms PCM peaks (~48 KiB), regardless of media duration or decoder lead. */
internal class PcmEnvelope {
    enum class Encoding(val bytes: Int) {
        U8(1),
        S16(2),
        S24(3),
        S32(4),
        FLOAT(4),
    }

    private val stamps = LongArray(4096) { Long.MIN_VALUE }
    private val peaks = FloatArray(stamps.size)
    private var latest = Long.MIN_VALUE

    @Synchronized
    fun clear() {
        stamps.fill(Long.MIN_VALUE)
        peaks.fill(0f)
        latest = Long.MIN_VALUE
    }

    @Synchronized
    fun append(buffer: ByteBuffer, timeUs: Long, rate: Int, channels: Int, encoding: Encoding) {
        if (rate <= 0 || channels !in 1..32 || timeUs < 0) return
        val frameBytes = encoding.bytes * channels
        val frames = buffer.remaining() / frameBytes
        val start = buffer.position()
        for (frame in 0 until frames) {
            var peak = 0f
            for (channel in 0 until channels) {
                val offset = start + frame * frameBytes + channel * encoding.bytes
                fun byte(index: Int) = buffer.get(offset + index).toInt() and 255
                // Media3 raw PCM is little endian. Absolute reads leave playback untouched.
                val sample =
                    when (encoding) {
                        Encoding.U8 -> (byte(0) - 128) / 128f
                        Encoding.S16 -> ((byte(0) or (byte(1) shl 8)).toShort().toInt()) / 32768f
                        Encoding.S24 ->
                            ((byte(0) or (byte(1) shl 8) or (byte(2) shl 16)) shl 8 shr 8) /
                                8388608f
                        Encoding.S32 ->
                            (byte(0) or (byte(1) shl 8) or (byte(2) shl 16) or (byte(3) shl 24)) /
                                2147483648f
                        Encoding.FLOAT ->
                            Float.fromBits(
                                byte(0) or (byte(1) shl 8) or (byte(2) shl 16) or (byte(3) shl 24)
                            )
                    }
                // Max across channels retains antiphase stereo instead of cancelling it.
                if (sample.isFinite()) peak = max(peak, abs(sample).coerceAtMost(1f))
            }
            val stamp = (timeUs + frame.toLong() * 1_000_000 / rate) / 2000
            val slot = (stamp % stamps.size).toInt()
            if (stamps[slot] != stamp) {
                stamps[slot] = stamp
                peaks[slot] = 0f
            }
            peaks[slot] = max(peaks[slot], peak)
            latest = max(latest, stamp)
        }
    }

    @Synchronized
    fun read(endUs: Long, windowUs: Long, output: FloatArray) {
        output.fill(0f)
        if (output.isEmpty() || endUs < 0 || windowUs <= 0) return
        val startUs = endUs - windowUs + 1
        for (column in output.indices) {
            val first = (startUs + windowUs * column / output.size).coerceAtLeast(0) / 2000
            val lastUs = startUs + windowUs * (column + 1) / output.size - 1
            if (lastUs < 0) continue
            val last = lastUs / 2000
            // Bound work even if a caller asks for more history than retained.
            for (stamp in max(first, latest - stamps.size + 1)..last.coerceAtMost(latest)) {
                val slot = (stamp % stamps.size).toInt()
                if (stamps[slot] == stamp) output[column] = max(output[column], peaks[slot])
            }
        }
    }
}
