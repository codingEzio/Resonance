package dev.resonance

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class LibraryDb(context: Context, name: String = "library.db") :
    SQLiteOpenHelper(context, name, null, 3) {
    val revision = MutableStateFlow(0L)

    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE media (
            id INTEGER PRIMARY KEY, uri TEXT NOT NULL UNIQUE, filename TEXT NOT NULL,
            title TEXT NOT NULL, creator TEXT NOT NULL DEFAULT '', album TEXT NOT NULL DEFAULT '',
            description TEXT NOT NULL DEFAULT '', duration INTEGER NOT NULL DEFAULT 0,
            bytes INTEGER NOT NULL DEFAULT 0, state TEXT NOT NULL DEFAULT 'queued',
            error TEXT NOT NULL DEFAULT '', cues TEXT NOT NULL DEFAULT '[]', content_hash TEXT NOT NULL DEFAULT '', preferred_uri TEXT NOT NULL DEFAULT '', duplicate_of INTEGER NOT NULL DEFAULT 0, download_state TEXT NOT NULL DEFAULT 'none', download_bytes INTEGER NOT NULL DEFAULT 0, library_locations TEXT NOT NULL DEFAULT '[]')"""
        )
        db.execSQL("CREATE INDEX media_state ON media(state,id)")
        createIdentityTables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE media ADD COLUMN content_hash TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE media ADD COLUMN preferred_uri TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE media ADD COLUMN duplicate_of INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE media ADD COLUMN download_state TEXT NOT NULL DEFAULT 'none'")
            db.execSQL("ALTER TABLE media ADD COLUMN download_bytes INTEGER NOT NULL DEFAULT 0")
            createIdentityTables(db)
            db.execSQL("UPDATE media SET state='queued' WHERE state='ready'")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE media ADD COLUMN library_locations TEXT NOT NULL DEFAULT '[]'")
        }
    }

    private fun createIdentityTables(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS sources (uri TEXT PRIMARY KEY, media_id INTEGER NOT NULL, kind TEXT NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS sources_media ON sources(media_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS media_hash ON media(content_hash,state)")
    }

    private val selectMedia =
        "SELECT media.*, (SELECT COUNT(*) FROM sources WHERE media_id=media.id) AS source_count, (SELECT COUNT(*) FROM sources WHERE media_id=media.id AND kind='remote') AS remote_count FROM media"

    private fun changed() {
        revision.update { it + 1 }
    }

    @Synchronized
    fun enqueue(items: List<Pair<String, String>>): Int {
        require(items.size <= MediaLimits.IMPORT_ITEMS) { "capacity" }
        val db = writableDatabase
        var added = 0
        db.beginTransaction()
        try {
            val current =
                db.rawQuery("SELECT COUNT(*) FROM media WHERE state IN ('queued','running')", null)
                    .use {
                        it.moveToFirst()
                        it.getInt(0)
                    }
            for ((uri, name) in items) {
                require(uri.startsWith("content://")) { "local_only" }
                val values =
                    ContentValues().apply {
                        put("uri", uri)
                        put("filename", name)
                        put("title", name.substringBeforeLast('.').replace('_', ' '))
                    }
                if (
                    db.insertWithOnConflict(
                        "media",
                        null,
                        values,
                        SQLiteDatabase.CONFLICT_IGNORE,
                    ) != -1L
                )
                    added++
                else {
                    added +=
                        db.update(
                            "media",
                            ContentValues().apply {
                                put("state", "queued")
                                put("error", "")
                            },
                            "uri=? AND state IN ('failed','cancelled')",
                            arrayOf(uri),
                        )
                }
                check(current + added <= MediaLimits.IMPORT_ITEMS) { "capacity" }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        changed()
        return added
    }

    fun entries(): List<MediaEntry> =
        readableDatabase.rawQuery("$selectMedia WHERE state!='alias' ORDER BY id DESC", null).use {
            c ->
            buildList { while (c.moveToNext()) add(read(c)) }
        }

    fun get(id: Long): MediaEntry? =
        readableDatabase.rawQuery("$selectMedia WHERE id=?", arrayOf(id.toString())).use {
            if (it.moveToFirst()) {
                val alias = it.getLong(it.getColumnIndexOrThrow("duplicate_of"))
                if (alias > 0) get(alias) else read(it)
            } else null
        }

    fun nextQueued(): MediaEntry? =
        readableDatabase
            .rawQuery("$selectMedia WHERE state='queued' ORDER BY id LIMIT 1", null)
            .use { if (it.moveToFirst()) read(it) else null }

    fun markRunning(id: Long): Boolean {
        val claimed =
            writableDatabase.update(
                "media",
                ContentValues().apply { put("state", "running") },
                "id=? AND state='queued'",
                arrayOf(id.toString()),
            ) > 0
        if (claimed) changed()
        return claimed
    }

    fun fail(id: Long, error: String) {
        writableDatabase.update(
            "media",
            ContentValues().apply {
                put("state", "failed")
                put("error", error)
            },
            "id=? AND state IN ('queued','running')",
            arrayOf(id.toString()),
        )
        changed()
    }

    @Synchronized
    fun retry(id: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val entry = get(id)
            if (entry?.state in listOf("failed", "cancelled")) {
                val active =
                    db.rawQuery(
                            "SELECT COUNT(*) FROM media WHERE state IN ('queued','running')",
                            null,
                        )
                        .use {
                            it.moveToFirst()
                            it.getInt(0)
                        }
                check(active < MediaLimits.IMPORT_ITEMS) { "capacity" }
                db.update(
                    "media",
                    ContentValues().apply {
                        put("state", "queued")
                        put("error", "")
                    },
                    "id=?",
                    arrayOf(id.toString()),
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        changed()
    }

    fun recoverInterrupted() {
        writableDatabase.execSQL("UPDATE media SET state='queued' WHERE state='running'")
        changed()
    }

    fun cancelPending() {
        writableDatabase.execSQL(
            "UPDATE media SET state='cancelled' WHERE state IN ('queued','running')"
        )
        changed()
    }

    @Synchronized
    fun complete(entry: MediaEntry) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val state =
                db.rawQuery("SELECT state FROM media WHERE id=?", arrayOf(entry.id.toString()))
                    .use { if (it.moveToFirst()) it.getString(0) else "" }
            if (state == "cancelled" || state == "alias") return
            val canonical =
                if (entry.contentHash.isNotEmpty())
                    db.rawQuery(
                            "SELECT id FROM media WHERE content_hash=? AND state='ready' AND id!=? LIMIT 1",
                            arrayOf(entry.contentHash, entry.id.toString()),
                        )
                        .use { if (it.moveToFirst()) it.getLong(0) else entry.id }
                else entry.id
            val previous = get(canonical)
            val kind =
                if (entry.uri.startsWith("resonance:")) "remote"
                else if (entry.uri.startsWith("file:")) "download" else "document"
            db.insertWithOnConflict(
                "sources",
                null,
                ContentValues().apply {
                    put("uri", entry.uri)
                    put("media_id", canonical)
                    put("kind", kind)
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            if (canonical != entry.id) {
                db.update(
                    "media",
                    ContentValues().apply {
                        put("state", "alias")
                        put("duplicate_of", canonical)
                        put("content_hash", entry.contentHash)
                    },
                    "id=?",
                    arrayOf(entry.id.toString()),
                )
            }
            val local = kind != "remote"
            val keepManual = previous?.cues?.any { it.origin == "manual" } == true
            db.update(
                "media",
                ContentValues().apply {
                    if (canonical == entry.id) {
                        put("title", entry.title)
                        put("creator", entry.creator)
                        put("album", entry.album)
                        put("description", entry.description)
                        put("duration", entry.durationMs)
                        put("bytes", entry.bytes)
                        put("cues", encodeCues(if (keepManual) previous!!.cues else entry.cues))
                    }
                    if (entry.locations.isNotEmpty())
                        put("library_locations", encodeLocations(entry.locations))
                    put("content_hash", entry.contentHash)
                    if (local || previous?.playUri.isNullOrEmpty()) put("preferred_uri", entry.uri)
                    put("state", "ready")
                    put("error", "")
                },
                "id=?",
                arrayOf(canonical.toString()),
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        changed()
    }

    fun byHash(hash: String): MediaEntry? =
        readableDatabase
            .rawQuery(
                "SELECT id FROM media WHERE content_hash=? AND state='ready' LIMIT 1",
                arrayOf(hash),
            )
            .use { if (it.moveToFirst()) get(it.getLong(0)) else null }

    @Synchronized
    fun acceptRemote(items: List<MediaEntry>) {
        require(items.size <= MediaLimits.CATALOG_ITEMS) { "capacity" }
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (item in items) {
                require(item.contentHash.matches(Regex("[a-f0-9]{64}"))) { "invalid_hash" }
                val existing = byHash(item.contentHash)
                if (existing != null) {
                    db.update(
                        "media",
                        ContentValues().apply {
                            put("library_locations", encodeLocations(item.locations))
                        },
                        "id=?",
                        arrayOf(existing.id.toString()),
                    )
                    db.insertWithOnConflict(
                        "sources",
                        null,
                        ContentValues().apply {
                            put("uri", item.uri)
                            put("media_id", existing.id)
                            put("kind", "remote")
                        },
                        SQLiteDatabase.CONFLICT_REPLACE,
                    )
                } else {
                    val id =
                        db.insertWithOnConflict(
                            "media",
                            null,
                            ContentValues().apply {
                                put("uri", item.uri)
                                put("filename", item.filename)
                                put("title", item.title)
                            },
                            SQLiteDatabase.CONFLICT_IGNORE,
                        )
                    val actual =
                        if (id > 0) id
                        else
                            db.rawQuery("SELECT id FROM media WHERE uri=?", arrayOf(item.uri)).use {
                                it.moveToFirst()
                                it.getLong(0)
                            }
                    complete(item.copy(id = actual))
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        changed()
    }

    fun hasLocal(id: Long): Boolean =
        readableDatabase
            .rawQuery(
                "SELECT 1 FROM sources WHERE media_id=? AND kind!='remote' LIMIT 1",
                arrayOf(id.toString()),
            )
            .use { it.moveToFirst() }

    fun hasRemote(id: Long): Boolean =
        readableDatabase
            .rawQuery(
                "SELECT 1 FROM sources WHERE media_id=? AND kind='remote' LIMIT 1",
                arrayOf(id.toString()),
            )
            .use { it.moveToFirst() }

    fun hasOfflineCopy(id: Long): Boolean =
        readableDatabase
            .rawQuery(
                "SELECT 1 FROM sources WHERE media_id=? AND kind='download' LIMIT 1",
                arrayOf(id.toString()),
            )
            .use { it.moveToFirst() }

    @Synchronized
    fun requestDownload(id: Long): Boolean {
        val canonical = get(id) ?: return false
        if (
            hasOfflineCopy(canonical.id) ||
                (hasLocal(canonical.id) && canonical.downloadState == "none")
        )
            return false
        check(hasRemote(canonical.id)) { "no_remote" }
        val active =
            readableDatabase
                .rawQuery(
                    "SELECT COUNT(*) FROM media WHERE download_state IN ('queued','running')",
                    null,
                )
                .use {
                    it.moveToFirst()
                    it.getInt(0)
                }
        check(
            DownloadPolicy.accepts(
                active,
                if (canonical.downloadState in listOf("queued", "running")) 0 else 1,
            )
        ) {
            "capacity"
        }
        writableDatabase.execSQL(
            "UPDATE media SET download_state='queued',error='' WHERE id=? AND download_state!='running'",
            arrayOf(canonical.id),
        )
        changed()
        return true
    }

    /** Canonical remote content only. Alternate locations with the same hash are one item. */
    private val downloadCatalog =
        """
        SELECT media.id,media.download_state,media.bytes,media.download_bytes,
          EXISTS(SELECT 1 FROM sources WHERE media_id=media.id AND kind='download') AS local
        FROM media WHERE media.id IN (
          SELECT MIN(m.id) FROM media m
          WHERE m.state='ready' AND m.duplicate_of=0 AND m.content_hash!=''
          AND EXISTS(SELECT 1 FROM sources WHERE media_id=m.id AND kind='remote')
          GROUP BY m.content_hash
        ) ORDER BY media.id
        """
            .trimIndent()

    /** All-or-nothing durable acceptance. Running work and completed sources are untouched. */
    @Synchronized
    fun requestAllDownloads(): DownloadBatchReceipt {
        val db = writableDatabase
        var added = 0
        db.beginTransaction()
        try {
            val active =
                db.rawQuery(
                        "SELECT COUNT(*) FROM media WHERE download_state IN ('queued','running')",
                        null,
                    )
                    .use {
                        it.moveToFirst()
                        it.getInt(0)
                    }
            val pending = mutableListOf<Long>()
            db.rawQuery(downloadCatalog, null).use { rows ->
                while (rows.moveToNext()) {
                    val state = rows.getString(1)
                    if (rows.getInt(4) == 0 && state !in listOf("queued", "running")) {
                        check(DownloadPolicy.accepts(active, pending.size + 1)) { "capacity" }
                        pending.add(rows.getLong(0))
                    }
                }
            }
            for (id in pending) db.execSQL(
                "UPDATE media SET download_state='queued',error='' WHERE id=?",
                arrayOf(id),
            )
            added = pending.size
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        changed()
        return DownloadBatchReceipt(added, downloadSummary())
    }

    fun downloadSummary(): DownloadSummary {
        var total = 0
        var complete = 0
        var waiting = 0
        var failed = 0
        var paused = 0
        var totalBytes = 0L
        var completedBytes = 0L
        var transferredBytes = 0L
        readableDatabase.rawQuery(downloadCatalog, null).use { rows ->
            while (rows.moveToNext()) {
                total++
                val bytes = rows.getLong(2).coerceAtLeast(0)
                totalBytes += bytes
                if (rows.getInt(4) != 0) {
                    complete++
                    completedBytes += bytes
                    transferredBytes += bytes
                } else {
                    transferredBytes += rows.getLong(3).coerceIn(0L, bytes)
                    when (rows.getString(1)) {
                        "queued",
                        "running" -> waiting++
                        "failed" -> failed++
                        "paused" -> paused++
                    }
                }
            }
        }
        return DownloadSummary(
            total,
            complete,
            waiting,
            failed,
            paused,
            total - complete,
            totalBytes,
            completedBytes,
            transferredBytes,
        )
    }

    fun nextDownload(): MediaEntry? =
        readableDatabase
            .rawQuery(
                "SELECT id FROM media WHERE download_state='queued' AND state='ready' ORDER BY id LIMIT 1",
                null,
            )
            .use { if (it.moveToFirst()) get(it.getLong(0)) else null }

    fun downloadProgress(id: Long, state: String, bytes: Long, error: String = "") {
        writableDatabase.update(
            "media",
            ContentValues().apply {
                put("download_state", state)
                put("download_bytes", bytes)
                put("error", error)
            },
            "id=? AND download_state!='paused'",
            arrayOf(id.toString()),
        )
        changed()
    }

    fun cancelDownload(id: Long) {
        writableDatabase.execSQL("UPDATE media SET download_state='paused' WHERE id=?", arrayOf(id))
        changed()
    }

    fun recoverDownloads() {
        writableDatabase.execSQL(
            "UPDATE media SET download_state='queued' WHERE download_state='running'"
        )
        changed()
    }

    @Synchronized
    fun finishDownload(id: Long, uri: String, bytes: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.insertWithOnConflict(
                "sources",
                null,
                ContentValues().apply {
                    put("uri", uri)
                    put("media_id", id)
                    put("kind", "download")
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            db.update(
                "media",
                ContentValues().apply {
                    put("preferred_uri", uri)
                    put("download_state", "complete")
                    put("download_bytes", bytes)
                    put("error", "")
                },
                "id=?",
                arrayOf(id.toString()),
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        changed()
    }

    fun saveCues(id: Long, cues: List<Cue>) {
        writableDatabase.update(
            "media",
            ContentValues().apply { put("cues", encodeCues(cues)) },
            "id=?",
            arrayOf(id.toString()),
        )
        changed()
    }

    private fun read(c: Cursor): MediaEntry {
        fun s(name: String) = c.getString(c.getColumnIndexOrThrow(name))
        fun l(name: String) = c.getLong(c.getColumnIndexOrThrow(name))
        return MediaEntry(
            l("id"),
            s("uri"),
            s("filename"),
            s("title"),
            s("creator"),
            s("album"),
            s("description"),
            l("duration"),
            l("bytes"),
            s("state"),
            s("error"),
            decodeCues(s("cues")),
            contentHash = s("content_hash"),
            playUri = s("preferred_uri").ifBlank { s("uri") },
            remote = s("preferred_uri").ifBlank { s("uri") }.startsWith("resonance:"),
            downloadState = s("download_state"),
            downloadBytes = l("download_bytes"),
            sourceCount = l("source_count").toInt().coerceAtLeast(1),
            locations = decodeLocations(s("library_locations")),
            remoteAvailable = l("remote_count") > 0,
        )
    }
}
