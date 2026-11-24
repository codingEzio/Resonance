package dev.resonance

import android.content.ContentValues
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class ForgetRemoteGroupTest {
    @Test
    fun removesOnlyRetiredRemoteSourcesAndPreservesOfflineAndOtherGroups() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-forget-${System.nanoTime()}.db"
        val db = LibraryDb(context, name)
        try {
            fun add(id: Int, groups: List<String>, local: Boolean): Long {
                val sql = db.writableDatabase
                val row =
                    sql.insertOrThrow(
                        "media",
                        null,
                        ContentValues().apply {
                            put("uri", "resonance://fixture$id")
                            put("filename", "$id.mp4")
                            put("title", "Fixture $id")
                            put("state", "ready")
                            put("content_hash", id.toString(16).padStart(64, '0'))
                            put(
                                "library_locations",
                                encodeLocations(groups.map { LibraryLocation(it, "") }),
                            )
                        },
                    )
                fun source(uri: String, kind: String) {
                    sql.insertOrThrow(
                        "sources",
                        null,
                        ContentValues().apply {
                            put("uri", uri)
                            put("media_id", row)
                            put("kind", kind)
                        },
                    )
                }
                source("resonance://fixture$id", "remote")
                if (local) source("content://fixture/$id", "download")
                return row
            }
            val remoteOnly = add(1, listOf("retired"), false)
            val offline = add(2, listOf("retired"), true)
            val shared = add(3, listOf("retired", "kept"), false)
            val other = add(4, listOf("kept"), false)
            assertEquals(1, db.forgetRemoteGroup("retired"))
            assertNull(db.get(remoteOnly))
            assertTrue(db.hasLocal(offline))
            assertFalse(db.hasRemote(offline))
            assertEquals(listOf("kept"), db.get(shared)!!.locations.map { it.group })
            assertTrue(db.hasRemote(shared))
            assertNotNull(db.get(other))
            assertEquals(0, db.forgetRemoteGroup("retired"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
