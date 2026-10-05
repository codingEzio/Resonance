package dev.resonance

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class PcmEnvelopeTest {
    @Test
    fun capturesAntiphaseStereoWithoutChangingAudioBuffer() {
        val pcm = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        pcm.position(4)
        repeat(2) {
            pcm.putShort(16384)
            pcm.putShort(-16384)
        }
        pcm.flip()
        pcm.position(4)
        val envelope = PcmEnvelope()
        envelope.append(pcm, 0, 1000, 2, PcmEnvelope.Encoding.S16)
        assertEquals(4, pcm.position())
        assertEquals(12, pcm.limit())
        val values = FloatArray(1)
        envelope.read(1999, 2000, values)
        assertEquals(.5f, values[0], .0001f)
    }

    @Test
    fun neverShowsDecodedFutureAndClearsDiscontinuities() {
        val envelope = PcmEnvelope()
        envelope.append(
            ByteBuffer.wrap(byteArrayOf(0, 127)),
            10_000,
            1000,
            1,
            PcmEnvelope.Encoding.S16,
        )
        val values = FloatArray(4)
        envelope.read(9999, 8000, values)
        assertTrue(values.all { it == 0f })
        envelope.read(11999, 8000, values)
        assertTrue(values.last() > .9f)
        envelope.clear()
        envelope.read(11999, 8000, values)
        assertTrue(values.all { it == 0f })
    }

    @Test
    fun supportsPcmEncodingsAndBoundsNonFiniteFloat() {
        val cases =
            listOf(
                PcmEnvelope.Encoding.U8 to byteArrayOf(192.toByte()),
                PcmEnvelope.Encoding.S16 to byteArrayOf(0, 64),
                PcmEnvelope.Encoding.S24 to byteArrayOf(0, 0, 64),
                PcmEnvelope.Encoding.S32 to byteArrayOf(0, 0, 0, 64),
                PcmEnvelope.Encoding.FLOAT to
                    ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(.5f).array(),
            )
        cases.forEach { (encoding, data) ->
            val envelope = PcmEnvelope()
            envelope.append(ByteBuffer.wrap(data), 0, 1000, 1, encoding)
            val values = FloatArray(1)
            envelope.read(1999, 2000, values)
            assertEquals(encoding.name, .5f, values[0], .0001f)
        }
        val envelope = PcmEnvelope()
        val pcm =
            ByteBuffer.allocate(12)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putFloat(Float.NaN)
                .putFloat(Float.POSITIVE_INFINITY)
                .putFloat(2f)
        pcm.flip()
        envelope.append(pcm, 0, 1000, 1, PcmEnvelope.Encoding.FLOAT)
        val values = FloatArray(2)
        envelope.read(3999, 4000, values)
        assertArrayEquals(floatArrayOf(0f, 1f), values, 0f)
    }

    @Test
    fun silencePartialFramesAndInvalidFormatsAreSafe() {
        val envelope = PcmEnvelope()
        envelope.append(
            ByteBuffer.wrap(byteArrayOf(0, 0, 127)),
            0,
            1000,
            1,
            PcmEnvelope.Encoding.S16,
        )
        envelope.append(ByteBuffer.wrap(byteArrayOf(127, 127)), 0, 0, 1, PcmEnvelope.Encoding.S16)
        val values = FloatArray(1)
        envelope.read(1999, 2000, values)
        assertEquals(0f, values[0], 0f)
    }

    @Test
    fun boundedHistoryEvictsOldTimesInsteadOfAliasing() {
        val envelope = PcmEnvelope()
        envelope.append(ByteBuffer.wrap(byteArrayOf(0, 64)), 0, 1000, 1, PcmEnvelope.Encoding.S16)
        envelope.append(
            ByteBuffer.wrap(byteArrayOf(0, 32)),
            20_000_000,
            1000,
            1,
            PcmEnvelope.Encoding.S16,
        )
        val values = FloatArray(1)
        envelope.read(20_001_999, 2000, values)
        assertEquals(.25f, values[0], .0001f)
        envelope.read(1999, 2000, values)
        assertEquals(0f, values[0], 0f)
    }
}
