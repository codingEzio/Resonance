package dev.resonance

import android.content.ContentValues
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Isolated database and files; never touches the user's library. */
class MirrorRetirementTest {
    @Test
    fun onlyRetiresKnownOwnedInactiveCopies() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-mirror-${System.nanoTime()}.db"
        val directory = File(context.cacheDir, "mirror-${System.nanoTime()}").apply { mkdirs() }
        val db = LibraryDb(context, name)
        try {
            fun item(
                n: Int,
                groups: List<String> = listOf("collection"),
                uriInOwnedDir: Boolean = true,
            ): MediaEntry {
                val hash = n.toString(16).padStart(64, '0')
                val item =
                    MediaEntry(
                        0,
                        "resonance://$hash",
                        "$n.mp4",
                        state = "ready",
                        contentHash = hash,
                        locations = groups.map { LibraryLocation(it, "", "collections") },
                    )
                db.acceptRemote(listOf(item))
                val row = db.byHash(hash)!!
                val path =
                    if (uriInOwnedDir) File(directory, "$hash.media")
                    else File(context.cacheDir, "outside-$hash")
                db.finishDownload(row.id, Uri.fromFile(path).toString(), 5)
                return db.byHash(hash)!!
            }
            val owned = item(1)
            val active = item(2)
            val other = item(3, listOf("collection", "other"))
            val document = item(4)
            db.writableDatabase.insertOrThrow(
                "sources",
                null,
                ContentValues().apply {
                    put("uri", "content://fixture/doc")
                    put("media_id", document.id)
                    put("kind", "document")
                },
            )
            val unknown = item(5, uriInOwnedDir = false)
            assertTrue(db.mirrorHashes("scope").isEmpty())
            db.acceptRemote(listOf(other), "scope")
            assertEquals(setOf(other.contentHash), db.mirrorHashes("scope"))
            var removedFromQueue = 0L
            assertEquals(
                owned.playUri,
                db.retireMirrored(owned.contentHash, "collection", "", directory) { id ->
                    removedFromQueue = id
                    true
                },
            )
            assertEquals(owned.id, removedFromQueue)
            assertNull(db.byHash(owned.contentHash))
            assertNull(
                db.retireMirrored(active.contentHash, "collection", "entry:${active.id}", directory)
            )
            assertNotNull(db.byHash(active.contentHash))
            assertEquals(
                "",
                db.retireMirrored(other.contentHash, "collection", "", directory) {
                    fail("Shared entries must stay in the playback queue")
                    false
                },
            )
            assertEquals(listOf("other"), db.byHash(other.contentHash)!!.locations.map { it.group })
            assertEquals(
                "",
                db.retireMirrored(
                    document.contentHash,
                    "collection",
                    "entry:${document.id}",
                    directory,
                ) {
                    fail("An independent document must stay in the playback queue")
                    false
                },
            )
            assertNotNull(db.byHash(document.contentHash))
            assertNull(
                db.retireMirrored(unknown.contentHash, "collection", "", directory) {
                    fail("An unknown file must stay in the playback queue")
                    false
                }
            )
            assertNotNull(db.byHash(unknown.contentHash))
            val last = item(6)
            assertNotNull(db.retireMirrored(last.contentHash, "collection", "", directory))
            assertNull(db.get(last.id))
            val next = item(7)
            assertTrue(next.id > last.id)
            assertNull(db.retireMirrored(next.contentHash, "collection", "", directory) { false })
            assertNotNull(db.get(next.id))
            val unlocated = item(8, emptyList())
            assertEquals("", db.retireMirrored(unlocated.contentHash, "collection", "", directory))
            assertNotNull(db.get(unlocated.id))
            assertEquals(
                unlocated.playUri,
                db.retireMirrored(unlocated.contentHash, "collection", "", directory, true),
            )
            assertNull(db.get(unlocated.id))
        } finally {
            db.close()
            context.deleteDatabase(name)
            directory.deleteRecursively()
        }
    }
}
