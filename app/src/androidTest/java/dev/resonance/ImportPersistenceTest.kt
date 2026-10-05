package dev.resonance

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Isolated emulator-only fixtures. Never touches a user media provider. */
class ImportPersistenceTest {
    @Test
    fun identicalContentConvergesAcrossTenThousandSourceUrisAndRestart() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-identity-${System.nanoTime()}.db"
        var db = LibraryDb(context, name)
        try {
            db.enqueue((1..10000).map { "content://resonance.fixture/copy-$it" to "Copy $it.mp4" })
            val firstId = db.nextQueued()!!.id
            repeat(10000) {
                val next = db.nextQueued()!!
                db.complete(
                    next.copy(
                        title = "One recording",
                        contentHash = "a".repeat(64),
                        bytes = 3,
                        state = "ready",
                    )
                )
            }
            assertEquals(1, db.entries().size)
            assertEquals(10000, db.entries().single().sourceCount)
            db.close()
            db = LibraryDb(context, name)
            assertEquals("a".repeat(64), db.get(firstId)!!.contentHash)
            assertEquals(
                0,
                db.enqueue(listOf("content://resonance.fixture/copy-9999" to "Copy again")),
            )
            assertEquals(1, db.entries().size)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun retryCannotExceedTheDurableQueueLimit() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-retry-${System.nanoTime()}.db"
        val db = LibraryDb(context, name)
        try {
            db.enqueue((1..10000).map { "content://resonance.fixture/$it" to "$it.mp4" })
            val first = db.nextQueued()!!
            db.fail(first.id, "unreadable")
            db.enqueue(listOf("content://resonance.fixture/replacement" to "replacement.mp4"))
            assertThrows(IllegalStateException::class.java) { db.retry(first.id) }
            assertEquals("failed", db.get(first.id)!!.state)
            assertEquals(10000, db.entries().count { it.state == "queued" })
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun tenThousandPersistResumeCancelAndRejectOverflow() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-import-${System.nanoTime()}.db"
        var db = LibraryDb(context, name)
        try {
            val items = (1..10000).map { "content://resonance.fixture/$it" to "Track $it.mp4" }
            assertEquals(10000, db.enqueue(items))
            assertEquals(0, db.enqueue(items))
            assertThrows(IllegalStateException::class.java) {
                db.enqueue(listOf("content://resonance.fixture/overflow" to "overflow"))
            }
            val first = db.nextQueued()!!
            db.markRunning(first.id)
            db.close()
            db = LibraryDb(context, name)
            db.recoverInterrupted()
            assertEquals(10000, db.entries().count { it.state == "queued" })
            db.complete(
                first.copy(
                    title = "Persisted title",
                    state = "ready",
                    durationMs = 60000,
                    cues = listOf(Cue("First", 0, 60000)),
                )
            )
            db.cancelPending()
            assertEquals(9999, db.entries().count { it.state == "cancelled" })
            assertEquals("Persisted title", db.get(first.id)!!.title)
            db.close()
            db = LibraryDb(context, name)
            assertEquals("First", db.get(first.id)!!.cues.single().title)
            db.retry(first.id + 1)
            assertEquals("queued", db.get(first.id + 1)!!.state)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
