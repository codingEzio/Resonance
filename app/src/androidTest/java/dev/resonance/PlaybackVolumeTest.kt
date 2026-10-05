package dev.resonance

import android.content.ComponentName
import android.media.AudioManager
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

/** Emulator-only service proof. Silent fixture; never runs against the personal installed app. */
class PlaybackVolumeTest {
    @Test
    fun sessionRequestsAndHardwareVolumeChangesRespectTheSelectedLimits() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".catalog"))
        check(
            android.os.Build.FINGERPRINT.contains("generic") ||
                android.os.Build.MODEL.contains("sdk")
        )
        val settings = context.getSharedPreferences("settings", 0)
        val previousLimit = settings.getFloat("volume_limit", .3f)
        val previousGuard = settings.getBoolean("system_volume_guard", false)
        val audio = context.getSystemService(AudioManager::class.java)
        val previousVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val allowed = (maxVolume * .1f).toInt()
        var controller: MediaController? = null
        val file = File(context.cacheDir, "volume-proof-${System.nanoTime()}.wav")
        fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)
        fun waitFor(message: String, condition: () -> Boolean) {
            val end = android.os.SystemClock.elapsedRealtime() + 4000
            while (android.os.SystemClock.elapsedRealtime() < end) {
                var ready = false
                onMain { ready = condition() }
                if (ready) return
                Thread.sleep(25)
            }
            fail(message)
        }
        try {
            settings
                .edit()
                .putFloat("volume_limit", .1f)
                .putBoolean("system_volume_guard", true)
                .commit()
            val length = 10 * 8000 * 2
            val header =
                ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                    put("RIFF".toByteArray())
                    putInt(36 + length)
                    put("WAVEfmt ".toByteArray())
                    putInt(16)
                    putShort(1)
                    putShort(1)
                    putInt(8000)
                    putInt(16000)
                    putShort(2)
                    putShort(16)
                    put("data".toByteArray())
                    putInt(length)
                }
            file.outputStream().use { out ->
                out.write(header.array())
                out.write(ByteArray(length))
            }
            var future: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
            onMain {
                future =
                    MediaController.Builder(
                            context,
                            SessionToken(
                                context,
                                ComponentName(context, PlaybackService::class.java),
                            ),
                        )
                        .buildAsync()
            }
            controller = future!!.get(10, TimeUnit.SECONDS)
            val player = controller!!
            waitFor("Initial gain exceeds limit") { player.volume <= .1001f }
            onMain { player.volume = 1f }
            waitFor("Controller request bypassed gain limit") { player.volume <= .1001f }
            settings.edit().putFloat("volume_limit", .05f).commit()
            waitFor("Changing the ceiling did not reach the live player") {
                player.volume <= .0501f
            }
            settings.edit().putFloat("volume_limit", .1f).commit()
            onMain {
                player.setMediaItem(MediaItem.fromUri(file.toURI().toString()))
                player.prepare()
                player.play()
            }
            waitFor("Silent proof file did not start") { player.isPlaying }
            audio.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                (allowed + 2).coerceAtMost(maxVolume),
                0,
            )
            waitFor("System volume key change was not lowered while playing") {
                audio.getStreamVolume(AudioManager.STREAM_MUSIC) <= allowed
            }
            settings.edit().putBoolean("system_volume_guard", false).commit()
            audio.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                (allowed + 2).coerceAtMost(maxVolume),
                0,
            )
            Thread.sleep(550)
            assertTrue(
                "Disabled guard still changes shared volume",
                audio.getStreamVolume(AudioManager.STREAM_MUSIC) > allowed,
            )
        } finally {
            controller?.let { player ->
                onMain {
                    player.stop()
                    player.clearMediaItems()
                    player.release()
                }
            }
            settings
                .edit()
                .putBoolean("system_volume_guard", false)
                .putFloat("volume_limit", previousLimit)
                .commit()
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, previousVolume, 0)
            settings.edit().putBoolean("system_volume_guard", previousGuard).commit()
            file.delete()
        }
    }
}
