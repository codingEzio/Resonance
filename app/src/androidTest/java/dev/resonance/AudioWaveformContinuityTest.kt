package dev.resonance

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

/** In-memory PCM only: no sound, files, microphone, network or personal library. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AudioWaveformContinuityTest {
    private val format =
        Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .setSampleRate(1000)
            .setChannelCount(1)
            .build()

    private fun tone() =
        ByteBuffer.allocate(400).order(ByteOrder.LITTLE_ENDIAN).apply {
            repeat(200) { putShort(16384) }
            flip()
        }

    @Test
    fun openingAPlayerAfterDecodingKeepsTheBufferedSignal() {
        AudioWaveform.reset()
        AudioWaveform.playback(false)
        val buffer = tone()
        AudioWaveform.append(buffer, 1_000_000, format)
        AudioWaveform.clock(1_150_000)
        val observer = AudioWaveform.observe()
        try {
            val columns = FloatArray(35)
            AudioWaveform.read(columns)
            assertTrue("Opening a view must retain decoded PCM", columns.any { it >= .5f })
            assertEquals("PCM tap leaves the sink buffer untouched", 0, buffer.position())
        } finally {
            observer.close()
            AudioWaveform.reset()
        }
    }

    @Test
    fun replacingOneVisiblePlayerWithAnotherDoesNotClearAudio() {
        AudioWaveform.reset()
        AudioWaveform.playback(false)
        val first = AudioWaveform.observe()
        AudioWaveform.append(tone(), 1_000_000, format)
        AudioWaveform.clock(1_150_000)
        first.close()
        val second = AudioWaveform.observe()
        try {
            val columns = FloatArray(35)
            AudioWaveform.read(columns)
            assertTrue(
                "Navigation must not reset the shared audio history",
                columns.any { it >= .5f },
            )
            AudioWaveform.reset()
            AudioWaveform.read(columns)
            assertTrue("A seek/renderer reset must still clear old audio", columns.all { it == 0f })
        } finally {
            second.close()
            AudioWaveform.reset()
        }
    }
}
