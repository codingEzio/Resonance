package dev.resonance

import android.net.Uri
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/** App-owned copies are retired only after two complete snapshots from the paired source. */
internal class MirrorCoordinator(private val app: ResonanceApp) {
    private val prefs = app.getSharedPreferences("collection_mirror", 0)
    val status = MutableStateFlow(if (prefs.contains("pending")) "retry" else "")

    private fun digest(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString(
            ""
        ) {
            "%02x".format(it)
        }

    private fun fingerprint(items: List<MediaEntry>): String =
        digest(
            items
                .sortedBy { it.contentHash }
                .joinToString("\n") { item ->
                    JSONObject()
                        .put("hash", item.contentHash)
                        .put("bytes", item.bytes)
                        .put("filename", item.filename)
                        .put("locations", encodeLocations(item.locations.sortedBy { it.key }))
                        .put("title", item.syncedDisplayName?.title ?: "")
                        .put("subtitle", item.syncedDisplayName?.subtitle ?: "")
                        .toString()
                }
        )

    @Synchronized
    fun stage(
        items: List<MediaEntry>,
        key: String,
        sourceId: String,
        retiredHashes: Set<String>,
    ): String {
        val groups =
            items
                .flatMap { it.locations }
                .filter { it.role == "collections" }
                .map { it.group }
                .toSet()
        require(
            groups.size <= 1 &&
                items.all { item -> item.locations.all { it.role == "collections" } }
        ) {
            "catalog_scope"
        }
        require(sourceId.isNotBlank()) { "catalog_scope" }
        val scope = digest(key + "|" + sourceId)
        val previous = prefs.getString("baseline_scope", "").orEmpty()
        val oldPending = prefs.getString("pending", null)?.let { JSONObject(it) }
        require(previous.isBlank() || previous == scope) { "catalog_scope" }
        require(oldPending == null || oldPending.optString("scope") == scope) { "catalog_scope" }
        val inherited =
            if (oldPending?.optString("scope") == scope) {
                oldPending.getJSONArray("hashes").strings() +
                    (oldPending.optJSONArray("inherited") ?: JSONArray()).strings()
            } else emptySet()
        val group =
            groups.singleOrNull()
                ?: if (oldPending?.optString("scope") == scope) oldPending.optString("group")
                else if (scope == previous) prefs.getString("baseline_group", "").orEmpty() else ""
        require(items.isEmpty() || group.isNotBlank()) { "catalog_scope" }
        if (scope == previous && items.isNotEmpty()) {
            val baselineGroup = prefs.getString("baseline_group", "").orEmpty()
            require(baselineGroup.isBlank() || group == baselineGroup) { "catalog_scope" }
        }
        if (oldPending?.optString("scope") == scope && items.isNotEmpty()) {
            val pendingGroup = oldPending.optString("group")
            require(pendingGroup.isBlank() || group == pendingGroup) { "catalog_scope" }
        }
        val pending =
            JSONObject()
                .put("scope", scope)
                .put("group", group)
                .put("fingerprint", fingerprint(items))
                .put("hashes", JSONArray(items.map { it.contentHash }.distinct().sorted()))
                .put("retired", JSONArray(retiredHashes.sorted()))
                .put("inherited", JSONArray(inherited.sorted()))
        check(prefs.edit().putString("pending", pending.toString()).commit()) { "storage_full" }
        status.value = "syncing"
        return scope
    }

    fun verifiedHashes(key: String, sourceId: String): Set<String> =
        if (prefs.getString("baseline_scope", "") == digest(key + "|" + sourceId))
            JSONArray(prefs.getString("baseline_hashes", "[]")).strings()
        else emptySet()

    @Synchronized
    fun pendingHashes(): Set<String> =
        prefs.getString("pending", null)?.let {
            val pending = JSONObject(it)
            pending.getJSONArray("hashes").strings() +
                (pending.optJSONArray("inherited") ?: JSONArray()).strings()
        } ?: emptySet()

    @Synchronized
    fun finishIfReady() {
        val raw = prefs.getString("pending", null) ?: return
        try {
            val pending = JSONObject(raw)
            val scope = pending.getString("scope")
            val hashes = pending.getJSONArray("hashes").strings()
            val offline = File(app.filesDir, "offline")
            if (
                hashes.any { hash ->
                    app.library.byHash(hash)?.let { app.library.hasUsableOffline(it, offline) } !=
                        true
                }
            ) {
                status.value =
                    if (
                        app.library.entries().any {
                            it.downloadState in listOf("queued", "running")
                        }
                    )
                        "syncing"
                    else "retry"
                return
            }
            val current = app.mac.verifiedCatalog()
            check(
                current.sourceId.isNotBlank() &&
                    scope == digest(current.key + "|" + current.sourceId)
            ) {
                "catalog_scope"
            }
            check(fingerprint(current.entries) == pending.getString("fingerprint")) {
                "catalog_changed"
            }
            val retired = (pending.optJSONArray("retired") ?: JSONArray()).strings()
            check(current.retiredHashes == retired) { "catalog_changed" }
            app.mac.commit(current)
            val old =
                if (prefs.getString("baseline_scope", "") == scope)
                    JSONArray(prefs.getString("baseline_hashes", "[]")).strings()
                else emptySet()
            val stale = (old + app.library.mirrorHashes(scope) + retired) - hashes
            val group = pending.getString("group")
            val deferred = mutableSetOf<String>()
            val cleanup = JSONArray(prefs.getString("cleanup", "[]")).strings().toMutableSet()
            // Record candidates first: if interrupted, cleanup remains retriable.
            cleanup.addAll(stale)
            check(
                prefs.edit().putString("cleanup", JSONArray(cleanup.toList()).toString()).commit()
            )
            for (hash in stale) {
                val legacy = app.library.byHash(hash)
                var verifiedUnlocated = false
                if (
                    hash in retired &&
                        legacy != null &&
                        legacy.locations.isEmpty() &&
                        app.library.hasOfflineCopy(legacy.id)
                ) {
                    val file = ownedOfflineFile(offline, hash, "media")
                    check(
                        file.isFile &&
                            file.inputStream().use { Fingerprint.read(it) } ==
                                (hash to legacy.bytes)
                    ) {
                        "integrity"
                    }
                    verifiedUnlocated = true
                }
                val uri =
                    runBlocking(Dispatchers.Main.immediate) {
                        app.library.retireMirrored(
                            hash,
                            group,
                            PlaybackService.activeMediaId.orEmpty(),
                            File(app.filesDir, "offline"),
                            verifiedUnlocated,
                            PlaybackService::prepareRetirement,
                        )
                    }
                if (uri == null) {
                    deferred.add(hash)
                    continue
                }
                if (uri.isEmpty()) {
                    if (app.library.byHash(hash) != null) {
                        cleanup.remove(hash)
                        app.library.forgetMirrorHash(scope, hash)
                    }
                    // With no database row, the recovery pass must still remove an orphaned copy.
                    continue
                }
                if (uri.isNotEmpty()) {
                    val parsed = Uri.parse(uri)
                    val expected = ownedOfflineFile(offline, hash, "media")
                    if (
                        parsed.scheme == "file" &&
                            parsed.path != null &&
                            File(parsed.path!!).canonicalFile == expected
                    ) {
                        if (expected.exists() && !expected.delete()) continue
                    }
                }
                val part = ownedOfflineFile(offline, hash, "part")
                if (!part.exists() || part.delete()) {
                    cleanup.remove(hash)
                    app.library.forgetMirrorHash(scope, hash)
                }
            }
            // A crash after the database update may leave a harmless unreferenced file.
            for (hash in cleanup.toList()) {
                if (hash in hashes) {
                    cleanup.remove(hash)
                    continue
                }
                if (hash in deferred || app.library.byHash(hash) != null) continue
                val file = ownedOfflineFile(offline, hash, "media")
                val part = ownedOfflineFile(offline, hash, "part")
                if ((!file.exists() || file.delete()) && (!part.exists() || part.delete())) {
                    cleanup.remove(hash)
                    app.library.forgetMirrorHash(scope, hash)
                }
            }
            val edit =
                prefs
                    .edit()
                    .putString("baseline_scope", scope)
                    .putString("baseline_group", group)
                    .putString(
                        "baseline_hashes",
                        JSONArray((hashes + deferred).sorted()).toString(),
                    )
                    .putString("cleanup", JSONArray(cleanup.toList()).toString())
            if (deferred.isEmpty() && cleanup.isEmpty()) edit.remove("pending")
            check(edit.commit())
            status.value =
                if (deferred.isNotEmpty()) "waiting"
                else if (cleanup.isEmpty()) "complete" else "retry"
        } catch (_: Exception) {
            status.value = "retry"
        }
    }

    private fun JSONArray.strings(): Set<String> = (0 until length()).map { getString(it) }.toSet()
}

/** Return an app-owned path without following a file-level symlink. */
internal fun ownedOfflineFile(directory: File, hash: String, extension: String): File {
    require(hash.matches(Regex("[a-f0-9]{64}")) && extension in listOf("media", "part"))
    val path = File(directory.canonicalFile, "$hash.$extension").absoluteFile
    check(path.canonicalFile == path) { "integrity" }
    return path
}
