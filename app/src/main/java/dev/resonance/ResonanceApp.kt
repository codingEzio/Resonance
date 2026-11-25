package dev.resonance

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ResonanceApp : Application() {
    lateinit var library: LibraryDb
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val importError = MutableStateFlow("")
    val downloadStopReason = MutableStateFlow("")
    private val downloadSettings by lazy { getSharedPreferences("download_recovery", 0) }
    private var worker: Job? = null
    private val inspector by lazy { ContentMediaInspector(this) }
    val mac by lazy { MacLibrary(this) }
    internal val mirror by lazy { MirrorCoordinator(this) }
    private val syncMutex = Mutex()
    internal val wishlist by lazy { WishlistStore(getSharedPreferences(AppSettings.STORE, 0)) }

    fun download(id: Long) {
        scope.launch {
            try {
                if (library.requestDownload(id)) {
                    clearDownloadStop()
                    androidx.core.content.ContextCompat.startForegroundService(
                        this@ResonanceApp,
                        Intent(this@ResonanceApp, DownloadService::class.java),
                    )
                }
            } catch (error: Exception) {
                pauseDownloadRecovery("download_start_failed")
                importError.value = error.message ?: "download_failed"
            }
        }
    }

    /**
     * Called only by an explicit bulk action. Queue commit precedes the receipt and one FGS start.
     */
    suspend fun downloadAll(): DownloadBatchReceipt =
        withContext(Dispatchers.IO) {
            val before = library.downloadSummary()
            check(
                StatFs(filesDir.path).availableBytes >
                    before.totalBytes - before.transferredBytes +
                        DownloadPolicy.STORAGE_RESERVE_BYTES
            ) {
                "storage_full"
            }
            val receipt = library.requestAllDownloads()
            if (library.nextDownload() != null) {
                clearDownloadStop()
                try {
                    androidx.core.content.ContextCompat.startForegroundService(
                        this@ResonanceApp,
                        Intent(this@ResonanceApp, DownloadService::class.java),
                    )
                } catch (error: Exception) {
                    pauseDownloadRecovery("download_start_failed")
                    throw error
                }
            }
            receipt
        }

    /** A complete Mac snapshot is staged before the device library is changed. */
    suspend fun syncFrom(address: String? = null, key: String = mac.key): Int =
        withContext(Dispatchers.IO) {
            check(syncMutex.tryLock()) { "sync_busy" }
            try {
                check(!DownloadService.workerRunning) { "sync_busy" }
                val pending = mirror.pendingHashes()
                val waiting =
                    library.entries().filter { it.downloadState in listOf("queued", "running") }
                check(waiting.all { it.contentHash in pending }) {
                    "sync_busy"
                }
                pauseDownloadRecovery("sync_preparing")
                check(!DownloadService.workerRunning) { "sync_busy" }
                mirror.status.value = "syncing"
                val snapshot = mac.prepare(address ?: mac.preferredBase(), key, fresh = true)
                require(snapshot.sourceId.isNotBlank()) { "catalog_scope" }
                val mirrorScope =
                    mirror.stage(
                        snapshot.entries,
                        snapshot.key,
                        snapshot.sourceId,
                        snapshot.retiredHashes,
                    )
                val offline = File(filesDir, "offline")
                val verified = mirror.verifiedHashes(snapshot.key, snapshot.sourceId)
                val missing =
                    snapshot.entries.filter { item ->
                        library.byHash(item.contentHash)?.let {
                            library.hasUsableOffline(it, offline, item.contentHash !in verified)
                        } != true
                    }
                check(DownloadPolicy.accepts(0, missing.size)) { "capacity" }
                val remaining = missing.sumOf { item ->
                    val part = File(offline, "${item.contentHash}.part")
                    item.bytes - part.length().coerceIn(0, item.bytes)
                }
                check(
                    StatFs(filesDir.path).availableBytes >
                        remaining + DownloadPolicy.STORAGE_RESERVE_BYTES
                ) {
                    "storage_full"
                }
                val currentHashes = snapshot.entries.map { it.contentHash }.toSet()
                for (item in waiting) if (item.contentHash !in currentHashes)
                    library.cancelDownload(item.id)
                library.acceptRemote(snapshot.entries, mirrorScope)
                mac.commit(snapshot)
                var accepted = 0
                try {
                    for (item in missing) {
                        val current = library.byHash(item.contentHash) ?: error("catalog_changed")
                        if (library.requestDownload(current.id, requireOffline = true)) accepted++
                    }
                } finally {
                    if (accepted > 0) {
                        clearDownloadStop()
                        androidx.core.content.ContextCompat.startForegroundService(
                            this@ResonanceApp,
                            Intent(this@ResonanceApp, DownloadService::class.java),
                        )
                    }
                }
                if (missing.isEmpty()) {
                    clearDownloadStop()
                    mirror.finishIfReady()
                }
                accepted
            } catch (error: Exception) {
                mirror.status.value = "retry"
                throw error
            } finally {
                syncMutex.unlock()
            }
        }

    fun pauseDownloadRecovery(reason: String) {
        // Persist the stop before Activity foreground effects can attempt recovery.
        downloadSettings.edit().putString("stop_reason", reason).commit()
        downloadStopReason.value = reason
        mirror.status.value = "retry"
    }

    private fun clearDownloadStop() {
        downloadSettings.edit().remove("stop_reason").commit()
        downloadStopReason.value = ""
    }

    fun resumeDownloads() {
        if (downloadStopReason.value.isEmpty() && library.nextDownload() != null)
            androidx.core.content.ContextCompat.startForegroundService(
                this,
                Intent(this, DownloadService::class.java),
            )
    }

    override fun onCreate() {
        super.onCreate()
        appPreferences().applyLegacyDefaults()
        LibraryPresentation.install(loadPresentationAssets(assets))
        mac.restoreNames()
        library = LibraryDb(this)
        if (BuildConfig.PRESENTATION_PROFILE == "bundled") {
            val preferences = getSharedPreferences("retired_shelf_groups", 0)
            val names =
                assets.open("private-fonts/retired-shelf-groups.txt").bufferedReader().use {
                    it.readLines().map(String::trim).filter(String::isNotEmpty).distinct()
                }
            for (name in names) {
                if (!preferences.getBoolean(name, false)) {
                    library.forgetRemoteGroup(name)
                    check(preferences.edit().putBoolean(name, true).commit())
                }
            }
        }
        downloadStopReason.value = downloadSettings.getString("stop_reason", "").orEmpty()
        library.recoverDownloads()
        scope.launch {
            library.recoverInterrupted()
            mirror.finishIfReady()
            startImport()
        }
    }

    fun accept(uris: List<Uri>, flags: Int) {
        scope.launch {
            try {
                require(uris.size <= MediaLimits.IMPORT_ITEMS) { "capacity" }
                val denied = mutableSetOf<String>()
                val items =
                    uris.distinct().map { uri ->
                        require(uri.scheme == "content") { "local_only" }
                        try {
                            contentResolver.takePersistableUriPermission(
                                uri,
                                flags and Intent.FLAG_GRANT_READ_URI_PERMISSION,
                            )
                        } catch (_: SecurityException) {
                            denied.add(uri.toString())
                        }
                        val name =
                            contentResolver
                                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                                ?.use { if (it.moveToFirst()) it.getString(0) else null }
                                ?: uri.lastPathSegment
                                ?: "Media"
                        uri.toString() to name
                    }
                library.enqueue(items)
                library
                    .entries()
                    .filter { it.uri in denied && it.state != "ready" }
                    .forEach { library.fail(it.id, "permission") }
                importError.value = ""
                startImport()
            } catch (e: Exception) {
                importError.value = e.message ?: "import_failed"
            }
        }
    }

    @Synchronized
    fun startImport() {
        if (worker?.isActive == true) return
        worker = scope.launch {
            try {
                while (isActive) {
                    val entry = library.nextQueued() ?: break
                    if (!library.markRunning(entry.id)) continue
                    try {
                        library.complete(
                            inspector.inspect(entry) { library.get(entry.id)?.state != "cancelled" }
                        )
                    } catch (e: Exception) {
                        if (library.get(entry.id)?.state != "cancelled")
                            library.fail(
                                entry.id,
                                if (e is SecurityException) "permission" else "unreadable",
                            )
                    }
                }
            } finally {
                synchronized(this@ResonanceApp) { worker = null }
                if (library.nextQueued() != null) startImport()
            }
        }
    }
}
