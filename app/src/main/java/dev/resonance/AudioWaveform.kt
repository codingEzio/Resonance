package dev.resonance

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer

/** In-process signal only: no microphone, audio recording, files, or permission prompt. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object AudioWaveform {
    private val envelope = PcmEnvelope()
    @Volatile private var readers = 0
    private var positionUs = AudioSink.CURRENT_POSITION_NOT_SET
    private var clockNanos = 0L
    private var speed = 1f
    private var advancing = false
    @Volatile private var capturedFrames = 0L
    @Volatile private var latestCapturedUs = -1L

    @Synchronized
    fun observe(): AutoCloseable {
        readers++
        var closed = false
        return AutoCloseable {
            synchronized(this) {
                if (!closed) readers = (readers - 1).coerceAtLeast(0)
                closed = true
            }
        }
    }

    fun append(buffer: ByteBuffer, timeUs: Long, format: Format) {
        // PCM may already be queued in the sink when a player view becomes visible.
        // Keep the fixed-size signal history independent of UI observation. No extra
        // decoding, audio files or background animation is started by this tap.
        val encoding =
            when (format.pcmEncoding) {
                C.ENCODING_PCM_8BIT -> PcmEnvelope.Encoding.U8
                C.ENCODING_PCM_16BIT -> PcmEnvelope.Encoding.S16
                C.ENCODING_PCM_24BIT -> PcmEnvelope.Encoding.S24
                C.ENCODING_PCM_32BIT -> PcmEnvelope.Encoding.S32
                C.ENCODING_PCM_FLOAT -> PcmEnvelope.Encoding.FLOAT
                else -> return
            }
        envelope.append(buffer, timeUs, format.sampleRate, format.channelCount, encoding)
        if (format.sampleRate > 0 && format.channelCount > 0) {
            val frames = buffer.remaining() / (encoding.bytes * format.channelCount)
            capturedFrames += frames
            latestCapturedUs = timeUs + frames.toLong() * 1_000_000 / format.sampleRate
        }
    }

    @Synchronized
    fun clock(positionUs: Long) {
        if (positionUs == AudioSink.CURRENT_POSITION_NOT_SET) return
        this.positionUs = positionUs
        clockNanos = System.nanoTime()
    }

    @Synchronized
    fun playback(advancing: Boolean) {
        this.advancing = advancing
    }

    @Synchronized
    fun speed(value: Float) {
        speed = value
    }

    @Synchronized
    fun reset() {
        positionUs = AudioSink.CURRENT_POSITION_NOT_SET
        envelope.clear()
        capturedFrames = 0L
        latestCapturedUs = -1L
    }

    /** Read-only, aggregate proof for debug catalog checks. Never exports audio samples. */
    @Synchronized
    fun diagnostics(): Map<String, Number> {
        if (!BuildConfig.DEBUG) return emptyMap()
        val columns = FloatArray(35)
        read(columns)
        return mapOf(
            "readers" to readers,
            "capturedFrames" to capturedFrames,
            "latestCapturedUs" to latestCapturedUs,
            "outputPositionUs" to positionUs,
            "visiblePeak" to (columns.maxOrNull() ?: 0f),
        )
    }

    @Synchronized
    fun read(output: FloatArray): Boolean {
        if (positionUs == AudioSink.CURRENT_POSITION_NOT_SET) {
            output.fill(0f)
            return false
        }
        // AudioSink reports the hardware output head, not the decoder's future position.
        // Interpolate only between fresh sink clock updates, never across a stalled renderer.
        val elapsedUs = ((System.nanoTime() - clockNanos) / 1000L).coerceIn(0, 50_000)
        val nowUs = positionUs + if (advancing) (elapsedUs * speed).toLong() else 0L
        envelope.read(nowUs, 280_000, output)
        return latestCapturedUs >= nowUs - 280_000 && capturedFrames > 0
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class WaveformRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioOutputPlaybackParams: Boolean,
    ): AudioSink? {
        val sink =
            super.buildAudioSink(context, enableFloatOutput, enableAudioOutputPlaybackParams)
                ?: return null
        return object : ForwardingAudioSink(sink) {
            private var format: Format? = null
            private var pendingBuffer: ByteBuffer? = null

            // Encoded passthrough / offload bypasses PCM. Decode in the app so every route
            // shares the same honest signal. Audio still passes untouched to the stock sink.
            override fun supportsFormat(format: Format) =
                format.sampleMimeType == MimeTypes.AUDIO_RAW && super.supportsFormat(format)

            override fun getFormatSupport(format: Format) =
                if (format.sampleMimeType == MimeTypes.AUDIO_RAW) super.getFormatSupport(format)
                else AudioSink.SINK_FORMAT_UNSUPPORTED

            override fun getFormatOffloadSupport(format: Format) =
                AudioOffloadSupport.DEFAULT_UNSUPPORTED

            override fun configure(config: AudioSink.AudioSinkConfig) {
                if (format != config.format) AudioWaveform.reset()
                format = config.format
                super.configure(config)
            }

            override fun handleBuffer(
                buffer: ByteBuffer,
                presentationTimeUs: Long,
                encodedAccessUnitCount: Int,
            ): Boolean {
                // A sink can consume a buffer in several calls. Capture exactly once,
                // using absolute reads so its position / limit and audio are untouched.
                if (buffer !== pendingBuffer) {
                    format?.let { AudioWaveform.append(buffer, presentationTimeUs, it) }
                    pendingBuffer = buffer
                }
                val consumed =
                    super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
                if (consumed) pendingBuffer = null
                return consumed
            }

            override fun getCurrentPositionUs(sourceEnded: Boolean): Long =
                super.getCurrentPositionUs(sourceEnded).also { AudioWaveform.clock(it) }

            override fun play() {
                super.play()
                AudioWaveform.playback(true)
            }

            override fun pause() {
                super.pause()
                AudioWaveform.playback(false)
            }

            override fun setPlaybackParameters(parameters: PlaybackParameters) {
                super.setPlaybackParameters(parameters)
                AudioWaveform.speed(super.getPlaybackParameters().speed)
            }

            override fun handleDiscontinuity() {
                AudioWaveform.reset()
                super.handleDiscontinuity()
            }

            override fun flush() {
                pendingBuffer = null
                AudioWaveform.reset()
                super.flush()
            }

            override fun reset() {
                pendingBuffer = null
                AudioWaveform.reset()
                super.reset()
            }

            override fun release() {
                AudioWaveform.playback(false)
                AudioWaveform.reset()
                super.release()
            }
        }
    }
}
