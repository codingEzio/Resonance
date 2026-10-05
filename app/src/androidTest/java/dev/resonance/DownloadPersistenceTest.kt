package dev.resonance

import android.content.ContentValues
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Emulator-only SQLite fixtures: no network, FGS, real files, or production database. */
class DownloadPersistenceTest {
    private fun fixture(db: LibraryDb, count: Int, start: Int = 1) {
        val sql = db.writableDatabase
        sql.beginTransaction()
        try {
            repeat(count) { offset ->
                val n = start + offset
                val id =
                    sql.insertOrThrow(
                        "media",
                        null,
                        ContentValues().apply {
                            put("uri", "resonance.fixture://remote/$n")
                            put("filename", "$n.mp4")
                            put("title", "Fixture $n")
                            put("state", "ready")
                            put("content_hash", n.toString(16).padStart(64, '0'))
                            put("bytes", 100L)
                        },
                    )
                sql.insertOrThrow(
                    "sources",
                    null,
                    ContentValues().apply {
                        put("uri", "resonance.fixture://remote/$n")
                        put("media_id", id)
                        put("kind", "remote")
                    },
                )
            }
            sql.setTransactionSuccessful()
        } finally {
            sql.endTransaction()
        }
    }

    @Test
    fun oneItemResumesWithoutRepeatingCompletedWork() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-download-one-${System.nanoTime()}.db"
        var db = LibraryDb(context, name)
        try {
            fixture(db, 1)
            val source = db.entries().single()
            db.acceptRemote(
                listOf(source.copy(uri = "http://127.0.0.1/fixture/retransmit", remote = true))
            )
            db.acceptRemote(
                listOf(source.copy(uri = "http://127.0.0.1/fixture/retransmit", remote = true))
            )
            db.writableDatabase.insertOrThrow(
                "sources",
                null,
                ContentValues().apply {
                    put("uri", "content://resonance.fixture/original")
                    put("media_id", source.id)
                    put("kind", "document")
                },
            )
            assertEquals(1, db.downloadSummary().total)
            assertEquals(0, db.downloadSummary().complete)
            assertEquals(3, db.get(source.id)!!.sourceCount)
            assertEquals(1, db.requestAllDownloads().accepted)
            val id = db.nextDownload()!!.id
            db.downloadProgress(id, "running", 40)
            db.close()
            db = LibraryDb(context, name)
            db.recoverDownloads()
            assertEquals("queued", db.get(id)!!.downloadState)
            assertEquals(40L, db.get(id)!!.downloadBytes)
            assertEquals(0, db.requestAllDownloads().accepted)
            db.cancelDownload(id)
            assertEquals(1, db.requestAllDownloads().accepted)
            db.finishDownload(id, "resonance.fixture://verified/$id", 100)
            assertEquals(0, db.requestAllDownloads().accepted)
            assertEquals(1, db.downloadSummary().complete)
            assertEquals(100L, db.downloadSummary().completedBytes)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun tenThousandAreDurableIdempotentAndOverflowRollsBack() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-download-batch-${System.nanoTime()}.db"
        var db = LibraryDb(context, name)
        try {
            fixture(db, 10000)
            assertEquals(10000, db.requestAllDownloads().accepted)
            assertEquals(0, db.requestAllDownloads().accepted)
            assertEquals(1000000L, db.downloadSummary().totalBytes)
            val first = db.nextDownload()!!.id
            db.downloadProgress(first, "running", 50)
            db.close()
            db = LibraryDb(context, name)
            db.recoverDownloads()
            assertEquals(10000, db.downloadSummary().waiting)
            assertEquals(50L, db.get(first)!!.downloadBytes)
            fixture(db, 1, 10001)
            assertThrows(IllegalStateException::class.java) { db.requestAllDownloads() }
            assertEquals(10000, db.downloadSummary().waiting)
            assertEquals("none", db.get(10001)!!.downloadState)
            db.downloadProgress(first, "failed", 50, "download_failed")
            assertEquals(1, db.downloadSummary().failed)
            assertThrows(IllegalStateException::class.java) { db.requestAllDownloads() }
            assertEquals("failed", db.get(first)!!.downloadState)
            db.finishDownload(10001, "resonance.fixture://verified/extra", 100)
            assertEquals(1, db.requestAllDownloads().accepted)
            assertEquals(10000, db.downloadSummary().waiting)
            assertEquals(1, db.downloadSummary().complete)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
