package dev.resonance

import android.content.ComponentName
import android.content.ContentValues
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

/** Isolated emulator session; non-playing URI fixtures, no media or personal-library writes. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackQueueTest {
    @Test
    fun tenThousandOccurrencesPersistAndRestoreWithOverflowRejected() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".catalog"))
        check(
            android.os.Build.FINGERPRINT.contains("generic") ||
                android.os.Build.MODEL.contains("sdk")
        )
        val app = context.applicationContext as ResonanceApp
        val db = app.library.writableDatabase
        val fixtureId =
            db.insertOrThrow(
                "media",
                null,
                ContentValues().apply {
                    put("uri", "resonance.queue.fixture://capacity-${System.nanoTime()}")
                    put("filename", "queue-capacity-fixture.wav")
                    put("title", "Queue capacity fixture")
                    put("state", "ready")
                },
            )
        fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)
        fun connect(): MediaController {
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
            return future!!.get(10, TimeUnit.SECONDS)
        }
        var player = connect()
        val item =
            MediaItem.Builder()
                .setMediaId("entry:$fixtureId")
                .setUri("resonance.queue.fixture://capacity")
                .build()
        fun send(items: List<MediaItem>): SessionResult {
            var response: com.google.common.util.concurrent.ListenableFuture<SessionResult>? = null
            onMain {
                response =
                    player.sendCustomCommand(
                        SessionCommand("dev.resonance.ADD_TO_QUEUE", Bundle.EMPTY),
                        queueArguments(items),
                    )
            }
            return response!!.get(30, TimeUnit.SECONDS)
        }
        try {
            onMain { player.clearMediaItems() }
            assertEquals(SessionResult.RESULT_SUCCESS, send(List(10000) { item }).resultCode)
            onMain { assertEquals(10000, player.mediaItemCount) }
            val checkpoint = app.getSharedPreferences("playback", 0)
            assertEquals(
                "Success reply must follow durable queue save",
                10000,
                JSONArray(checkpoint.getString("queue", "[]")).length(),
            )
            assertNotEquals(SessionResult.RESULT_SUCCESS, send(listOf(item)).resultCode)
            onMain {
                assertEquals("Overflow must not partly append", 10000, player.mediaItemCount)
                player.release()
            }
            onMain { context.stopService(Intent(context, PlaybackService::class.java)) }
            player = connect()
            val deadline = android.os.SystemClock.elapsedRealtime() + 10000
            var restored = false
            while (!restored && android.os.SystemClock.elapsedRealtime() < deadline) {
                onMain { restored = player.mediaItemCount == 10000 }
                if (!restored) Thread.sleep(25)
            }
            assertTrue("Queue did not resume after service restart", restored)
            onMain { assertFalse(player.playWhenReady) }
        } finally {
            onMain {
                player.stop()
                player.clearMediaItems()
                player.release()
            }
            db.delete("media", "id=?", arrayOf(fixtureId.toString()))
        }
    }

    @Test
    fun addingAtTheEndKeepsTheCurrentItemAndExistingOrder() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".catalog"))
        check(
            android.os.Build.FINGERPRINT.contains("generic") ||
                android.os.Build.MODEL.contains("sdk")
        )
        fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)
        var future: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
        onMain {
            future =
                MediaController.Builder(
                        context,
                        SessionToken(context, ComponentName(context, PlaybackService::class.java)),
                    )
                    .buildAsync()
        }
        val player = future!!.get(10, TimeUnit.SECONDS)
        fun item(id: String) =
            MediaItem.Builder().setMediaId(id).setUri("resonance.queue.fixture://$id").build()
        try {
            onMain { player.setMediaItems(listOf(item("one"), item("two")), 0, 1234) }
            var response: com.google.common.util.concurrent.ListenableFuture<SessionResult>? = null
            onMain {
                response =
                    player.sendCustomCommand(
                        SessionCommand("dev.resonance.ADD_TO_QUEUE", Bundle.EMPTY),
                        Bundle().apply {
                            putParcelableArrayList(
                                "items",
                                arrayListOf(
                                    item("three").toBundleIncludeLocalConfiguration(),
                                    item("four").toBundleIncludeLocalConfiguration(),
                                ),
                            )
                        },
                    )
            }
            assertEquals(
                "Append command must be available to the app controller",
                SessionResult.RESULT_SUCCESS,
                response!!.get(5, TimeUnit.SECONDS).resultCode,
            )
            onMain {
                assertEquals(
                    listOf("one", "two", "three", "four"),
                    (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId },
                )
                assertEquals("one", player.currentMediaItem!!.mediaId)
                assertEquals(1234L, player.currentPosition)
                assertFalse(player.playWhenReady)
            }
            onMain {
                response =
                    player.sendCustomCommand(
                        SessionCommand("dev.resonance.PLAY_NEXT_ORDER", Bundle.EMPTY),
                        Bundle().apply {
                            putParcelableArrayList(
                                "items",
                                arrayListOf(item("next").toBundleIncludeLocalConfiguration()),
                            )
                        },
                    )
            }
            assertEquals(
                SessionResult.RESULT_SUCCESS,
                response!!.get(5, TimeUnit.SECONDS).resultCode,
            )
            onMain {
                assertEquals(
                    listOf("one", "next", "two", "three", "four"),
                    (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId },
                )
                assertEquals(1234L, player.currentPosition)
            }
        } finally {
            onMain {
                player.stop()
                player.clearMediaItems()
                player.release()
            }
        }
    }
}
